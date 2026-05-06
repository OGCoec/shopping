package com.example.ShoppingSystem.avatar;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarMetadata {

    private String bucket;
    private String objectKey;
    private String url;
    private Long uploadedAtEpochMilli;

    public boolean hasObjectLocation() {
        return StrUtil.isNotBlank(bucket) && StrUtil.isNotBlank(objectKey);
    }
}
