package com.thesis.sentimentshop.web.api;

import com.thesis.sentimentshop.catalog.CatalogService;
import com.thesis.sentimentshop.web.api.dto.ProductDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// AI-assisted code: Created during baseline app init. Generated with Claude (Anthropic) and reviewed by the author.

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public Page<ProductDTO> list(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<?> raw = (category == null)
                ? catalog.list(pageable)
                : catalog.listByCategory(category, pageable);
        return ((Page<com.thesis.sentimentshop.catalog.Product>) raw)
                .map(ProductDTO::from);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> get(@PathVariable Long id) {
        return catalog.findById(id)
                .map(ProductDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
