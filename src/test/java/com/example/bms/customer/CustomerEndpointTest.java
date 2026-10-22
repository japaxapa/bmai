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
