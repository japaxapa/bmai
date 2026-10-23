package com.example.bms.customer;

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
 * Seam A (agreed with the user): the customers HTTP surface, observed through
 * MockMvc exactly as curl would see it — status, content type and body shape
 * only. The database boundary is stubbed here; the real unique {@code
 * document} index and the real filter chain are exercised end to end by
 * {@link CustomerEndToEndTest}.
 *
 * <p>ticket #7's mirror with one deliberate difference: business-rules §4
 * lets EMPLOYEE create and edit customers (the clerk opens a sale for a
 * walk-in) — only the *deactivation* is ADMIN/MANAGER. The role cells below
 * pin that difference rather than the supplier split.
 */
@WebMvcTest(controllers = CustomerController.class)
@Import({SecurityConfig.class, CustomerService.class})
class CustomerEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * The customer database boundary, stubbed the way the supplier slice
     * stubs its own: an in-memory store of two known rows (row 7 is also the
     * one the later edit and status tests load). The stub filters
     * case-insensitively on name + document the way the SQL will — the *real*
     * ILIKE and the real unique index are seam B's job
     * ({@link CustomerEndToEndTest}).
     *
     * <p>Inserts deliberately do not enter the store: they would move
     * {@code total} under the tests that count rows, and the count would then
     * depend on test order rather than on the search.
     */
    @TestConfiguration
    static class StubbedCustomers {
        @Bean
        CustomerRepository customerRepository() {
            var rows = new java.util.concurrent.ConcurrentHashMap<Long, Customer>();
            rows.put(7L, new Customer(7, "Mercado Rio Doce ME", "11222333000181",
                    "contato@riodoce.com.br", 30, true));
            rows.put(8L, new Customer(8, "Padaria São Jorge Ltda", "12345678909",
                    null, 45, true));
            return new CustomerRepository() {
                @Override
                public java.util.List<Customer> findAll(int page, int size, String search) {
                    return matching(search).stream()
                            .skip((long) page * size)
                            .limit(size)
                            .toList();
                }

                @Override
                public long count(String search) {
                    return matching(search).size();
                }

                @Override
                public java.util.Optional<Customer> findById(long id) {
                    return java.util.Optional.ofNullable(rows.get(id));
                }

                @Override
                public Customer save(Customer customer) {
                    if (customer.id() == 0) {
                        return customer.withId(ids.getAndIncrement());
                    }
                    rows.put(customer.id(), customer);
                    return customer;
                }

                private static final java.util.concurrent.atomic.AtomicLong ids =
                        new java.util.concurrent.atomic.AtomicLong(1);

                private java.util.List<Customer> matching(String search) {
                    if (search == null || search.isBlank()) {
                        return rows.values().stream().sorted(ordered).toList();
                    }
                    var needle = search.toLowerCase();
                    return rows.values().stream()
                            .filter(customer -> customer.name().toLowerCase().contains(needle)
                                    || customer.document().contains(needle))
                            .sorted(ordered)
                            .toList();
                }
            };
        }

        private static final java.util.Comparator<Customer> ordered =
                java.util.Comparator.comparing(Customer::name);
    }

    /**
     * The same rule at the other edge: a body can satisfy {@code @NotBlank}
     * with a value that carries no digits at all ("abc"), and the canonical
     * form collapses it to the empty string. The length rule must still bite —
     * otherwise a party with no document walks in through the back door. (A
     * pinning test: A4's guard already answers this shape.)
     */
    @Test
    void documentWithNoDigitsAtAllIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
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
     * digits that do not add up is a typo, not a party. The algorithm is the
     * shared {@code CheckDigits} (ADR 0017's arithmetic, one copy for both
     * parties) — pure code, no database counterpart, and the canonical form
     * has already stripped the punctuation, so the digits being judged are
     * the digits the person typed.
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
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
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
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tamanho errado\",\"document\":\"12345\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail")
                        .value("Document must be 11 digits (CPF) or 14 digits (CNPJ)."));
    }

    /**
     * The domain's own refusal, in Product's exact shape ("Minimum stock
     * cannot be negative."): payment terms are a business range (≥ 0 days),
     * not DTO shape, so the entity judges (ADR 0017) and the advice layer
     * answers 422 with the sentence the UI renders inline. Without the guard
     * a negative value answers V7's CHECK — an unhandled 500 with no sentence
     * for any channel to render.
     */
    @Test
    void negativePaymentTermsAreRefusedAs422() throws Exception {
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Prazo impossível ME","document":"11.222.333/0001-81",
                                 "paymentTermsDays":-5}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Payment terms cannot be negative."));
    }

    /**
     * api-design: customers carry {@code ?search=} (search/filter/
     * pagination). This slice pins the wiring (URL → service → boundary,
     * term intact); the real case-insensitive match against Postgres is seam
     * B's job. The stub filters on name and document the way the SQL will —
     * party search matches the two columns a person would type from: the
     * trading name and the CPF/CNPJ digits.
     */
    @Test
    void searchNarrowsTheListToTheMatchingRows() throws Exception {
        mvc.perform(get("/api/customers?search=mercado").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                // Row 7 by id — a sibling test renames it in this shared
                // store, so the *name* can only be pinned by what survives
                // the rename.
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].name", org.hamcrest.Matchers.containsString("Mercado")))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    /**
     * The second column the predicate covers: a person searching a customer
     * types the CPF/CNPJ digits as readily as the trading name, and the
     * canonical form means the digits on the wire are the digits stored.
     */
    @Test
    void searchAlsoMatchesTheDocumentDigits() throws Exception {
        mvc.perform(get("/api/customers?search=12345678909")
                        .with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                // The document, not the name: a sibling test renames row 7 in
                // this shared store — so this assertion holds in any order.
                .andExpect(jsonPath("$.items[0].document").value("12345678909"))
                .andExpect(jsonPath("$.total").value(1));
    }

    /** A blank term is no filter — the list is the whole list. */
    @Test
    void blankSearchMeansNoFilter() throws Exception {
        mvc.perform(get("/api/customers?search=").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }

    /**
     * api-design errors table: "404 | unknown id". A stale row (edited in
     * another tab) must not become a 500 or an empty 404 — the UI toasts the
     * same envelope it gets everywhere else. (A pinning test: the edit path
     * already answers the unknown id this way.)
     */
    @Test
    void unknownCustomerIsA404ProblemJson() throws Exception {
        mvc.perform(put("/api/customers/999").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ghost","document":"11.222.333/0001-81"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * api-design: the deactivation convention every collection follows —
     * {@code PUT /{resource}/{id}/status} with {@code {active}}, answered 200
     * with the updated row (same shape as edit, so the UI has one path).
     * Deactivation, never deletion (GLOSSARY "Deactivated"): sales orders
     * reference customers, so a retired row stays, marked.
     */
    @Test
    void statusEndpointTurnsACustomerOffAndAnswersWithIt() throws Exception {
        // Row 8, not row 7: the store is shared across this class, and row 7
        // is the one the edit test asserts `active` true on — flipping it
        // here would make that test depend on the method order.
        mvc.perform(put("/api/customers/8/status").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(8))
                // The row's other fields ride through untouched — only the
                // status verb may change the status.
                .andExpect(jsonPath("$.document").value("12345678909"))
                .andExpect(jsonPath("$.active").value(false));
    }

    /**
     * The customers-specific cell of business-rules §4: unlike create/edit
     * (which EMPLOYEE carries), the deactivation is ADMIN/MANAGER only. The
     * refusal carries a body: the frontend renders 403s as a toast
     * (frontend-architecture §Forms) and a toast with no sentence has nothing
     * to say.
     */
    @Test
    void employeeCannotChangeACustomerStatus() throws Exception {
        mvc.perform(put("/api/customers/7/status").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * The other half of the matrix: MANAGER genuinely carries the write — a
     * bare {@code hasAnyRole('ADMIN','EMPLOYEE')} would fail here, and the
     * create gate is the one cell nothing else pins.
     */
    @Test
    void managerMayCreateACustomer() throws Exception {
        mvc.perform(post("/api/customers").with(user("supervisor").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Torrefação Sul ME","document":"11.222.333/0001-81"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Torrefação Sul ME"));
    }

    /**
     * Edit posts the whole shape (the same DTO create takes — one record
     * holds the field rules, so create cannot drift from edit), answers 200
     * with the stored row, and the fields no form writes ride along
     * untouched: {@code active} (status is its own verb) and {@code id}.
     */
    @Test
    void adminEditAnswersWithTheUpdatedCustomer() throws Exception {
        mvc.perform(put("/api/customers/7").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mercado Rio Doce e São Jorge ME","document":"11.222.333/0001-81",
                                 "email":"novas@riodoce.com.br","paymentTermsDays":45}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Mercado Rio Doce e São Jorge ME"))
                .andExpect(jsonPath("$.document").value("11222333000181"))
                .andExpect(jsonPath("$.email").value("novas@riodoce.com.br"))
                .andExpect(jsonPath("$.paymentTermsDays").value(45))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * The shape rule the category and supplier fields already carry, byte for
     * byte ({@code @Size}): name tops out at 120, V7's
     * {@code customers_name_length} CHECK included. Answered 400 *naming the
     * field* — the refusal router (lib/api/refusal.ts) prints fieldErrors on
     * the field itself, so the form shows the sentence under the Name box
     * rather than as a generic banner. Without the annotation the 121st
     * character sails past every DTO rule and Postgres answers first — as an
     * unhandled 500, no field named and no sentence to show.
     */
    @Test
    void overlongNameIsRefusedAsA400NamingTheField() throws Exception {
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(121)
                                + "\",\"document\":\"11.222.333/0001-81\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name[0]")
                        .value("Name must be at most 120 characters."));
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
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
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
     * The happy path the form will call. 201 (not 200) so a caller can tell a
     * created customer from an edited one, and three born values come back
     * rather than being assumed by the caller: a customer is born usable
     * ({@code active} true — the UI renders what the API answers), payment
     * terms default to 30 (database-design's {@code DEFAULT 30}, business-rules
     * §3), and the document arrives in canonical form (GLOSSARY "Canonical
     * form") — the punctuation a person types is presentation, the digits are
     * what the unique index compares.
     *
     * <p>And it is an EMPLOYEE who posts it: business-rules §4 grants the
     * clerk "Create/edit customer" (the walk-in at the counter), unlike every
     * other write in the system.
     */
    @Test
    void employeeCreateAnswers201WithTheStoredCustomer() throws Exception {
        mvc.perform(post("/api/customers").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Mercado Rio Doce ME","document":"11.222.333/0001-81",
                                 "email":"contato@riodoce.com.br"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Mercado Rio Doce ME"))
                .andExpect(jsonPath("$.document").value("11222333000181"))
                .andExpect(jsonPath("$.email").value("contato@riodoce.com.br"))
                .andExpect(jsonPath("$.paymentTermsDays").value(30))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * The read side of business-rules §4: the matrix gates *managing*
     * customers, never *seeing* them (a clerk needs the list to open a sale).
     * The envelope keys are the contract the frontend table binds to.
     */
    @Test
    void anySignedInRoleMayListCustomersInAPagedEnvelope() throws Exception {
        mvc.perform(get("/api/customers").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }
}
