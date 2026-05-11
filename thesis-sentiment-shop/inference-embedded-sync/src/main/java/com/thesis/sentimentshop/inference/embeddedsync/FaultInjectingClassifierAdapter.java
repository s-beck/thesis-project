package com.thesis.sentimentshop.inference.embeddedsync;

import com.thesis.sentimentshop.inference.SentimentClassificationException.FailureMode;
import com.thesis.sentimentshop.inference.FaultInjectingClassifier;
import com.thesis.sentimentshop.inference.embedded.OnnxSentimentClassifier;

import java.util.Set;

final class FaultInjectingClassifierAdapter extends FaultInjectingClassifier implements AutoCloseable {
    private final OnnxSentimentClassifier delegate;

    FaultInjectingClassifierAdapter(OnnxSentimentClassifier delegate,
                                    Set<FailureMode> supportedModes,
                                    String variantName) {
        super(delegate, supportedModes, variantName);
        this.delegate = delegate;
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
