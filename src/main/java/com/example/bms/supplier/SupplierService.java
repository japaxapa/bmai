package com.example.bms.supplier;

import com.example.bms.shared.PageWindow;
import com.example.bms.shared.PagedEnvelope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplier use cases (architecture §2: the application service is
 * transaction + load + authorize + save).
 *
 * <p>The permission split (agreed with the user) follows the categories and
 * products precedent: reads are open to any signed-in role, writes are
 * ADMIN/MANAGER (business-rules §4 row "Create/edit supplier").
 */
@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
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
    public PagedEnvelope<SupplierResponse> list(int page, int size, String search) {
        var window = PageWindow.of(page, size);
        var items = suppliers.findAll(window.page(), window.size(), search).stream()
                .map(SupplierService::responseOf)
                .toList();
        var total = suppliers.count(search);
        return window.envelope(items, total);
    }

    /** Entity → API response: the mapping lives in exactly one place. */
    static SupplierResponse responseOf(Supplier supplier) {
        return new SupplierResponse(
                supplier.id(), supplier.name(), supplier.document(),
                supplier.email(), supplier.paymentTermsDays(), supplier.active());
    }

    /**
     * Create: business-rules §4 row "Create/edit supplier" — ADMIN and MANAGER
     * write, EMPLOYEE does not. The reads stay ungated above; the URL layer
     * requires a signed-in caller either way.
     *
     * <p>A supplier is born usable ({@code active} true) with the database's
     * terms default — {@code paymentTermsDays} is optional in the body, and 30
     * (database-design {@code DEFAULT 30}, business-rules §3) is what an empty
     * box means. The response echoes what was stored, so the UI renders the
     * API's answer rather than re-deriving it.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        var stored = store(new Supplier(
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
     * comes from V6's unique index — Postgres says no — and this is only the
     * translation into the 409 the UI renders inline. Nothing here "checks
     * first", so two identical POSTs answer identically and an edit cannot
     * race past the constraint either. The sentence quotes the canonical
     * form, which is the spelling that actually collided.
     */
    private Supplier store(Supplier supplier) {
        try {
            return suppliers.save(supplier);
        } catch (DuplicateKeyException refused) {
            throw new SupplierConflictException(supplier.document());
        }
    }

    /**
     * Edit: load, refuse the unknown id as a 404 (api-design errors table)
     * rather than letting it fall through to a 500 or an insert, and rebuild
     * the row keeping the fields no form writes — {@code active} (status is
     * its own verb) and the id. The whole shape is required, so an edit
     * cannot silently blank a field the caller simply left out of the body.
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public SupplierResponse edit(long id, SupplierRequest request) {
        var existing = suppliers.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
        var stored = store(new Supplier(
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
     * purchase orders and payables point at suppliers, so retiring one has to
     * leave it in history (GLOSSARY "Deactivated").
     */
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public SupplierResponse changeStatus(long id, boolean active) {
        var existing = suppliers.findById(id)
                .orElseThrow(() -> new SupplierNotFoundException(id));
        var stored = store(existing.withActive(active));
        return responseOf(stored);
    }
}
