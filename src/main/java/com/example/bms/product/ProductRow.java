package com.example.bms.product;

/**
 * What the port's read side answers with: the product plus the names its
 * foreign keys point at, resolved in the repository's own joins so listing a
 * page of products is one round trip — not N lookups and not a second query
 * in the service. The supplier is nullable (V6), so its name is a
 * {@code String} that may be null: the absent supplier is expressed as an
 * absent answer, not a sentinel the UI would have to filter out.
 */
public record ProductRow(Product product, String categoryName,
                                  String supplierName) {
}
