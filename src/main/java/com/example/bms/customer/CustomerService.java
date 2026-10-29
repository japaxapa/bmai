package com.example.bms.customer;

import com.example.bms.shared.PageWindow;
import com.example.bms.shared.PagedEnvelope;
import org.springframework.dao.DuplicateKeyException;
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
     * The customer slice another feature needs to put the party on a
     * document — the order draft reads who it is for and whether it may still
     * be sold to (business-rules §5). A narrow read like
     * {@code SupplierService.findName}: the caller reaches this service, never
     * the repository or the entity (architecture §1), and an unknown id is
     * this feature's own 404.
     */
    @Transactional(readOnly = true)
    public CustomerRef findRef(long id) {
        var customer = customers.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        return new CustomerRef(customer.id(), customer.name(), customer.active());
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
        var stored = store(new Customer(
                0,
                request.name(),
                request.document(),
                request.email(),
                request.paymentTermsDays() == null ? 30 : request.paymentTermsDays(),
                true));
        return responseOf(stored);
    }

    /**
     * One write path — create, edit and the status flip. The document refusal
     * comes from V7's unique index — Postgres says no — and this is only the
     * translation into the 409 the UI renders inline. Nothing here "checks
     * first", so two identical POSTs answer identically and an edit cannot
     * race past the constraint either. The sentence quotes the canonical
     * form, which is the spelling that actually collided.
     */
    private Customer store(Customer customer) {
        try {
            return customers.save(customer);
        } catch (DuplicateKeyException refused) {
            throw new CustomerConflictException(customer.document());
        }
    }

    /**
     * Edit: load, refuse the unknown id as a 404 (api-design errors table)
     * rather than letting it fall through to a 500 or an insert, and rebuild
     * the row keeping the fields no form writes — {@code active} (status is
     * its own verb) and the id. The whole shape is required, so an edit
     * cannot silently blank a field the caller simply left out of the body.
     *
     * <p>Same gate as create (business-rules §4 row "Create/edit customer"):
     * EMPLOYEE carries the edit too.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE')")
    @Transactional
    public CustomerResponse edit(long id, CustomerRequest request) {
        var existing = customers.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        var stored = store(new Customer(
                existing.id(),
                request.name(),
                request.document(),
                request.email(),
                request.paymentTermsDays() == null ? existing.paymentTermsDays()
                        : request.paymentTermsDays(),
                existing.active()));
        return responseOf(stored);
    }

    /**
     * The ticket's "change status", as a deactivation rather than a delete:
     * sales orders and invoices point at customers, so retiring one has to
     * leave it in history (GLOSSARY "Deactivated").
     *
     * <p>And the one write EMPLOYEE does <em>not</em> carry
     * (business-rules §4: "Deactivate customer" is ADMIN/MANAGER): the clerk
     * registers and edits the walk-in party, but retiring a customer is a
     * supervisor's call.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public CustomerResponse changeStatus(long id, boolean active) {
        var existing = customers.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        var stored = store(existing.withActive(active));
        return responseOf(stored);
    }
}
