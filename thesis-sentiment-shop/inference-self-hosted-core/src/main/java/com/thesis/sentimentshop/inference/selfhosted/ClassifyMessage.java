package com.thesis.sentimentshop.inference.selfhosted;

public record ClassifyMessage(long reviewId, String text) {
}