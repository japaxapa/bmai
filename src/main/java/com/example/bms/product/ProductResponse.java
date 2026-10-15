package com.example.bms.product;

import java.math.BigDecimal;

/**
 * What the product endpoints answer with — the shape the UI table binds to,
 * mapped by hand (api-design: no MapStruct at this scale). The category
 * travels as id + name: the id is what an edit posts back, the name is what
 * the row displays, and neither the UI nor the API needs a second request
 * to join them.
 */
public record ProductResponse(
        long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        BigDecimal cost,
        int minimumStock,
        int stock,
        long categoryId,
        String categoryName,
        boolean active) {
}
