package com.example.ShoppingSystem.tools.openai.mail;

import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OpenAiMailMatcher {

    private final List<String> senderKeywords;
    private final List<String> subjectKeywords;
    private final List<String> evidencePhrases;

    public OpenAiMailMatcher(String senderKeywords, String subjectKeywords, String evidencePhrases) {
        this.senderKeywords = normalizeCsv(senderKeywords);
        this.subjectKeywords = normalizeCsv(subjectKeywords);
        this.evidencePhrases = normalizeCsv(evidencePhrases);
    }

    public boolean isPotentialOpenAiMail(String sender, String subject) {
        String normalizedSender = normalize(sender);
        String normalizedSubject = normalize(subject);
        return containsAny(normalizedSender, senderKeywords) || containsAny(normalizedSubject, subjectKeywords);
    }

    public String findEvidencePhrase(String rawContent) {
        String normalizedContent = normalize(rawContent);
        if (normalizedContent == null) {
            return null;
        }
        for (String phrase : evidencePhrases) {
            if (normalizedContent.contains(phrase)) {
                return phrase;
            }
        }
        return null;
    }

    public SearchTerm buildCandidateTerm() {
        List<SearchTerm> terms = new ArrayList<>();
        for (String keyword : senderKeywords) {
            terms.add(new FromStringTerm(keyword));
        }
        for (String keyword : subjectKeywords) {
            terms.add(new SubjectTerm(keyword));
        }
        if (terms.isEmpty()) {
            return null;
        }
        SearchTerm combined = terms.get(0);
        for (int i = 1; i < terms.size(); i += 1) {
            combined = new OrTerm(combined, terms.get(i));
        }
        return combined;
    }

    private boolean containsAny(String value, List<String> keywords) {
        if (value == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : raw.split(",")) {
            String normalized = normalize(part);
            if (normalized != null && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
