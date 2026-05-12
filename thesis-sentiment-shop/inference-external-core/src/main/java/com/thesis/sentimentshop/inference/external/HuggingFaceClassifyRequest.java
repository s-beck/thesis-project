package com.thesis.sentimentshop.inference.external;

public record HuggingFaceClassifyRequest(String inputs, Options options) {

    public static HuggingFaceClassifyRequest of(String text) {
        return new HuggingFaceClassifyRequest(text, Options.NO_WAIT_FOR_MODEL);
    }

    public record Options(boolean wait_for_model) {
        static final Options NO_WAIT_FOR_MODEL = new Options(false);
    }
}