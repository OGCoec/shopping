package com.example.ShoppingSystem.service.user.auth.login.impl;

import com.example.ShoppingSystem.entity.entity.UserProfile;
import com.example.ShoppingSystem.mapper.UserProfileMapper;
import com.example.ShoppingSystem.service.user.auth.login.UserProfileService;
import com.example.ShoppingSystem.service.user.auth.login.model.UserProfileDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户资料服务实现。
 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileMapper userProfileMapper;

    public UserProfileServiceImpl(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    @Transactional
    public void initIfAbsent(Long userId, UserProfileDraft draft) {
        if (userId == null || userProfileMapper.existsById(userId)) {
            return;
        }

        userProfileMapper.insertUserProfile(buildUserProfile(userId, draft));
    }

    private UserProfile buildUserProfile(Long userId, UserProfileDraft draft) {
        UserProfile.UserProfileBuilder builder = UserProfile.builder().id(userId);
        if (draft == null) {
            return builder.build();
        }

        return builder
                .firstName(draft.getFirstName())
                .lastName(draft.getLastName())
                .gender(draft.getGender())
                .bio(draft.getBio())
                .birthday(draft.getBirthday())
                .country(draft.getCountry())
                .language(draft.getLanguage())
                .timezone(draft.getTimezone())
                .build();
    }
}
