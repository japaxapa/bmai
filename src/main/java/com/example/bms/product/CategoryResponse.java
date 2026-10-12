package com.example.bms.product;

/**
 * What the category endpoints answer with — the shape the UI table binds to,
 * mapped by hand (api-design: no MapStruct at this scale).
 */
public record CategoryResponse(long id, String name, boolean active) {
}
