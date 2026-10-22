package com.example.bms.customer;

/**
 * What the customer endpoints answer with — the shape the UI table binds to,
 * mapped by hand (api-design: no MapStruct at this scale).
 */
public record CustomerResponse(
        long id,
        String name,
        String document,
        String email,
        int paymentTermsDays,
        boolean active) {
}
