package com.thesis.sentimentshop.sampledata;

import com.thesis.sentimentshop.catalog.Product;
import com.thesis.sentimentshop.catalog.ProductRepository;
import com.thesis.sentimentshop.inference.Sentiment;
import com.thesis.sentimentshop.reviews.Review;
import com.thesis.sentimentshop.reviews.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SampleDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final String CSV_PATH = "reviews_seed.csv";

    private static final String PLACEHOLDER_DESCRIPTION =
            "Placeholder description — products in this catalog exist for "
                    + "demonstration only and are not part of the experimental dataset.";

    private static final BigDecimal PLACEHOLDER_PRICE = new BigDecimal("9.99");

    private final ProductRepository products;
    private final ReviewRepository reviews;

    public SampleDataLoader(ProductRepository products, ReviewRepository reviews) {
        this.products = products;
        this.reviews = reviews;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        long existing = products.count();
        if (existing > 0) {
            log.info("Sample data loader: {} products already present, skipping.", existing);
            return;
        }

        log.info("Sample data loader: empty database detected, loading seed dataset…");
        LoadResult result = loadFromCsv();
        log.info("Sample data loader: loaded {} products and {} reviews.",
                result.productCount, result.reviewCount);
    }

    private LoadResult loadFromCsv() throws IOException {
        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Curated dataset not found on classpath: " + CSV_PATH);
        }

        Map<String, Product> productByDatasetId = new HashMap<>();
        List<Review> pendingReviews = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine();
            if (header == null) {
                throw new IllegalStateException("Curated dataset is empty");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                CsvRow row = parseRow(line);
                if (row == null) continue;

                Product product = productByDatasetId.computeIfAbsent(
                        row.productId,
                        id -> products.save(buildProduct(row)));

                Review review = new Review(product, "demo_user", row.reviewText, row.rating);
                review.recordSentiment(row.sentiment, 1.0);
                pendingReviews.add(review);
            }
        }

        reviews.saveAll(pendingReviews);
        return new LoadResult(productByDatasetId.size(), pendingReviews.size());
    }

    private Product buildProduct(CsvRow row) {
        return new Product(row.productTitle, PLACEHOLDER_DESCRIPTION, PLACEHOLDER_PRICE, null, row.category);
    }

    private CsvRow parseRow(String line) {
        List<String> fields = splitCsv(line);
        if (fields.size() != 6) {
            log.debug("Skipping malformed CSV row: {}", line);
            return null;
        }
        try {
            return new CsvRow(
                    fields.get(0),
                    fields.get(1),
                    fields.get(2),
                    fields.get(3),
                    Integer.parseInt(fields.get(4)),
                    Sentiment.valueOf(fields.get(5))
            );
        } catch (IllegalArgumentException e) {
            log.debug("Skipping unparseable CSV row: {}", line);
            return null;
        }
    }

    private List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private record CsvRow(
            String productId,
            String productTitle,
            String category,
            String reviewText,
            int rating,
            Sentiment sentiment
    ) {}

    private record LoadResult(int productCount, int reviewCount) {}
}
