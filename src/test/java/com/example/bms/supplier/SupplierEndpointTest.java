package com.example.bms.supplier;

import com.example.bms.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seam A (agreed with the user): the suppliers HTTP surface, observed through
 * MockMvc exactly as curl would see it — status, content type and body shape
 * only. The database boundary is stubbed here; the real unique {@code document}
 * index and the real filter chain are exercised end to end by
 * {@link SupplierEndToEndTest}.
 *
 * <p>testing-strategy §2 "cell delegation": the 409 cell (duplicate document,
 * refused by V6's unique index) is delegated to that E2E — a stubbed
 * repository has no index to refuse with — and 401 lives once repo-wide in
 * {@code AuthEndpointTest}.
 */
@WebMvcTest(controllers = SupplierController.class)
@Import({SecurityConfig.class, SupplierService.class})
class SupplierEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * The supplier database boundary, stubbed the same way the category and
     * product slices stub theirs: an in-memory store of two known rows (row 7
     * is also the one the edit and status tests load). The stub filters
     * case-insensitively on name + document the way the SQL will — the *real*
     * ILIKE and the real unique index are seam B's job
     * ({@link SupplierEndToEndTest}).
     *
     * <p>Inserts deliberately do not enter the store: they would move
     * {@code total} under the tests that count rows, and the count would then
     * depend on test order rather than on the search.
     */
    @TestConfiguration
    static class StubbedSuppliers {
        @Bean
        SupplierRepository supplierRepository() {
            var rows = new java.util.concurrent.ConcurrentHashMap<Long, Supplier>();
            rows.put(7L, new Supplier(7, "Café Bom LTDA", "11222333000181",
                    "contato@cafebom.com.br", 30, true));
            rows.put(8L, new Supplier(8, "Trigo Norte LTDA", "00000000000191",
                    "compras@trigonorte.com.br", 45, true));
            return new SupplierRepository() {
                @Override
                public java.util.List<Supplier> findAll(int page, int size, String search) {
                    return matching(search).stream()
                            .skip((long) page * size)
                            .limit(size)
                            .toList();
                }

                @Override
                public long count(String search) {
                    return matching(search).size();
                }

                private java.util.List<Supplier> matching(String search) {
                    if (search == null || search.isBlank()) {
                        return rows.values().stream().sorted(ordered).toList();
                    }
                    var needle = search.toLowerCase();
                    return rows.values().stream()
                            .filter(supplier -> supplier.name().toLowerCase().contains(needle)
                                    || supplier.document().contains(needle))
                            .sorted(ordered)
                            .toList();
                }

                @Override
                public java.util.Optional<Supplier> findById(long id) {
                    return java.util.Optional.ofNullable(rows.get(id));
                }

                @Override
                public Supplier save(Supplier supplier) {
                    if (supplier.id() == 0) {
                        return supplier.withId(ids.getAndIncrement());
                    }
                    rows.put(supplier.id(), supplier);
                    return supplier;
                }
            };
        }

        private static final java.util.Comparator<Supplier> ordered =
                java.util.Comparator.comparing(Supplier::name);

        private static final java.util.concurrent.atomic.AtomicLong ids =
                new java.util.concurrent.atomic.AtomicLong(1);
    }

    /**
     * The read side of business-rules §4 (agreed with the user): the matrix
     * gates *managing* suppliers (ADMIN/MANAGER), not *seeing* them. The
     * envelope keys are the contract the frontend table binds to.
     */
    @Test
    void anySignedInRoleMayListSuppliersInAPagedEnvelope() throws Exception {
        mvc.perform(get("/api/suppliers").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    /**
     * The happy path the form will call. 201 (not 200) so a caller can tell a
     * created supplier from an edited one, and three born values come back
     * rather than being assumed by the caller: a supplier is born usable
     * ({@code active} true — the UI renders what the API answers), payment
     * terms default to 30 (database-design's {@code DEFAULT 30}, business-rules
     * §3), and the document arrives in canonical form (GLOSSARY "Canonical
     * form") — the punctuation a person types is presentation, the digits are
     * what the unique index compares.
     */
    @Test
    void adminCreateAnswers201WithTheStoredSupplier() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Café Bom LTDA","document":"11.222.333/0001-81",
                                 "email":"contato@cafebom.com.br"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Café Bom LTDA"))
                .andExpect(jsonPath("$.document").value("11222333000181"))
                .andExpect(jsonPath("$.email").value("contato@cafebom.com.br"))
                .andExpect(jsonPath("$.paymentTermsDays").value(30))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * AC: "Constraint violations return 409/422 rendered inline in the UI" —
     * which is only possible if the API says *which field* refused, rather
     * than a bare sentence the form would have to guess about. Whitespace-only
     * is the shape a user actually produces by hitting space in the box, and a
     * missing document is the shape a "required" box the form forgot produces.
     * A party is name + document: neither can be blank.
     */
    @Test
    void blankNameAndDocumentAreRefusedWithFieldErrorsNamingTheFields() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"document\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").isArray())
                .andExpect(jsonPath("$.fieldErrors.name[0]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.document").isArray())
                .andExpect(jsonPath("$.fieldErrors.document[0]").isNotEmpty());
    }

    /**
     * The domain's own refusal (ADR 0017: the entity refuses, the advice
     * layer answers 422) — not DTO shape: a syntactically perfect body can
     * still carry a document that is neither a CPF nor a CNPJ. The canonical
     * form strips the punctuation first, so what is judged is the digit count:
     * 11 = CPF, 14 = CNPJ, anything else is not a document this domain holds.
     *
     * <p>Answered 422 ("business rule refuses", api-design errors table) with
     * the sentence the UI renders inline.
     */
    @Test
    void documentOfTheWrongLengthIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tamanho errado\",\"document\":\"12345\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Document must be 11 digits (CPF) or 14 digits (CNPJ)."));
    }

    /**
     * The same rule at the other edge: a body can satisfy {@code @NotBlank}
     * with a value that carries no digits at all ("abc"), and the canonical
     * form collapses it to the empty string. The length rule must still bite —
     * otherwise a party with no document walks in through the back door.
     */
    @Test
    void documentWithNoDigitsAtAllIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sem dígitos\",\"document\":\"abc\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Document must be 11 digits (CPF) or 14 digits (CNPJ)."));
    }

    /**
     * The checksum half of the same domain rule: CPF and CNPJ both carry
     * modulus-11 verification digits, and a body of the right *length* with
     * digits that do not add up is a typo, not a party. The algorithm lives on
     * the entity (ADR 0017) — pure code, no database counterpart, and the
     * canonical form has already stripped the punctuation, so the digits being
     * judged are the digits the person typed.
     *
     * <p>Both shapes are named because they are the two a person can produce:
     * a CPF whose last digit is off by one, and a CNPJ ditto.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "529.982.247-24",   // a real CPF (…-25) with its last digit wrong
            "11.222.333/0001-82", // a real CNPJ (…-81) with its last digit wrong
    })
    void documentWithBadCheckDigitsIsRefusedAs422(String document) throws Exception {
        mvc.perform(post("/api/suppliers").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dígitos errados\",\"document\":\""
                                + document + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Document check digits do not match."));
    }

    /**
     * business-rules §4, row "Create/edit supplier": ADMIN and MANAGER write,
     * EMPLOYEE does not — and EMPLOYEE is the role a reader would assume is
     * privileged (it can create customers). The refusal carries a body: the
     * frontend renders 403s as a toast (frontend-architecture §Forms) and a
     * toast with no sentence has nothing to say.
     */
    @Test
    void employeeCannotCreateASupplier() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Café Bom LTDA","document":"11.222.333/0001-81"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * The other half of the row: MANAGER is not just "not EMPLOYEE" — it
     * genuinely carries the write, and this test is what keeps the expression
     * honest (a bare {@code hasRole('ADMIN')} would fail here).
     */
    @Test
    void managerMayCreateASupplier() throws Exception {
        mvc.perform(post("/api/suppliers").with(user("supervisor").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Torrefação Sul LTDA","document":"11.222.333/0001-81"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Torrefação Sul LTDA"));
    }

    /**
     * Edit posts the whole shape (the same DTO create takes — one record
     * holds the field rules, so create cannot drift from edit), answers 200
     * with the stored row, and the fields no form writes ride along
     * untouched: {@code active} (status is its own verb) and {@code id}.
     */
    @Test
    void adminEditAnswersWithTheUpdatedSupplier() throws Exception {
        mvc.perform(put("/api/suppliers/7").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Café Bom do Norte LTDA","document":"11.222.333/0001-81",
                                 "email":"novas@cafebom.com.br","paymentTermsDays":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Café Bom do Norte LTDA"))
                .andExpect(jsonPath("$.document").value("11222333000181"))
                .andExpect(jsonPath("$.email").value("novas@cafebom.com.br"))
                .andExpect(jsonPath("$.paymentTermsDays").value(45))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * api-design: the deactivation convention every collection follows —
     * {@code PUT /{resource}/{id}/status} with {@code {active}}, answered 200
     * with the updated row (same shape as edit, so the UI has one path).
     * Deactivation, never deletion (GLOSSARY "Deactivated"): history references
     * suppliers (purchase orders, payables), so a retired row stays, marked.
     */
    @Test
    void statusEndpointTurnsASupplierOffAndAnswersWithIt() throws Exception {
        mvc.perform(put("/api/suppliers/7/status").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                // The row's other fields ride through untouched — only the
                // status verb may change the status.
                .andExpect(jsonPath("$.document").value("11222333000181"))
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * api-design errors table: "404 | unknown id". A stale row (edited in
     * another tab) must not become a 500 or an empty 404 — the UI toasts the
     * same envelope it gets everywhere else.
     */
    @Test
    void unknownSupplierIsA404ProblemJson() throws Exception {
        mvc.perform(put("/api/suppliers/999").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ghost","document":"11.222.333/0001-81"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * The deny half of business-rules §4 for the other two write verbs: the
     * gate is per use case, not per URL shape, so a future
     * {@code @PreAuthorize} that gates create but leaves edit and the status
     * flip open would otherwise walk straight through (testing-strategy §3:
     * every role × action cell).
     */
    @Test
    void employeeCannotEditASupplier() throws Exception {
        mvc.perform(put("/api/suppliers/7").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Not mine to edit","document":"11.222.333/0001-81"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void employeeCannotChangeASupplierStatus() throws Exception {
        mvc.perform(put("/api/suppliers/7/status").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * api-design: suppliers carry {@code ?search=} (search/filter/pagination).
     * This slice pins the wiring (URL → service → boundary, term intact); the
     * real case-insensitive match against Postgres is seam B's job. The stub
     * filters on name and document the way the SQL will — party search matches
     * the two columns a person would type from: the trading name and the
     * CPF/CNPJ digits.
     */
    @Test
    void searchNarrowsTheListToTheMatchingRows() throws Exception {
        mvc.perform(get("/api/suppliers?search=trigo").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Trigo Norte LTDA"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    /**
     * The second column the predicate covers: a person searching a supplier
     * types the CPF/CNPJ digits as readily as the trading name, and the
     * canonical form means the digits on the wire are the digits stored.
     */
    @Test
    void searchAlsoMatchesTheDocumentDigits() throws Exception {
        mvc.perform(get("/api/suppliers?search=11222333000181")
                        .with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                // The document, not the name: a sibling test renames row 7 in
                // this shared store, while the document it posts is the same
                // digits — so this assertion holds in any test order.
                .andExpect(jsonPath("$.items[0].document").value("11222333000181"))
                .andExpect(jsonPath("$.total").value(1));
    }

    /** A blank term is no filter — the list is the whole list. */
    @Test
    void blankSearchMeansNoFilter() throws Exception {
        mvc.perform(get("/api/suppliers?search=").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }
}
