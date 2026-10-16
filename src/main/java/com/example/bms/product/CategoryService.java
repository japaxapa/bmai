package com.example.bms.product;

import com.example.bms.shared.PagedEnvelope;
import com.example.bms.shared.PageSize;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category use cases (architecture §2: the application service is
 * transaction + load + authorize + save).
 *
 * <p>The permission split was settled with the user: reads are open to any
 * signed-in role, writes are ADMIN-only. business-rules §4 gates *managing*
 * the vocabulary, not *seeing* it — MANAGER and EMPLOYEE still need the list
 * to fill the category dropdown on the product forms of tickets #6/#7.
 */
@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    /**
     * Deliberately no {@code @PreAuthorize}: this is the read side of the
     * split above. The URL layer still requires a signed-in caller.
     */
    @Transactional(readOnly = true)
    public PagedEnvelope<CategoryResponse> list(int page, int size) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.clamp(size, 1, PageSize.MAX_PAGE_SIZE);
        var items = categories.findAll(safePage, safeSize).stream()
                .map(category -> new CategoryResponse(
                        category.id(), category.name(), category.active()))
                .toList();
        var total = categories.count();
        var totalPages = (int) ((total + safeSize - 1) / safeSize);
        return new PagedEnvelope<>(items, safePage, safeSize, total, totalPages);
    }

    /**
     * The first production {@code @PreAuthorize} in the system (business-rules
     * §4, row "Categories"): ADMIN manages the vocabulary, nobody else. The
     * refusal carries a body — the frontend renders 403s as a toast, and a
     * toast with no sentence has nothing to say.
     *
     * <p>A category is born usable: {@code active} starts true and comes back
     * from the store rather than being assumed by the caller (the UI renders
     * what the API answers).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse create(String name) {
        var stored = store(new Category(0, name, true));
        return responseOf(stored);
    }

    /**
     * Rename: load, refuse the unknown id as a 404 (api-design errors table)
     * rather than letting it fall through to a 500 or an insert.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse edit(long id, String name) {
        var category = categories.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        var stored = store(category.renamedTo(name));
        return responseOf(stored);
    }

    /**
     * The ticket's "change status", as a deactivation rather than a delete:
     * products point at categories with ON DELETE RESTRICT, so retiring a
     * vocabulary row has to leave it in history (GLOSSARY "Deactivated").
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryResponse changeStatus(long id, boolean active) {
        var category = categories.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        var stored = store(category.withActive(active));
        return responseOf(stored);
    }

    /** Entity → API response: the mapping lives in exactly one place. */
    private static CategoryResponse responseOf(Category category) {
        return new CategoryResponse(category.id(), category.name(), category.active());
    }

    /**
     * One write path for create, rename and the status flip.
     *
     * <p>The name refusal comes from V3's unique index — Postgres says no —
     * and this is only the translation into the 409 the UI renders inline.
     * Nothing here "checks first", so two identical POSTs answer identically
     * and a rename cannot race past the constraint either.
     */
    private Category store(Category category) {
        try {
            return categories.save(category);
        } catch (DuplicateKeyException refused) {
            throw new CategoryConflictException(category.name());
        }
    }
}
