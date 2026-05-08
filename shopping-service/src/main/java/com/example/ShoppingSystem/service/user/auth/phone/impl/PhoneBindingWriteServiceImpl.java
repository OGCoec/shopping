package com.example.ShoppingSystem.service.user.auth.phone.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.example.ShoppingSystem.entity.entity.UserLoginIdentity;
import com.example.ShoppingSystem.mapper.UserLoginIdentityMapper;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBindingWriteService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneBoundCountingBloomService;
import com.example.ShoppingSystem.service.user.auth.phone.PhoneVerifiedUserLookupService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class PhoneBindingWriteServiceImpl implements PhoneBindingWriteService {

    private static final String LOCK_KEY_PREFIX = "lock:phone:bind:";
    private static final long LOCK_WAIT_SECONDS = 2L;
    private static final long LOCK_LEASE_SECONDS = 30L;

    private final RedissonClient redissonClient;
    private final UserLoginIdentityMapper userLoginIdentityMapper;
    private final PhoneBoundCountingBloomService phoneBoundCountingBloomService;
    private final PhoneVerifiedUserLookupService phoneVerifiedUserLookupService;

    public PhoneBindingWriteServiceImpl(RedissonClient redissonClient,
                                        UserLoginIdentityMapper userLoginIdentityMapper,
                                        PhoneBoundCountingBloomService phoneBoundCountingBloomService,
                                        PhoneVerifiedUserLookupService phoneVerifiedUserLookupService) {
        this.redissonClient = redissonClient;
        this.userLoginIdentityMapper = userLoginIdentityMapper;
        this.phoneBoundCountingBloomService = phoneBoundCountingBloomService;
        this.phoneVerifiedUserLookupService = phoneVerifiedUserLookupService;
    }

    @Override
    public PhoneBindingResult bindVerifiedPhone(Long userId, String normalizedE164) {
        if (userId == null || userId <= 0 || StrUtil.isBlank(normalizedE164)) {
            return failed(ERROR_PHONE_BIND_IDENTITY_MISSING, "Failed to bind phone number.", normalizedE164);
        }
        String phone = normalizedE164.trim();
        RLock lock = redissonClient.getLock(lockKey(phone));
        boolean locked = false;
        boolean unlockDeferred = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                return failed(ERROR_PHONE_BIND_BUSY, "Phone binding is busy, please try again later.", phone);
            }
            unlockDeferred = deferUnlockUntilTransactionComplete(lock);
            return bindInsideLock(userId, phone);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(ERROR_PHONE_BIND_BUSY, "Phone binding is busy, please try again later.", phone);
        } finally {
            if (locked && !unlockDeferred) {
                unlockIfHeld(lock);
            }
        }
    }

    private PhoneBindingResult bindInsideLock(Long userId, String phone) {
        UserLoginIdentity existingByPhone = userLoginIdentityMapper.findByPhone(phone);
        if (existingByPhone != null) {
            if (Objects.equals(existingByPhone.getUserId(), userId)
                    && Boolean.TRUE.equals(existingByPhone.getPhoneVerified())) {
                markUserPhoneVerifiedAfterCommit(userId);
                return new PhoneBindingResult(true, true, null, null, "Phone number is already verified.", phone);
            }
            return failed(ERROR_PHONE_ALREADY_BOUND, "This phone number is already in use.", phone);
        }

        UserLoginIdentity current = userLoginIdentityMapper.findByUserId(userId);
        if (current == null) {
            return failed(ERROR_PHONE_BIND_IDENTITY_MISSING, "Registered account identity was not found.", phone);
        }
        if (Boolean.TRUE.equals(current.getPhoneVerified()) && StrUtil.isNotBlank(current.getPhone())) {
            if (phone.equals(current.getPhone().trim())) {
                markUserPhoneVerifiedAfterCommit(userId);
                return new PhoneBindingResult(true, true, null, null, "Phone number is already verified.", phone);
            }
            return failed(ERROR_PHONE_USER_ALREADY_VERIFIED, "This account already has a verified phone number.", phone);
        }

        try {
            int updatedRows = userLoginIdentityMapper.bindVerifiedPhoneByUserId(userId, phone);
            if (updatedRows <= 0) {
                return failed(ERROR_PHONE_BIND_FAILED, "Failed to bind phone number.", phone);
            }
        } catch (DataIntegrityViolationException e) {
            return failed(ERROR_PHONE_ALREADY_BOUND, "This phone number is already in use.", phone);
        }
        markPhoneBoundAfterCommit(userId, phone);
        return new PhoneBindingResult(true, false, null, null, "Phone verification completed.", phone);
    }

    private void markPhoneBoundAfterCommit(Long userId, String phone) {
        Runnable marker = () -> {
            phoneBoundCountingBloomService.addVerifiedPhoneAsync(phone);
            phoneVerifiedUserLookupService.markPhoneVerified(userId);
        };
        runAfterCommit(marker);
    }

    private void markUserPhoneVerifiedAfterCommit(Long userId) {
        runAfterCommit(() -> phoneVerifiedUserLookupService.markPhoneVerified(userId));
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private boolean deferUnlockUntilTransactionComplete(RLock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                unlockIfHeld(lock);
            }
        });
        return true;
    }

    private void unlockIfHeld(RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private PhoneBindingResult failed(String errorCode, String message, String phone) {
        return new PhoneBindingResult(false, false, errorCode, errorCode, message, phone);
    }

    private String lockKey(String phone) {
        return LOCK_KEY_PREFIX + DigestUtil.sha256Hex(phone);
    }
}
