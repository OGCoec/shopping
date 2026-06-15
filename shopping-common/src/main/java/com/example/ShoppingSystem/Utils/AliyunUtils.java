package com.example.ShoppingSystem.Utils;

import cn.hutool.core.util.StrUtil;
import com.aliyun.auth.credentials.Credential;
import com.aliyun.auth.credentials.provider.EnvironmentVariableCredentialProvider;
import com.aliyun.auth.credentials.provider.ICredentialProvider;
import com.aliyun.auth.credentials.provider.StaticCredentialProvider;
import com.aliyun.core.http.HttpClient;
import com.aliyun.core.http.ProxyOptions;
import com.aliyun.httpcomponent.httpclient.ApacheAsyncHttpClientBuilder;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.sdk.service.oss2.OSSAsyncClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.CopyObjectRequest;
import com.aliyun.sdk.service.oss2.models.DeleteObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.example.ShoppingSystem.common.proxy.OutboundRouteResolver;
import com.google.gson.Gson;
import darabonba.core.client.ClientOverrideConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Component
public class AliyunUtils {

    private static final Logger log = LoggerFactory.getLogger(AliyunUtils.class);

    private static final String DEFAULT_OSS_BUCKET = "shopping6655";
    private static final String OSS_REGION = "cn-hongkong";
    private static final String OSS_ENDPOINT = "https://oss-cn-hongkong.aliyuncs.com";
    public static final String HONG_KONG_OSS_REGION = "cn-hongkong";
    public static final String HONG_KONG_OSS_ENDPOINT = "https://oss-cn-hongkong.aliyuncs.com";

    private static final String SMS_ENDPOINT_HOST = "dypnsapi.aliyuncs.com";
    private static final int SMS_ENDPOINT_PORT = 443;

    private final OutboundRouteResolver outboundRouteResolver;
    private final boolean smsProxyEnabled;
    private final String smsProxyHost;
    private final int smsProxyPort;
    private final String smsProxyRouteMode;
    private final int smsProxyRouteProbeTimeoutMs;
    private final String smsAccessKeyId;
    private final String smsAccessKeySecret;

    public AliyunUtils(OutboundRouteResolver outboundRouteResolver,
                       @Value("${aliyun.sms.proxy.enabled:true}") boolean smsProxyEnabled,
                       @Value("${aliyun.sms.proxy.host:127.0.0.1}") String smsProxyHost,
                       @Value("${aliyun.sms.proxy.port:7892}") int smsProxyPort,
                       @Value("${aliyun.sms.proxy.route-mode:auto}") String smsProxyRouteMode,
                       @Value("${aliyun.sms.proxy.route-probe-timeout-ms:1500}") int smsProxyRouteProbeTimeoutMs,
                       @Value("${aliyun.sms.access-key-id:}") String smsAccessKeyId,
                       @Value("${aliyun.sms.access-key-secret:}") String smsAccessKeySecret) {
        this.outboundRouteResolver = outboundRouteResolver;
        this.smsProxyEnabled = smsProxyEnabled;
        this.smsProxyHost = smsProxyHost;
        this.smsProxyPort = smsProxyPort;
        this.smsProxyRouteMode = smsProxyRouteMode;
        this.smsProxyRouteProbeTimeoutMs = smsProxyRouteProbeTimeoutMs;
        this.smsAccessKeyId = smsAccessKeyId;
        this.smsAccessKeySecret = smsAccessKeySecret;
    }

    public void sendSmsVerifyCode(String telephoneNumber,
                                  String templateCode,
                                  String code,
                                  String time) throws Exception {
        ICredentialProvider provider = resolveSmsCredentialProvider();

        var clientBuilder = AsyncClient.builder()
                .region("cn-hangzhou")
                .credentialsProvider(provider)
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                .setEndpointOverride(SMS_ENDPOINT_HOST)
                );
        HttpClient smsHttpClient = buildSmsHttpClient();
        if (smsHttpClient != null) {
            clientBuilder.httpClient(smsHttpClient);
        }

        try (AsyncClient client = clientBuilder.build()) {

            String templateParamJson = String.format("{\"code\":\"%s\",\"min\":\"%s\"}", code, time);

            SendSmsVerifyCodeRequest request = SendSmsVerifyCodeRequest.builder()
                    .phoneNumber(telephoneNumber)
                    .signName(resolveSmsSignName())
                    .templateCode(templateCode)
                    .templateParam(templateParamJson)
                    .build();

            CompletableFuture<SendSmsVerifyCodeResponse> responseFuture = client.sendSmsVerifyCode(request);
            SendSmsVerifyCodeResponse response = responseFuture.get();
            log.info("Aliyun SMS send result: {}", new Gson().toJson(response));
        }
    }

    private HttpClient buildSmsHttpClient() {
        if (!smsProxyEnabled) {
            return null;
        }
        OutboundRouteResolver.RouteSelection routeSelection = outboundRouteResolver.selectRoute(
                "Aliyun SMS HTTP",
                SMS_ENDPOINT_HOST,
                SMS_ENDPOINT_PORT,
                smsProxyHost,
                smsProxyPort,
                smsProxyRouteMode,
                smsProxyRouteProbeTimeoutMs,
                OutboundRouteResolver.ProxyProtocol.HTTP_CONNECT
        );
        if (routeSelection.direct()) {
            log.info("Aliyun SMS HTTP DIRECT route selected, target={}:{}, reachable={}, reason={}",
                    SMS_ENDPOINT_HOST,
                    SMS_ENDPOINT_PORT,
                    routeSelection.reachable(),
                    routeSelection.reason());
            return null;
        }
        InetSocketAddress proxyAddress = routeSelection.address();
        if (proxyAddress == null) {
            return null;
        }
        log.info("Aliyun SMS HTTP proxy selected: host={}, port={}, target={}:{}, reachable={}, reason={}",
                proxyAddress.getHostString(),
                proxyAddress.getPort(),
                SMS_ENDPOINT_HOST,
                SMS_ENDPOINT_PORT,
                routeSelection.reachable(),
                routeSelection.reason());
        return new ApacheAsyncHttpClientBuilder()
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(10))
                .maxConnections(128)
                .maxIdleTimeOut(Duration.ofSeconds(50))
                .proxy(new ProxyOptions(ProxyOptions.Type.HTTP, proxyAddress))
                .build();
    }

    private ICredentialProvider resolveSmsCredentialProvider() {
        if (StrUtil.isNotBlank(smsAccessKeyId) && StrUtil.isNotBlank(smsAccessKeySecret)) {
            return StaticCredentialProvider.create(
                    Credential.builder()
                            .accessKeyId(smsAccessKeyId.trim())
                            .accessKeySecret(smsAccessKeySecret.trim())
                            .build()
            );
        }
        return new EnvironmentVariableCredentialProvider();
    }

    public CompletableFuture<String> uploadFile(String objectKey, byte[] fileBytes) {
        return uploadFileToBucket(DEFAULT_OSS_BUCKET, objectKey, fileBytes);
    }

    public CompletableFuture<String> uploadFileToBucket(String bucket, String objectKey, byte[] fileBytes) {
        return uploadFileToBucket(bucket, OSS_REGION, OSS_ENDPOINT, objectKey, fileBytes);
    }

    public CompletableFuture<String> uploadFileToBucket(String bucket,
                                                        String region,
                                                        String endpoint,
                                                        String objectKey,
                                                        byte[] fileBytes) {
        String resolvedBucket = normalizeBucket(bucket);
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider, region, endpoint);

        return client.putObjectAsync(
                PutObjectRequest.newBuilder()
                        .bucket(resolvedBucket)
                        .key(objectKey)
                        .body(BinaryData.fromBytes(fileBytes))
                        .build()
        ).thenApply(result -> {
            log.info("OSS upload succeeded, bucket={}, key={}, statusCode={}, requestId={}, eTag={}",
                    resolvedBucket, objectKey, result.statusCode(), result.requestId(), result.eTag());
            return buildFileUrl(resolvedBucket, endpoint, objectKey);
        }).whenComplete((url, ex) -> closeClient(client, "upload", resolvedBucket, objectKey, ex));
    }

    public CompletableFuture<String> uploadContent(String objectKey, String content) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider);

        return client.putObjectAsync(
                PutObjectRequest.newBuilder()
                        .bucket(DEFAULT_OSS_BUCKET)
                        .key(objectKey)
                        .body(BinaryData.fromString(content))
                        .build()
        ).thenApply(result -> {
            log.info("OSS content upload succeeded, key={}, statusCode={}, requestId={}, eTag={}",
                    objectKey, result.statusCode(), result.requestId(), result.eTag());
            return buildFileUrl(DEFAULT_OSS_BUCKET, objectKey);
        }).whenComplete((url, ex) -> closeClient(client, "upload-content", DEFAULT_OSS_BUCKET, objectKey, ex));
    }

    public CompletableFuture<Void> deleteFile(String objectKey) {
        return deleteFileFromBucket(DEFAULT_OSS_BUCKET, objectKey);
    }

    public CompletableFuture<Void> deleteFileFromBucket(String bucket, String objectKey) {
        return deleteFileFromBucket(bucket, OSS_REGION, OSS_ENDPOINT, objectKey);
    }

    public CompletableFuture<Void> deleteFileFromBucket(String bucket,
                                                        String region,
                                                        String endpoint,
                                                        String objectKey) {
        String resolvedBucket = normalizeBucket(bucket);
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider, region, endpoint);

        return client.deleteObjectAsync(
                DeleteObjectRequest.newBuilder()
                        .bucket(resolvedBucket)
                        .key(objectKey)
                        .build()
        ).thenAccept(result -> log.info("OSS delete succeeded, bucket={}, key={}, statusCode={}, requestId={}",
                resolvedBucket, objectKey, result.statusCode(), result.requestId()))
                .whenComplete((res, ex) -> closeClient(client, "delete", resolvedBucket, objectKey, ex));
    }

    public CompletableFuture<String> copyFile(String srcKey, String destKey) {
        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSAsyncClient client = buildOssClient(provider);

        return client.copyObjectAsync(
                CopyObjectRequest.newBuilder()
                        .bucket(DEFAULT_OSS_BUCKET)
                        .key(destKey)
                        .sourceBucket(DEFAULT_OSS_BUCKET)
                        .sourceKey(srcKey)
                        .build()
        ).thenApply(result -> {
            log.info("OSS copy succeeded, src={}, dest={}, statusCode={}", srcKey, destKey, result.statusCode());
            return buildFileUrl(DEFAULT_OSS_BUCKET, destKey);
        }).whenComplete((url, ex) -> closeClient(client, "copy", DEFAULT_OSS_BUCKET, destKey, ex));
    }

    public String buildFileUrl(String bucket, String objectKey) {
        return buildFileUrl(bucket, OSS_ENDPOINT, objectKey);
    }

    public String buildFileUrl(String bucket, String endpoint, String objectKey) {
        String host = normalizeEndpoint(endpoint).replace("https://", "");
        return String.format("https://%s.%s/%s", normalizeBucket(bucket), host, objectKey);
    }

    private OSSAsyncClient buildOssClient(CredentialsProvider provider) {
        return buildOssClient(provider, OSS_REGION, OSS_ENDPOINT);
    }

    private OSSAsyncClient buildOssClient(CredentialsProvider provider, String region, String endpoint) {
        return OSSAsyncClient.newBuilder()
                .region(StrUtil.blankToDefault(region, OSS_REGION).trim())
                .endpoint(normalizeEndpoint(endpoint))
                .credentialsProvider(provider)
                .build();
    }

    private String resolveSmsSignName() {
        String signName = System.getenv("ALIYUN_SMS_SIGN_NAME");
        if (signName == null || signName.isBlank()) {
            return "速通互联验证平台";
        }
        return signName.trim();
    }

    private String normalizeBucket(String bucket) {
        return StrUtil.blankToDefault(bucket, DEFAULT_OSS_BUCKET).trim();
    }

    private String normalizeEndpoint(String endpoint) {
        return StrUtil.blankToDefault(endpoint, OSS_ENDPOINT).trim();
    }

    private void closeClient(OSSAsyncClient client,
                             String action,
                             String bucket,
                             String objectKey,
                             Throwable ex) {
        if (ex != null) {
            log.error("OSS {} failed, bucket={}, key={}, error={}", action, bucket, objectKey, ex.getMessage(), ex);
        }
        try {
            client.close();
        } catch (Exception closeError) {
            log.error("OSS client close failed, bucket={}, key={}", bucket, objectKey, closeError);
        }
    }
}
