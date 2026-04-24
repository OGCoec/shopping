package com.example.ShoppingSystem.Utils.redis;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RedisData<T> {
    // 逻辑上的过期时间（并非 Redis 物理过期）
    private LocalDateTime expireTime;
    // 真正的业务数据（存入 Object，依靠外部泛型反序列化）
    private T data;
}
