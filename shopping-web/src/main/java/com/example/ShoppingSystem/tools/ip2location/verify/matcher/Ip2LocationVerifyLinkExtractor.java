package com.example.ShoppingSystem.tools.ip2location.verify.matcher;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Ip2LocationVerifyLinkExtractor {

    private static final Pattern VERIFY_URL_PATTERN = Pattern.compile(
            "https://www\\.ip2location\\.io/verify\\?code=([A-Za-z0-9_-]+)",
            Pattern.CASE_INSENSITIVE);

    public String extractVerifyUrlFromMessage(Message message) throws MessagingException, IOException {
        String fromSubject = extractVerifyUrl(message.getSubject());
        if (fromSubject != null) {
            return fromSubject;
        }
        return extractVerifyUrlFromPart(message);
    }

    public String extractVerifyToken(String verifyUrl) {
        if (isBlank(verifyUrl)) {
            return null;
        }
        Matcher matcher = VERIFY_URL_PATTERN.matcher(verifyUrl);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String extractVerifyUrlFromPart(Part part) throws MessagingException, IOException {
        if (part == null) {
            return null;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String text) {
                return extractVerifyUrl(text);
            }
            return null;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content instanceof String html) {
                return extractVerifyUrl(html);
            }
            return null;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof MimeMultipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    String found = extractVerifyUrlFromPart(bodyPart);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                return extractVerifyUrlFromPart(nestedPart);
            }
        }
        return null;
    }

    private String extractVerifyUrl(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        Matcher matcher = VERIFY_URL_PATTERN.matcher(normalizeRawContent(raw));
        if (!matcher.find()) {
            return null;
        }
        return "https://www.ip2location.io/verify?code=" + matcher.group(1);
    }

    private String normalizeRawContent(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("&#x2F;", "/")
                .replace("&#47;", "/")
                .replace("&#x3D;", "=")
                .replace("&#61;", "=")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
