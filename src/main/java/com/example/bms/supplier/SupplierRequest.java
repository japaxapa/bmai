package com.example.bms.supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The write DTO create and edit share — one record holds the field rules, so
 * create cannot drift from edit. {@code name} and {@code document} are the
 * identity pair a party cannot exist without; {@code email} is contact info
 * (requirements: "email not unique — contact info, not identity") and
 * {@code paymentTermsDays} is optional, born 30 when the box is left empty.
 *
 * <p>Document *validity* (CPF/CNPJ length and check digits) is a domain rule,
 * not a shape rule — it lives on the entity, so every write path funnels
 * through it. {@code name}'s 120 cap is the opposite: a shape rule, exactly
 * like the category field's, answered here as a 400 naming the field. Payment
 * terms are a business range again — the entity refuses a negative one as a
 * 422, like Product's minimum stock.
 */
public record SupplierRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 120, message = "Name must be at most 120 characters.")
        String name,
        @NotBlank(message = "Document is required.")
        String document,
        String email,
        Integer paymentTermsDays) {
}
