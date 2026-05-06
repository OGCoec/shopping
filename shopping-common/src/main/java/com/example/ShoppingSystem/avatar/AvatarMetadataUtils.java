package com.example.ShoppingSystem.avatar;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class AvatarMetadataUtils {

    private AvatarMetadataUtils() {
    }

    public static AvatarMetadata parse(String rawJson, ObjectMapper objectMapper) {
        if (objectMapper == null || StrUtil.isBlank(rawJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return fromUrl(node.asText());
            }
            if (node.isObject()) {
                AvatarMetadata metadata = objectMapper.treeToValue(node, AvatarMetadata.class);
                return enrichFromUrl(metadata);
            }
            return null;
        } catch (Exception ignored) {
        }
        return fromUrl(rawJson);
    }

    public static String toJson(AvatarMetadata metadata, ObjectMapper objectMapper) {
        if (metadata == null || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize avatar metadata.", e);
        }
    }

    public static String extractUrl(String rawJson, ObjectMapper objectMapper) {
        AvatarMetadata metadata = parse(rawJson, objectMapper);
        if (metadata == null) {
            return "";
        }
        return StrUtil.blankToDefault(metadata.getUrl(), "");
    }

    public static AvatarMetadata fromUrl(String url) {
        String normalizedUrl = StrUtil.blankToDefault(url, "").trim();
        if (!isHttpUrl(normalizedUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(normalizedUrl);
            String host = StrUtil.blankToDefault(uri.getHost(), "");
            if (StrUtil.isBlank(host)) {
                return null;
            }
            String bucket = "";
            int bucketEndIndex = host.indexOf('.');
            if (bucketEndIndex > 0) {
                bucket = host.substring(0, bucketEndIndex);
            }
            String rawPath = StrUtil.blankToDefault(uri.getRawPath(), "");
            String objectKey = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            objectKey = URLDecoder.decode(objectKey, StandardCharsets.UTF_8);
            return AvatarMetadata.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .url(normalizedUrl)
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static AvatarMetadata enrichFromUrl(AvatarMetadata metadata) {
        if (metadata == null || StrUtil.isBlank(metadata.getUrl())) {
            return metadata;
        }
        AvatarMetadata fromUrl = fromUrl(metadata.getUrl());
        if (fromUrl == null) {
            return metadata;
        }
        if (StrUtil.isBlank(metadata.getBucket())) {
            metadata.setBucket(fromUrl.getBucket());
        }
        if (StrUtil.isBlank(metadata.getObjectKey())) {
            metadata.setObjectKey(fromUrl.getObjectKey());
        }
        return metadata;
    }

    private static boolean isHttpUrl(String value) {
        return StrUtil.startWithIgnoreCase(value, "http://")
                || StrUtil.startWithIgnoreCase(value, "https://");
    }
}
