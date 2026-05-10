package com.example.ShoppingSystem.tools.ip2location.verify;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Manual entry point for checking whether a mailbox has IP2Location registration or verify mail traces.
 */
public class Ip2LocationRegistrationMailCheckMain {

    static {
        Ip2LocationVerifyMailMainSupport.configureUtf8Console();
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("请输入邮箱凭证(email----password----client_id----refresh_token): ");
        String credentials = reader.readLine().trim();

        Ip2LocationVerifyMailReaderService service =
                Ip2LocationVerifyMailMainSupport.createMailReaderService();

        long startNanos = System.nanoTime();
        Ip2LocationVerifyMailReaderService.RegistrationMailCheckResult result =
                service.checkRegistrationMailTraceFromCredentials(credentials);
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0d;

        if (result.success()) {
            System.out.println("判断结果: 已发现 IP2Location 发件人邮件痕迹");
            System.out.println("邮箱: " + result.email());
            System.out.println("所在文件夹: " + result.folderName());
            System.out.println("发件人: " + result.sender());
            System.out.println("邮件主题: " + result.subject());
            System.out.println("接收时间: " + result.receivedAt());
            System.out.println("耗时秒数: " + String.format(Locale.ROOT, "%.3f", elapsedSeconds));
            return;
        }

        if ("ip2location_sender_not_found".equals(result.reason())
                || result.reason() != null && result.reason().startsWith("ip2location_sender_not_found_in_")) {
            System.out.println("判断结果: 未发现 IP2Location 发件人邮件痕迹");
        } else {
            System.out.println("判断结果: 检查失败，不能判断是否注册过");
        }
        System.out.println("原因: " + result.reason());
        System.out.println("耗时秒数: " + String.format(Locale.ROOT, "%.3f", elapsedSeconds));
    }
}
