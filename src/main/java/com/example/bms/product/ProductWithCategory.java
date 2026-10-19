package com.example.bms.product;

/**
 * What the port's read side answers with: the product plus the category
 * name its foreign key points at, resolved in the repository's own join so
 * listing a page of products is one round trip — not N lookups and not a
 * second query in the service.
 */
public record ProductWithCategory(Product product, String categoryName) {
}
