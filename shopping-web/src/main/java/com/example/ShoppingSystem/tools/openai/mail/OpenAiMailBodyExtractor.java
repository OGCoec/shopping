package com.example.ShoppingSystem.tools.openai.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;

import java.io.IOException;

public final class OpenAiMailBodyExtractor {

    private static final int MAX_CONTENT_CHARS = 200_000;

    public String extractText(Part part) throws MessagingException, IOException {
        StringBuilder builder = new StringBuilder();
        appendText(part, builder);
        return normalizeContent(builder.toString());
    }

    private void appendText(Part part, StringBuilder builder) throws MessagingException, IOException {
        if (part == null || builder.length() >= MAX_CONTENT_CHARS) {
            return;
        }
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            if (content instanceof String text) {
                appendLimited(builder, text);
            }
            return;
        }
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            if (content instanceof String html) {
                appendLimited(builder, html.replaceAll("(?s)<[^>]+>", " "));
            }
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof MimeMultipart multipart) {
                for (int i = 0; i < multipart.getCount(); i += 1) {
                    BodyPart bodyPart = multipart.getBodyPart(i);
                    appendText(bodyPart, builder);
                    if (builder.length() >= MAX_CONTENT_CHARS) {
                        return;
                    }
                }
            }
            return;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                appendText(nestedPart, builder);
            }
        }
    }

    private void appendLimited(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        int remaining = MAX_CONTENT_CHARS - builder.length();
        if (remaining <= 0) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(text, 0, Math.min(text.length(), remaining));
    }

    private String normalizeContent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.replace("\\/", "/")
                .replace("&amp;", "&")
                .replace("&#x2F;", "/")
                .replace("&#47;", "/")
                .replace("&#x3D;", "=")
                .replace("&#61;", "=")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }
}
