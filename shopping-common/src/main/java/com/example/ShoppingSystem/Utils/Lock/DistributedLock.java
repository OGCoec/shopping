package com.example.ShoppingSystem.Utils.Lock;

/**
 * 分布式锁接口
 */
public interface DistributedLock {

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的超时时间（秒）
     * @return 是否成功获取锁
     */
    boolean tryLock(Long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
