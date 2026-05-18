package com.thesis.sentimentshop.web.api;

import com.thesis.sentimentshop.reviews.ReviewService;
import com.thesis.sentimentshop.web.api.dto.SentimentSummaryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@RestController
@RequestMapping("/api/sentiment")
public class SentimentSummaryController {

    private final ReviewService reviews;

    public SentimentSummaryController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/summary")
    public SentimentSummaryDTO summary() {
        return SentimentSummaryDTO.from(reviews.summary());
    }
}
