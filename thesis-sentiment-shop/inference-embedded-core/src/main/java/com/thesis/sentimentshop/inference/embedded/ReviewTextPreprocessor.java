package com.thesis.sentimentshop.inference.embedded;

import java.util.regex.Pattern;

/**
 * Replicates the {@code preprocess()} step published on the
 * {@code cardiffnlp/twitter-roberta-base-sentiment-latest} model card:
 * mentions of the form {@code @handle} are replaced with the literal
 * token {@code @user}, and URLs are replaced with the literal token
 * {@code http}.
 *
 * <p>The model was fine-tuned on tweets that had this preprocessing
 * applied, so applying the same transformation at inference time is
 * required for the predicted distribution to match the one observed
 * during fine-tuning. For typical product-review text this is a no-op,
 * since they rarely contain @-mentions or URLs, but applying it
 * unconditionally is cheap and removes one possible source of variance
 * across the six variants. The same preprocessor will be reused
 * by the S-* and X-* variants so that all six see identical model input.
 *
 * <p>Stateless, thread-safe.
 */

// AI-assisted code: Generated with Claude (Anthropic) and reviewed/modified by the author.
public class ReviewTextPreprocessor {
    private static final Pattern MENTION = Pattern.compile("@\\S+");

    private static final Pattern URL = Pattern.compile("https?://\\S+");

    public String preprocess(String text) {
        // Order matters: replace URLs first, otherwise an URL containing
        // an @ (e.g. an email-style query string) would be partially
        // mangled by the mention pattern.
        String urlsReplaced = URL.matcher(text).replaceAll("http");
        return MENTION.matcher(urlsReplaced).replaceAll("@user");
    }
}
