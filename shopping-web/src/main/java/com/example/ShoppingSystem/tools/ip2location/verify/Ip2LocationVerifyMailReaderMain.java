package com.example.ShoppingSystem.tools.ip2location.verify;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Manual entry point for reading the latest IP2Location verify token from Microsoft mail.
 */
public class Ip2LocationVerifyMailReaderMain {

    static {
        Ip2LocationVerifyMailMainSupport.configureUtf8Console();
    }

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        System.out.print("Enter credentials (email----password----client_id----refresh_token): ");
        String credentials = reader.readLine().trim();

        Ip2LocationVerifyMailReaderService service =
                Ip2LocationVerifyMailMainSupport.createMailReaderService();

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
