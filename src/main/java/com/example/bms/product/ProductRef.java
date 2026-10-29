package com.example.bms.product;

import java.math.BigDecimal;

/**
 * The slice of a product an order line needs: the id to store, the sku and
 * name to display, the current price to copy onto the line while the order
 * is still a DRAFT, and the flag that decides whether it may be sold at all
 * (business-rules §5). A narrow read like {@code SupplierService.findName}
 * — the order feature reaches it through {@link ProductService}, never the
 * repository or the entity (architecture §1).
 */
public record ProductRef(long id, String sku, String name, BigDecimal price,
                         boolean active) {
}
