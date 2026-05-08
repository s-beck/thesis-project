package com.thesis.sentimentshop.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository products;

    public CatalogService(ProductRepository products) {
        this.products = products;
    }

    public Page<Product> list(Pageable pageable) {
        return products.findAll(pageable);
    }

    public Page<Product> listByCategory(String category, Pageable pageable) {
        return products.findByCategory(category, pageable);
    }

    public Optional<Product> findById(Long id) {
        return products.findById(id);
    }
}
