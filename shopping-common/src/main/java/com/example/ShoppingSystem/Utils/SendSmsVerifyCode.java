package com.example.ShoppingSystem.Utils;


import com.aliyun.auth.credentials.provider.EnvironmentVariableCredentialProvider;

import com.aliyun.sdk.service.dypnsapi20170525.models.*;
import com.aliyun.sdk.service.dypnsapi20170525.*;
import com.google.gson.Gson;

import darabonba.core.client.ClientOverrideConfiguration;

//官方速度快
//import javax.net.ssl.KeyManager;
//import javax.net.ssl.X509TrustManager;

import java.util.concurrent.CompletableFuture;


public class SendSmsVerifyCode {
    public static void main(String[] args) throws Exception {

        // HttpClient Configuration
        /*HttpClient httpClient = new ApacheAsyncHttpClientBuilder()
                .connectionTimeout(Duration.ofSeconds(10)) // Set the connection timeout time, the default is 10 seconds
                .responseTimeout(Duration.ofSeconds(10)) // Set the response timeout time, the default is 20 seconds
                .maxConnections(128) // Set the connection pool size
                .maxIdleTimeOut(Duration.ofSeconds(50)) // Set the connection pool timeout, the default is 30 seconds
                // Configure the proxy
                .proxy(new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress("<your-proxy-hostname>", 9001))
                        .setCredentials("<your-proxy-username>", "<your-proxy-password>"))
                // If it is an https connection, you need to configure the certificate, or ignore the certificate(.ignoreSSL(true))
                .x509TrustManagers(new X509TrustManager[]{})
                .keyManagers(new KeyManager[]{})
                .ignoreSSL(false)
                .build();*/

        // Configure Credentials authentication information
        EnvironmentVariableCredentialProvider provider = new EnvironmentVariableCredentialProvider();

        // Configure the Client
        try (AsyncClient client = AsyncClient.builder()
                .region("cn-hangzhou") // Region ID
                //.httpClient(httpClient) // Use the configured HttpClient, otherwise use the default HttpClient (Apache HttpClient)
                .credentialsProvider(provider)
                //.serviceConfiguration(Configuration.create()) // Service-level configuration
                // Client-level configuration rewrite, can set Endpoint, Http request parameters, etc.
                .overrideConfiguration(
                        ClientOverrideConfiguration.create()
                                  // Endpoint 请参考 https://api.aliyun.com/product/Dypnsapi
                                .setEndpointOverride("dypnsapi.aliyuncs.com")
                        //.setConnectTimeout(Duration.ofSeconds(30))
                )
                .build()) {

            // Parameter settings for API request
            SendSmsVerifyCodeRequest sendSmsVerifyCodeRequest = SendSmsVerifyCodeRequest.builder()
                    .phoneNumber("18060978626")
                    .signName("速通互联验证码")
                    .templateCode("100001")
                    .templateParam("{\"code\":\"1234\",\"min\":\"5\"}")
                    // Request-level configuration rewrite, can set Http request parameters, etc.
                    // .requestConfiguration(RequestConfiguration.create().setHttpHeaders(new HttpHeaders()))
                    .build();

            // Asynchronously get the return value of the API request
            CompletableFuture<SendSmsVerifyCodeResponse> response = client.sendSmsVerifyCode(sendSmsVerifyCodeRequest);
            // Synchronously get the return value of the API request
            SendSmsVerifyCodeResponse resp = response.get();
            System.out.println(new Gson().toJson(resp));
            // Asynchronous processing of return values
            /*response.thenAccept(resp -> {
                System.out.println(new Gson().toJson(resp));
            }).exceptionally(throwable -> { // Handling exceptions
                System.out.println(throwable.getMessage());
                return null;
            });*/

        }
    }

}
