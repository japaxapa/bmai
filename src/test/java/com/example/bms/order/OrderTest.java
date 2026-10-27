package com.example.bms.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seam: the {@link Order} aggregate, pure and without mocks. This is where
 * the totals rule (business-rules §1 / GLOSSARY "Total") is decided, so the
 * HTTP slice and the integration journey can both stay about plumbing: a
 * line's subtotal is quantity × unit price and the order total is the sum of
 * the line subtotals. Expected values are worked examples, not recomputed by
 * the code under test.
 */
class OrderTest {

    @Test
    void aDraftTotalsEachLineAsQuantityTimesUnitPriceAndSumsThem() {
        var order = Order.draft(7, "Mercado Aurora ME", List.of(
                OrderItem.of(11, "cafe-1", "Café Torrado 1kg", 2, new BigDecimal("12.50")),
                OrderItem.of(12, "acucar-1", "Açúcar Refinado 1kg", 3, new BigDecimal("4.10"))));

        // 2 × 12.50 = 25.00; 3 × 4.10 = 12.30; total 37.30.
        assertThat(order.items().get(0).subtotal()).isEqualByComparingTo("25.00");
        assertThat(order.items().get(1).subtotal()).isEqualByComparingTo("12.30");
        assertThat(order.total()).isEqualByComparingTo("37.30");
    }

    /**
     * AC: "Editing a non-draft sales order is rejected." The guard lives on
     * the aggregate (ADR 0017), not in the service, so no later caller can
     * bypass it by skipping the service. Every non-draft state is named: the
     * rule is about DRAFT being the only editable status, not about CONFIRMED
     * happening to be the first one ticket #10 creates.
     */
    @ParameterizedTest
    @EnumSource(value = OrderStatus.class,
            names = {"CONFIRMED", "PROCESSING", "COMPLETED", "CANCELLED", "RETURNED"})
    void editingANonDraftOrderIsRefused(OrderStatus status) {
        var stored = new Order(1, "ORD-2026-0001", 7, "Mercado Aurora ME", status,
                List.of(OrderItem.of(11, "cafe-1", "Café Torrado 1kg", 2,
                        new BigDecimal("12.50"))),
                BigDecimal.ZERO, Instant.parse("2026-10-20T12:00:00Z"));

        assertThatThrownBy(() -> stored.withDraft(7, "Mercado Aurora ME", List.of()))
                .isInstanceOf(OrderNotEditableException.class)
                .hasMessage("Only DRAFT sales orders can be edited.");
    }

    /**
     * The other half of the same rule: a DRAFT really is editable. The edit
     * replaces the lines wholesale (PUT /orders/{id} carries the full item
     * array — adding, removing and re-quantifying are the same verb), swaps
     * the customer, and keeps the identity a person reads — number, id,
     * status, creation stamp.
     */
    @Test
    void editingADraftReplacesItsItemsAndKeepsItsNumber() {
        var createdAt = Instant.parse("2026-10-20T12:00:00Z");
        var draft = new Order(1, "ORD-2026-0001", 7, "Mercado Aurora ME",
                OrderStatus.DRAFT,
                List.of(OrderItem.of(11, "cafe-1", "Café Torrado 1kg", 2,
                        new BigDecimal("12.50"))),
                BigDecimal.ZERO, createdAt);

        var edited = draft.withDraft(8, "Padaria São Jorge Ltda", List.of(
                OrderItem.of(12, "acucar-1", "Açúcar Refinado 1kg", 3,
                        new BigDecimal("4.10"))));

        assertThat(edited.id()).isEqualTo(1);
        assertThat(edited.number()).isEqualTo("ORD-2026-0001");
        assertThat(edited.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(edited.createdAt()).isEqualTo(createdAt);
        assertThat(edited.customerId()).isEqualTo(8);
        assertThat(edited.customerName()).isEqualTo("Padaria São Jorge Ltda");
        assertThat(edited.items()).extracting(OrderItem::productId).containsExactly(12L);
        assertThat(edited.total()).isEqualByComparingTo("12.30");
    }

    /**
     * AC: "Invalid quantities rejected with field-level errors." The HTTP
     * layer refuses a non-positive quantity as a 400 naming the item field
     * (@Positive on the DTO); this is the domain backstop, so a later caller
     * that bypasses the DTO still cannot put a zero or negative quantity into
     * an order — the rule lives on the value object (ADR 0017).
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void aLineWithANonPositiveQuantityIsRefused(int quantity) {
        assertThatThrownBy(() -> OrderItem.of(11, "cafe-1", "Café Torrado 1kg",
                quantity, new BigDecimal("12.50")))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessage("Quantity must be greater than zero.");
    }
}
