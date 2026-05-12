package com.thesis.sentimentshop.inference.external;

public record HuggingFaceClassifyResponse(String label, double score) {
}