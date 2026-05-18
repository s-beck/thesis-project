package com.thesis.sentimentshop.web.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public record SubmitReviewRequest(
        @NotBlank @Size(max = 4000) String text,
        @Min(1) @Max(5) int rating
) {}
