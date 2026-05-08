package com.thesis.sentimentshop.web.api.dto;

import com.thesis.sentimentshop.catalog.Product;

import java.math.BigDecimal;

public record ProductDTO(Long id,
                         String name,
                         String description,
                         BigDecimal price,
                         String imageUrl,
                         String category) {

    public static ProductDTO from (Product product) {
        return new ProductDTO(product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.getCategory()
        );
    }
}

