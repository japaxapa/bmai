package com.example.bms.supplier;

import jakarta.validation.constraints.NotBlank;

/**
 * The write DTO create and edit share — one record holds the field rules, so
 * create cannot drift from edit. {@code name} and {@code document} are the
 * identity pair a party cannot exist without; {@code email} is contact info
 * (requirements: "email not unique — contact info, not identity") and
 * {@code paymentTermsDays} is optional, born 30 when the box is left empty.
 *
 * <p>Document *validity* (CPF/CNPJ length and check digits) is a domain rule,
 * not a shape rule — it lives on the entity, so every write path funnels
 * through it.
 */
public record SupplierRequest(
        @NotBlank(message = "Name is required.")
        String name,
        @NotBlank(message = "Document is required.")
        String document,
        String email,
        Integer paymentTermsDays) {
}
