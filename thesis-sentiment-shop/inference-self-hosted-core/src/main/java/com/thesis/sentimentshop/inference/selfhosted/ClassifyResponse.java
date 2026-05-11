package com.thesis.sentimentshop.inference.selfhosted;

public record ClassifyResponse(String sentiment, double confidence, int latencyMs) {
}
