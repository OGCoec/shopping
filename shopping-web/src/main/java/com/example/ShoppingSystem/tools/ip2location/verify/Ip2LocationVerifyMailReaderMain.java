package com.example.ShoppingSystem.tools.ip2location.verify;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Locale;

/**
 * Manual entry point for reading the latest IP2Location verify token from Microsoft mail.
 */
public class Ip2LocationVerifyMailReaderMain {

    static {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("Enter credentials (email----password----client_id----refresh_token): ");
        String credentials = reader.readLine().trim();

        Ip2LocationVerifyMailReaderService service = new Ip2LocationVerifyMailReaderService(
                new ObjectMapper(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build(),
                "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                "imap-mail.outlook.com",
                993,
                "https://outlook.office.com/IMAP.AccessAsUser.All offline_access",
                20,
                List.of("Junk Email", "INBOX"),
                "ip2location.io",
                "ip2location",
                "127.0.0.1",
                7892,
                0
        );

        long startNanos = System.nanoTime();
        Ip2LocationVerifyMailReaderService.VerifyLinkReadResult result =
                service.readLatestVerifyLinkFromCredentials(credentials);
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0d;

        if (!result.success()) {
            System.out.println("Verify link read failed.");
            System.out.println("Reason: " + result.reason());
            System.out.println("ElapsedSeconds: " + String.format(Locale.ROOT, "%.3f", elapsedSeconds));
            return;
        }

        System.out.println("Verify link read succeeded.");
        System.out.println("Email: " + result.email());
        System.out.println("Folder: " + result.folderName());
        System.out.println("Sender: " + result.sender());
        System.out.println("Subject: " + result.subject());
        System.out.println("ReceivedAt: " + result.receivedAt());
        System.out.println("VerifyToken: " + result.verifyToken());
        System.out.println("VerifyUrl: " + result.verifyUrl());
        System.out.println("ElapsedSeconds: " + String.format(Locale.ROOT, "%.3f", elapsedSeconds));
    }
}
