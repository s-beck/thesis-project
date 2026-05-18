package com.thesis.sentimentshop.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByCategory(String category, Pageable pageable);
}
