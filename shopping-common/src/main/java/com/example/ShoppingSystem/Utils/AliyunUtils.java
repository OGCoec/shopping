package com.example.ShoppingSystem.Utils;

import com.aliyun.auth.credentials.provider.EnvironmentVariableCredentialProvider;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.google.gson.Gson;
import darabonba.core.client.ClientOverrideConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 阿里云工具类
 * <p>
 * 功能：
 * <ul>
 *   <li>短信验证码发送（基于 dypnsapi SDK）</li>
 *   <li>对象存储文件上传（基于 alibabacloud-oss-v2 SDK）</li>
 * </ul>
 *
 * <p>所需环境变量（AK/SK 凭证）：
 * <ul>
 *   <li>{@code ALIBABA_CLOUD_ACCESS_KEY_ID}    - 阿里云访问密钥 ID</li>
 *   <li>{@code ALIBABA_CLOUD_ACCESS_KEY_SECRET} - 阿里云访问密钥 Secret</li>
 * </ul>
 *
 * <p>OSS 固定配置：
 * <ul>
 *   <li>Bucket：{@value #OSS_BUCKET}</li>
 *   <li>Region：{@value #OSS_REGION}</li>
 *   <li>Endpoint：{@value #OSS_ENDPOINT}</li>
 * </ul>
 */
@Component
public class AliyunUtils {

    private static final Logger log = LoggerFactory.getLogger(AliyunUtils.class);

    // ─── OSS 配置常量 ────────────────────────────────────────────────────────
    /** OSS Bucket 名称 */
    private static final String OSS_BUCKET   = "damnit";
    /** OSS 所在地域 */
    private static final String OSS_REGION   = "cn-shenzhen";
    /** OSS 服务 Endpoint */
    private static final String OSS_ENDPOINT = "https://oss-cn-shenzhen.aliyuncs.com";

    // ─── 短信发送 ────────────────────────────────────────────────────────────

    /**
     * 发送短信验证码
     *
     * @param telephoneNumber 目标手机号
     * @param templateCode    短信模板 Code
     * @param code            验证码内容
     * @param time            有效时间（分钟）
     * @throws Exception 发送失败时抛出
     */
    public void sendSmsVerifyCode(String telephoneNumber, String templateCode,
                                  String code, String time) throws Exception {
        // 使用环境变量提供 AK 信息：ALIBABA_CLOUD_ACCESS_KEY_ID / ALIBABA_CLOUD_ACCESS_KEY_SECRET
        EnvironmentVariableCredentialProvider provider = new EnvironmentVariableCredentialProvider();

        try (AsyncClient client = AsyncClient.builder()
                .region("cn-hangzhou")
                .credentialsProvider(provider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride("dypnsapi.aliyuncs.com")
                )
                .build()) {

            String templateParamJson = String.format("{\"code\":\"%s\",\"min\":\"%s\"}", code, time);

            SendSmsVerifyCodeRequest sendSmsVerifyCodeRequest = SendSmsVerifyCodeRequest.builder()
                    .phoneNumber(telephoneNumber)
                    .signName(resolveSmsSignName())
                    .templateCode(templateCode)
                    .templateParam(templateParamJson)
                    .build();

            CompletableFuture<SendSmsVerifyCodeResponse> responseFuture =
                    client.sendSmsVerifyCode(sendSmsVerifyCodeRequest);

            SendSmsVerifyCodeResponse resp = responseFuture.get();
            log.info("阿里云短信发送结果: {}", new Gson().toJson(resp));
        }
    }

    private String resolveSmsSignName() {
        String signName = System.getenv("ALIYUN_SMS_SIGN_NAME");
        if (signName == null || signName.isBlank()) {
            return "速通互联验证平台";
        }
        return signName.trim();
    }

    // ─── OSS 文件上传 ────────────────────────────────────────────────────────
    /**
     * @param objectKey  OSS 文件路径（即对象 Key），例如 {@code "images/avatar/user_1.jpg"}
     * @param fileBytes  待上传的文件字节数组
     * @return 包含文件公共访问 URL 的 CompletableFuture
     */
    public CompletableFuture<String> uploadFile(String objectKey, byte[] fileBytes) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider);

        return client.putObjectAsync(
                PutObjectRequest.newBuilder()
                        .bucket(OSS_BUCKET)
                        .key(objectKey)
                        .body(BinaryData.fromBytes(fileBytes))
                        .build()
        ).thenApply(result -> {
            log.info("OSS 上传成功 | key={} | statusCode={} | requestId={} | eTag={}",
                    objectKey, result.statusCode(), result.requestId(), result.eTag());
            return buildFileUrl(objectKey);
        }).whenComplete((url, ex) -> {
            if (ex != null) {
                log.error("OSS 上传失败 | key={} | error={}", objectKey, ex.getMessage(), ex);
            }
            try {
                client.close();
            } catch (Exception e) {
                log.error("OSS 客户端关闭失败", e);
            }
        });
    }

    /**
     * 上传字符串内容到 OSS（适合存储文本型数据，例如 JSON 配置文件）
     *
     * @param objectKey OSS 文件路径（即对象 Key），例如 {@code "configs/app-config.json"}
     * @param content   待上传的字符串内容
     * @return 包含文件公共访问 URL 的 CompletableFuture
     */
    public CompletableFuture<String> uploadContent(String objectKey, String content) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider);

        return client.putObjectAsync(
                PutObjectRequest.newBuilder()
                        .bucket(OSS_BUCKET)
                        .key(objectKey)
                        .body(BinaryData.fromString(content))
                        .build()
        ).thenApply(result -> {
            log.info("OSS 内容上传成功 | key={} | statusCode={} | requestId={} | eTag={}",
                    objectKey, result.statusCode(), result.requestId(), result.eTag());
            return buildFileUrl(objectKey);
        }).whenComplete((url, ex) -> {
            if (ex != null) {
                log.error("OSS 内容上传失败 | key={} | error={}", objectKey, ex.getMessage(), ex);
            }
            try {
                client.close();
            } catch (Exception e) {
                log.error("OSS 客户端关闭失败", e);
            }
        });
    }

    /**
     * 从 OSS 中删除文件
     *
     * @param objectKey OSS 文件路径
     * @return 表示操作完成的 CompletableFuture
     */
    public CompletableFuture<Void> deleteFile(String objectKey) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider);

        return client.deleteObjectAsync(
                com.aliyun.sdk.service.oss2.models.DeleteObjectRequest.newBuilder()
                        .bucket(OSS_BUCKET)
                        .key(objectKey)
                        .build()
        ).thenAccept(result -> {
            log.info("OSS 文件删除成功 | key={} | statusCode={} | requestId={}",
                    objectKey, result.statusCode(), result.requestId());
        }).whenComplete((res, ex) -> {
            if (ex != null) {
                log.error("OSS 文件删除失败 | key={} | error={}", objectKey, ex.getMessage(), ex);
            }
            try {
                client.close();
            } catch (Exception e) {
                log.error("OSS 客户端关闭失败", e);
            }
        });
    }

    /**
     * OSS 服务端内部拷贝文件（不消耗客户端带宽）
     *
     * @param srcKey  源 OSS 对象 Key，例如 {@code "dish/temp/abc.jpg"}
     * @param destKey 目标 OSS 对象 Key，例如 {@code "dish/ABEiM0RV.../abc.jpg"}
     * @return 包含目标文件公共访问 URL 的 CompletableFuture
     */
    public CompletableFuture<String> copyFile(String srcKey, String destKey) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider);

        return client.copyObjectAsync(
                com.aliyun.sdk.service.oss2.models.CopyObjectRequest.newBuilder()
                        .bucket(OSS_BUCKET)
                        .key(destKey)
                        .sourceBucket(OSS_BUCKET)
                        .sourceKey(srcKey)
                        .build()
        ).thenApply(result -> {
            log.info("OSS 服务端拷贝成功 | src={} | dest={} | statusCode={}",
                    srcKey, destKey, result.statusCode());
            return buildFileUrl(destKey);
        }).whenComplete((url, ex) -> {
            if (ex != null) {
                log.error("OSS 服务端拷贝失败 | src={} | dest={} | error={}", srcKey, destKey, ex.getMessage(), ex);
            }
            try {
                client.close();
            } catch (Exception e) {
                log.error("OSS 客户端关闭失败", e);
            }
        });
    }



    /**
     * 构建 OSSAsyncClient 实例
     *
     * @param provider 凭证提供者
     * @return 已配置好 Region 与 Endpoint 的异步客户端
     */
    private OSSAsyncClient buildOssClient(CredentialsProvider provider) {
        return OSSAsyncClient.newBuilder()
                .region(OSS_REGION)
                .endpoint(OSS_ENDPOINT)
                .credentialsProvider(provider)
                .build();
    }

    /**
     * 根据 objectKey 拼接文件的公共访问 URL
     *
     * <p>URL 格式：{@code https://<bucket>.<host>/<objectKey>}
     * <br>例如：{@code https://damnit.oss-cn-shenzhen.aliyuncs.com/images/avatar/user_1.jpg}
     *
     * @param objectKey OSS 对象 Key
     * @return 完整的公共访问 URL 字符串
     */
    private String buildFileUrl(String objectKey) {
        // 从 Endpoint 中提取主机名（去掉协议前缀 https://）
        String host = OSS_ENDPOINT.replace("https://", "");
        return String.format("https://%s.%s/%s", OSS_BUCKET, host, objectKey);
    }
}
