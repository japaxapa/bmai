package com.example.bms.order;

import com.example.bms.customer.CustomerRef;
import com.example.bms.customer.CustomerService;
import com.example.bms.product.ProductRef;
import com.example.bms.product.ProductService;
import com.example.bms.shared.PageWindow;
import com.example.bms.shared.PagedEnvelope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

/**
 * Sales-order use cases (architecture §2: the application service is
 * transaction + load + authorize + save). This ticket owns the DRAFT side of
 * the lifecycle — create, replace, read. Confirm and everything after it
 * arrive with ticket #10.
 *
 * <p>business-rules §4 gives every signed-in role the order flow through
 * COMPLETED, so all three writes below share one gate; the reads stay open to
 * any signed-in caller, like every other list a clerk needs.
 *
 * <p>The customer and products are read through their own features' public
 * services (architecture §1), never their repositories. A deactivated party
 * or product is refused here — "blocked in all *new* documents"
 * (business-rules §5) — and an unknown id answers that feature's own 404.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final CustomerService customers;
    private final ProductService products;

    public OrderService(OrderRepository orders, CustomerService customers,
                        ProductService products) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
    }

    /** One page of the orders table, newest first. */
    @Transactional(readOnly = true)
    public PagedEnvelope<OrderSummaryResponse> list(int page, int size) {
        var window = PageWindow.of(page, size);
        var items = orders.findAll(window.page(), window.size()).stream()
                .map(OrderService::summaryOf)
                .toList();
        return window.envelope(items, orders.count());
    }

    /** The draft with its lines and computed totals, or the 404 envelope. */
    @Transactional(readOnly = true)
    public OrderResponse get(long id) {
        return orders.findById(id)
                .map(OrderService::responseOf)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    /**
     * Creates a DRAFT. The number is the database's job (the repository asks
     * V8's sequence); the total is the aggregate's, never the client's. An
     * empty item list is a legal starting point — a salesperson may open the
     * order and add lines next.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE')")
    @Transactional
    public OrderResponse create(OrderRequest request) {
        var customer = party(request.customerId());
        return responseOf(orders.save(
                Order.draft(customer.id(), customer.name(), lines(request.items()))));
    }

    /**
     * Replaces a DRAFT's customer and lines. The aggregate refuses a
     * non-draft row as a 409 before anything is written, so the refusal is
     * the state machine's (ADR 0017), not a service check that a later
     * caller could forget.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE')")
    @Transactional
    public OrderResponse edit(long id, OrderRequest request) {
        var existing = orders.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        var customer = party(request.customerId());
        return responseOf(orders.save(
                existing.withDraft(customer.id(), customer.name(), lines(request.items()))));
    }

    /**
     * A customer may sit on a new document only while active (business-rules
     * §5). The check is here rather than in the database because a draft can
     * outlive a deactivation — the row stays legal, but a new save may not
     * put the retired party on it.
     */
    private CustomerRef party(long customerId) {
        var customer = customers.findRef(customerId);
        if (!customer.active()) {
            throw new InvalidOrderException("Customer is deactivated.");
        }
        return customer;
    }

    /**
     * The request's lines, priced from the products. The unit price is copied
     * on every save while the order is a DRAFT (GLOSSARY "Price" locks it at
     * CONFIRM), and one product may appear only once — two lines for the same
     * product would make every later stock check ambiguous.
     */
    private List<OrderItem> lines(List<OrderItemRequest> requested) {
        var seen = new HashSet<Long>();
        return requested.stream().map(line -> {
            if (!seen.add(line.productId())) {
                throw new InvalidOrderException(
                        "Product " + line.productId() + " appears more than once on the order.");
            }
            ProductRef product = products.findRef(line.productId());
            if (!product.active()) {
                throw new InvalidOrderException(
                        "Product " + product.sku() + " is deactivated.");
            }
            return OrderItem.of(product.id(), product.sku(), product.name(),
                    line.quantity(), product.price());
        }).toList();
    }

    /** The detail mapping, in one place. */
    static OrderResponse responseOf(Order order) {
        return new OrderResponse(
                order.id(), order.number().value(), order.customerId(), order.customerName(),
                order.status(),
                order.items().stream().map(item -> new OrderItemResponse(
                        item.id(), item.productId(), item.sku(), item.productName(),
                        item.quantity(), item.unitPrice(), item.subtotal())).toList(),
                order.total(), order.createdAt());
    }

    /** The list mapping, in one place. */
    static OrderSummaryResponse summaryOf(OrderSummary order) {
        return new OrderSummaryResponse(
                order.id(), order.number().value(), order.customerId(), order.customerName(),
                order.status(), order.total(), order.createdAt());
    }
}
