package com.thesis.sentimentshop.web.api;

import com.thesis.sentimentshop.reviews.Review;
import com.thesis.sentimentshop.reviews.ReviewService;
import com.thesis.sentimentshop.web.api.dto.ReviewDTO;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public Page<ReviewDTO> list(
            @PathVariable Long productId,
            @PageableDefault(size = 20) Pageable pageable) {
        return reviews.listForProduct(productId, pageable).map(ReviewDTO::from);
    }

    @PostMapping
    public ResponseEntity<ReviewDTO> submit(
            @PathVariable Long productId,
            @Valid @RequestBody SubmitReviewRequest request,
            @AuthenticationPrincipal UserDetails user) {
        Review created = reviews.submit(
                productId, user.getUsername(), request.text(), request.rating());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewDTO.from(created));
    }
}
