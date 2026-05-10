package com.example.ShoppingSystem.tools.ip2location.verify.matcher;

import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Ip2LocationMailMatcher {

    private final String senderDomainFilter;
    private final String subjectKeywordFilter;

    public Ip2LocationMailMatcher(String senderDomainFilter, String subjectKeywordFilter) {
        this.senderDomainFilter = normalizeFilter(senderDomainFilter);
        this.subjectKeywordFilter = normalizeFilter(subjectKeywordFilter);
    }

    public boolean hasSenderFilter() {
        return !isBlank(senderDomainFilter);
    }

    public boolean senderMatches(String sender) {
        return !isBlank(senderDomainFilter)
                && sender != null
                && sender.toLowerCase(Locale.ROOT).contains(senderDomainFilter);
    }

    public boolean isPotentialIp2LocationMail(String sender, String subject) {
        String normalizedSubject = subject == null ? "" : subject.toLowerCase(Locale.ROOT);
        if (senderMatches(sender)) {
            return true;
        }
        return !isBlank(subjectKeywordFilter) && normalizedSubject.contains(subjectKeywordFilter);
    }

    public SearchTerm buildVerifyLinkCandidateTerm() {
        List<SearchTerm> searchTerms = new ArrayList<>(2);
        if (!isBlank(senderDomainFilter)) {
            searchTerms.add(new FromStringTerm(senderDomainFilter));
        }
        if (!isBlank(subjectKeywordFilter)) {
            searchTerms.add(new SubjectTerm(subjectKeywordFilter));
        }
        if (searchTerms.isEmpty()) {
            return null;
        }
        if (searchTerms.size() == 1) {
            return searchTerms.get(0);
        }
        return new OrTerm(searchTerms.get(0), searchTerms.get(1));
    }

    public SearchTerm buildSenderOnlyTerm() {
        if (isBlank(senderDomainFilter)) {
            return null;
        }
        return new FromStringTerm(senderDomainFilter);
    }

    private String normalizeFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
