package com.example.ShoppingSystem.tools.ip2location.verify.imap;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ImapFolderScanPlanner {

    private static final Logger log = LoggerFactory.getLogger(ImapFolderScanPlanner.class);
    private static final List<String> JUNK_FOLDER_KEYWORDS = List.of(
            "junk", "spam", "bulk", "垃圾", "垃圾邮件", "广告邮件");
    private static final List<String> INBOX_FOLDER_KEYWORDS = List.of(
            "inbox", "收件箱");

    private final List<String> configuredFolderOrder;

    public ImapFolderScanPlanner(List<String> configuredFolderOrder) {
        this.configuredFolderOrder = configuredFolderOrder == null || configuredFolderOrder.isEmpty()
                ? List.of("Junk Email", "INBOX")
                : List.copyOf(configuredFolderOrder);
    }

    public List<String> resolveScanOrder(Store store) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        List<String> discoveredFolders = discoverReadableFolders(store);
        for (String folderName : discoveredFolders) {
            if (containsFolderKeyword(folderName, JUNK_FOLDER_KEYWORDS)) {
                ordered.add(folderName);
            }
        }
        for (String folderName : discoveredFolders) {
            if (containsFolderKeyword(folderName, INBOX_FOLDER_KEYWORDS)) {
                ordered.add(folderName);
            }
        }

        for (String configuredName : configuredFolderOrder) {
            String resolved = resolveExistingFolderName(discoveredFolders, configuredName);
            if (!isBlank(resolved)) {
                ordered.add(resolved);
            }
        }

        ordered.addAll(discoveredFolders);
        return new ArrayList<>(ordered);
    }

    private List<String> discoverReadableFolders(Store store) {
        LinkedHashSet<String> folderNames = new LinkedHashSet<>();
        try {
            Folder defaultFolder = store.getDefaultFolder();
            if (defaultFolder == null) {
                return List.of();
            }
            collectReadableFolders(defaultFolder, folderNames, 0);
            log.info("Discovered IMAP folders, folders={}", folderNames);
        } catch (MessagingException e) {
            log.warn("Discover IMAP folders failed, reason={}", e.getMessage());
        }
        return new ArrayList<>(folderNames);
    }

    private void collectReadableFolders(Folder folder, Set<String> collector, int depth) throws MessagingException {
        if (folder == null || depth > 8) {
            return;
        }

        String fullName = trimToNull(folder.getFullName());
        if (fullName != null
                && !fullName.equals("/")
                && !fullName.equalsIgnoreCase("[Gmail]")
                && holdsMessages(folder)) {
            collector.add(fullName);
        }

        Folder[] children = folder.list();
        if (children == null || children.length == 0) {
            return;
        }
        for (Folder child : children) {
            collectReadableFolders(child, collector, depth + 1);
        }
    }

    private boolean holdsMessages(Folder folder) throws MessagingException {
        int type = folder.getType();
        return (type & Folder.HOLDS_MESSAGES) != 0;
    }

    private boolean containsFolderKeyword(String folderName, List<String> keywords) {
        if (isBlank(folderName) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = folderName.trim().toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (!isBlank(keyword) && normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String resolveExistingFolderName(List<String> discoveredFolders, String configuredName) {
        if (discoveredFolders == null || discoveredFolders.isEmpty() || isBlank(configuredName)) {
            return null;
        }
        String normalizedConfigured = configuredName.trim().toLowerCase(Locale.ROOT);
        for (String discoveredFolder : discoveredFolders) {
            if (!isBlank(discoveredFolder)
                    && discoveredFolder.trim().toLowerCase(Locale.ROOT).equals(normalizedConfigured)) {
                return discoveredFolder;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
