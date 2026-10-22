package com.example.bms.customer;

import com.example.bms.shared.PageWindow;
import com.example.bms.shared.PagedEnvelope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer use cases (architecture §2: the application service is
 * transaction + load + authorize + save).
 *
 * <p>The permission split (agreed with the user) follows business-rules §4,
 * which is *not* the supplier split: reads are open to any signed-in role,
 * writes are ADMIN/MANAGER <em>and EMPLOYEE</em> — the clerk opens a sale
 * for a walk-in and needs to be able to register the party. Only the
 * deactivation is ADMIN/MANAGER.
 */
@Service
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    /**
     * Deliberately no {@code @PreAuthorize}: this is the read side of the
     * split above. The URL layer still requires a signed-in caller.
     *
     * @param search the term the URL carried — handed to the boundary
     *               untouched (a blank term is no filter, and the boundary
     *               owns how matching works).
     */
    @Transactional(readOnly = true)
    public PagedEnvelope<CustomerResponse> list(int page, int size, String search) {
        var window = PageWindow.of(page, size);
        var items = customers.findAll(window.page(), window.size(), search).stream()
                .map(CustomerService::responseOf)
                .toList();
        var total = customers.count(search);
        return window.envelope(items, total);
    }

    /** Entity → API response: the mapping lives in exactly one place. */
    static CustomerResponse responseOf(Customer customer) {
        return new CustomerResponse(
                customer.id(), customer.name(), customer.document(),
                customer.email(), customer.paymentTermsDays(), customer.active());
    }

    /**
     * Create: business-rules §4 row "Create/edit customer" — ADMIN, MANAGER
     * <em>and EMPLOYEE</em> write (the clerk registers the walk-in party at
     * the counter). The three roles are listed rather than expressed as a
     * bare "authenticated": the matrix is the rule, so a future fourth role
     * is gated until the matrix — and this line — say otherwise.
     *
     * <p>A customer is born usable ({@code active} true) with the database's
     * terms default — {@code paymentTermsDays} is optional in the body, and 30
     * (database-design {@code DEFAULT 30}, business-rules §3) is what an empty
     * box means. The response echoes what was stored, so the UI renders the
     * API's answer rather than re-deriving it.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE')")
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        var stored = customers.save(new Customer(
                0,
                request.name(),
                request.document(),
                request.email(),
                request.paymentTermsDays() == null ? 30 : request.paymentTermsDays(),
                true));
        return responseOf(stored);
    }
}
