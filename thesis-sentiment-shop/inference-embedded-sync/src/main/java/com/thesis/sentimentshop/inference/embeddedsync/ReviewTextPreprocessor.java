package com.thesis.sentimentshop.inference.embeddedsync;

import java.util.regex.Pattern;

public class ReviewTextPreprocessor {
    private static final Pattern MENTION = Pattern.compile("@\\S+");

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    String preprocess(String text) {
        // Order matters: replace URLs first, otherwise an URL containing
        // an @ (e.g. an email-style query string) would be partially
        // mangled by the mention pattern.
        String urlsReplaced = URL.matcher(text).replaceAll("http");
        return MENTION.matcher(urlsReplaced).replaceAll("@user");
    }
}
