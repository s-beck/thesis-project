package com.thesis.sentimentshop.inference.external;

public record ClassifyMessage(long reviewId, String text) {
}