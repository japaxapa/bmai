package com.example.bms.supplier;

/**
 * What the supplier endpoints answer with — the shape the UI table binds to,
 * mapped by hand (api-design: no MapStruct at this scale).
 */
public record SupplierResponse(
        long id,
        String name,
        String document,
        String email,
        int paymentTermsDays,
        boolean active) {
}
