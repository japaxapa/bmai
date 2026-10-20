package com.example.bms.product;

import com.example.bms.shared.SecurityConfig;
import com.example.bms.supplier.Supplier;
import com.example.bms.supplier.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seam A (the same pair ticket #5 settled): the products HTTP surface,
 * observed through MockMvc exactly as curl would see it — status, content
 * type and body shape only. The database boundary is stubbed here; the real
 * case-insensitive SKU index and the real filter chain are exercised end to
 * end by {@link ProductEndToEndTest}.
 *
 * <p>testing-strategy §2 "cell delegation": the slice's 409 cell (duplicate
 * SKU, refused by V4's {@code lower(sku)} index) is delegated to that E2E —
 * a stubbed repository has no index to refuse with — and 401 lives once
 * repo-wide in {@code AuthEndpointTest} (the filter chain doesn't vary per
 * endpoint). The cells that do vary per endpoint — 400/422/403 — are all
 * here.
 *
 * <p>{@code @WebMvcTest} names its controller (as {@code AuthEndpointTest}
 * does): the slice then wires only what <em>this</em> surface needs, so a
 * controller a later ticket adds costs this file nothing.
 */
@WebMvcTest(controllers = ProductController.class)
@Import({SecurityConfig.class, CategoryService.class, ProductService.class})
class ProductEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * The categories boundary, needed by the service itself: a product's
     * category name is resolved here, and the known row (id 7) is the one
     * the create test links to.
     */
    @TestConfiguration
    static class StubbedCategories {
        @Bean
        CategoryRepository categoryRepository() {
            return new CategoryRepository() {
                @Override
                public List<Category> findAll(int page, int size) {
                    return List.of(new Category(7, "Padaria", true));
                }

                @Override
                public long count() {
                    return 1;
                }

                @Override
                public Optional<Category> findById(long id) {
                    return Optional.ofNullable(id == 7 ? new Category(7, "Padaria", true) : null);
                }

                @Override
                public Category save(Category category) {
                    return category;
                }
            };
        }
    }

    /**
     * The product database boundary, stubbed in memory: two known rows
     * carrying every field the response shape must not lose (row id 7 is
     * also the one the edit and status tests load). The stub filters
     * case-insensitively on sku + name the way the SQL will — the *real*
     * ILIKE and the real unique index are seam B's job.
     */
    @TestConfiguration
    static class StubbedProducts {
        @Bean
        ProductRepository productRepository() {
            var cafe = new Product(
                    7, "CAFE-1", "Café 500g", "Torra média",
                    new BigDecimal("24.90"), new BigDecimal("15.00"),
                    10, 42, 3, 7L, true);
            var trigo = new Product(
                    8, "TRIGO-2", "Trigo 1kg", null,
                    new BigDecimal("5.50"), new BigDecimal("3.20"),
                    0, 120, 3, null, true);
            List<ProductWithCategory> rows = List.of(
                    new ProductWithCategory(cafe, "Padaria", "Café Bom LTDA"),
                    new ProductWithCategory(trigo, "Padaria", null));
            return new ProductRepository() {
                @Override
                public List<ProductWithCategory> findAll(int page, int size, String search) {
                    return matching(search).stream()
                            .skip((long) page * size)
                            .limit(size)
                            .toList();
                }

                @Override
                public long count(String search) {
                    return matching(search).size();
                }

                private List<ProductWithCategory> matching(String search) {
                    if (search == null || search.isBlank()) {
                        return rows;
                    }
                    var needle = search.toLowerCase();
                    return rows.stream()
                            .filter(row -> row.product().sku().toLowerCase().contains(needle)
                                    || row.product().name().toLowerCase().contains(needle))
                            .toList();
                }

                @Override
                public Optional<ProductWithCategory> findById(long id) {
                    return rows.stream().filter(row -> row.product().id() == id).findFirst();
                }

                @Override
                public Product save(Product product) {
                    return product;
                }
            };
        }
    }

    /**
     * The suppliers boundary, wired because the product create now resolves
     * a supplier name. The known row (id 7) is the one the supplier tests
     * link to; the repository is never asked for a list by this slice.
     */
    @TestConfiguration
    static class StubbedSuppliers {
        @Bean
        SupplierRepository supplierRepository() {
            return new SupplierRepository() {
                @Override
                public List<Supplier> findAll(int page, int size, String search) {
                    return List.of(new Supplier(7, "Café Bom LTDA", "11222333000181",
                            "contato@cafebom.com.br", 30, true));
                }

                @Override
                public long count(String search) {
                    return 1;
                }

                @Override
                public Optional<Supplier> findById(long id) {
                    return java.util.Optional.ofNullable(id == 7
                            ? new Supplier(7, "Café Bom LTDA", "11222333000181",
                                    "contato@cafebom.com.br", 30, true)
                            : null);
                }

                @Override
                public Supplier save(Supplier supplier) {
                    return supplier.id() == 0 ? supplier.withId(9) : supplier;
                }
            };
        }
    }

    /**
     * The read side of business-rules §4: the matrix gates *managing*
     * products (ADMIN/MANAGER), not *seeing* them — an EMPLOYEE needs the
     * list to pick a product on an order form. The envelope keys are the
     * contract the frontend table binds to.
     */
    @Test
    void anySignedInRoleMayListProductsInAPagedEnvelope() throws Exception {
        mvc.perform(get("/api/products").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    /**
     * The happy path the form will call. 201 (not 200) so a caller can tell
     * a created product from an edited one; the response carries the whole
     * stored row — including the category link as id + resolved name — and
     * {@code active} comes back true (a product is born usable), with the
     * stock mirror at zero (movements are what will move it, ticket #12).
     */
    @Test
    void adminCreateAnswers201WithTheStoredProduct() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"CAFE-1","name":"Café 500g","description":"Torra média",
                                 "price":24.90,"cost":15.00,"minimumStock":10,"categoryId":7}
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                // The API echoes the canonical form (GLOSSARY "Canonical
                // form"): what was posted was canonicalized, not stored raw.
                .andExpect(jsonPath("$.sku").value("cafe-1"))
                .andExpect(jsonPath("$.name").value("Café 500g"))
                .andExpect(jsonPath("$.description").value("Torra média"))
                .andExpect(jsonPath("$.price").value(24.90))
                .andExpect(jsonPath("$.cost").value(15.00))
                .andExpect(jsonPath("$.minimumStock").value(10))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.categoryId").value(7))
                .andExpect(jsonPath("$.categoryName").value("Padaria"))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * AC: "Field-level validation errors shown in the UI" — only possible if
     * the answer says *which field* refused. Whitespace-only is the shape a
     * user actually produces by hitting space in the box; a missing price is
     * the shape a "required" box the form forgot produces. The category link
     * is required (requirements: "Category: required on products").
     */
    @Test
    void blankSkuAndNameAreRefusedWithFieldErrorsNamingTheFields() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"   ","name":"  ","price":1.00,"categoryId":7}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.sku").isArray())
                .andExpect(jsonPath("$.fieldErrors.sku[0]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.name").isArray())
                .andExpect(jsonPath("$.fieldErrors.name[0]").isNotEmpty());
    }

    @Test
    void missingPriceAndCategoryAreRefusedWithFieldErrorsNamingTheFields() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"CAFE-1","name":"Café 500g"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.price").isArray())
                .andExpect(jsonPath("$.fieldErrors.price[0]").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.categoryId").isArray())
                .andExpect(jsonPath("$.fieldErrors.categoryId[0]").isNotEmpty());
    }

    /**
     * The business ranges — price > 0, cost ≥ 0, minimum stock ≥ 0 — are the
     * *domain's* refusals, not DTO shape: a syntactically perfect body can
     * still be a bad product. They answer 422 ("business rule refuses",
     * api-design errors table) with the sentence naming the offending field,
     * and they live on the entity (ADR 0017), so no caller can bypass them
     * by skipping a service. Zero price is refused because "price > 0" — a
     * free product is a gift, not a sale.
     */
    @Test
    void negativePriceIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-1\",\"name\":\"Negativo\",\"price\":-1,\"categoryId\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Price must be greater than zero."));
    }

    @Test
    void zeroPriceIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-2\",\"name\":\"Grátis\",\"price\":0,\"categoryId\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Price must be greater than zero."));
    }

    @Test
    void negativeCostIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-3\",\"name\":\"Custo negativo\",\"price\":1,\"cost\":-0.01,\"categoryId\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Cost cannot be negative."));
    }

    @Test
    void negativeMinimumStockIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-4\",\"name\":\"Mínimo negativo\",\"price\":1,\"minimumStock\":-1,\"categoryId\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Minimum stock cannot be negative."));
    }

    /**
     * Money is {@code numeric(12,2)} (database-design): a value finer than
     * cents is not a price the domain can hold. Left unchecked the database
     * would *round* it — 0.001 → 0.00 — and the {@code price > 0} CHECK
     * would fire as an unhandled {@code DataIntegrityViolationException}:
     * a 500, not the 422 this API owes. The entity refuses first.
     */
    @Test
    void subCentPriceIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-5\",\"name\":\"Fracionado\",\"price\":0.001,\"categoryId\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Price can have at most two decimal places."));
    }

    /**
     * The quieter half of the same rule: cost 9.999 rounds to 10.00, which
     * the {@code cost >= 0} CHECK happily accepts — money silently changes
     * value on the way in. The same domain refusal keeps the digits honest.
     */
    @Test
    void subCentCostIsRefusedAs422() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-6\",\"name\":\"Custo fracionado\",\"price\":10,\"cost\":9.999,\"categoryId\":7}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value("Cost can have at most two decimal places."));
    }

    /**
     * business-rules §4, row "Create/edit/deactivate product": ADMIN and
     * MANAGER write; EMPLOYEE is the one role a reader would assume is
     * privileged (it can create customers) but may not create products.
     * The refusal carries a body — the frontend renders 403s as a toast,
     * and a toast with no sentence has nothing to say.
     */
    @Test
    void employeeCannotCreateAProduct() throws Exception {
        mvc.perform(post("/api/products").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"A-1\",\"name\":\"Café\",\"price\":1,\"categoryId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * The other half of the row: MANAGER is not just "not EMPLOYEE" — it
     * genuinely carries the write, and this test is what keeps the
     * expression honest (a bare {@code hasRole('ADMIN')} would fail here).
     */
    @Test
    void managerMayCreateAProduct() throws Exception {
        mvc.perform(post("/api/products").with(user("supervisor").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"M-1\",\"name\":\"Café\",\"price\":1,\"categoryId\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("m-1"));
    }

    /**
     * Edit posts the whole shape (the same DTO create takes — one record
     * holds the field rules), answers 200 with the stored row, and the stock
     * mirror rides along untouched: a form never writes it (ADR 0006).
     */
    @Test
    void adminEditAnswersWithTheUpdatedProduct() throws Exception {
        mvc.perform(put("/api/products/7").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"CAFE-1","name":"Café 500g torra escura",
                                 "description":"Nova torra","price":27.90,"cost":15.00,
                                 "minimumStock":10,"categoryId":7}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Café 500g torra escura"))
                .andExpect(jsonPath("$.price").value(27.90))
                .andExpect(jsonPath("$.stock").value(42))
                .andExpect(jsonPath("$.categoryName").value("Padaria"));
    }

    /**
     * The ticket's "change status", as a deactivation rather than a delete
     * (GLOSSARY "Deactivated"): a product stays in order history, marked.
     */
    @Test
    void statusEndpointTurnsAProductOffAndAnswersWithIt() throws Exception {
        mvc.perform(put("/api/products/7/status").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.active").value(false));
    }

    /** Same matrix as create — the gate is per use case, not per URL shape. */
    @Test
    void employeeCannotEditAProduct() throws Exception {
        mvc.perform(put("/api/products/7").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"CAFE-1\",\"name\":\"Café\",\"price\":1,\"categoryId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * The third write verb of the same matrix row — "Create/edit/deactivate
     * product" (business-rules §4) — so the deny half is pinned for all
     * three: a future {@code @PreAuthorize} that gates create and edit but
     * leaves the status flip open would otherwise walk straight through
     * (testing-strategy §3: every role × action cell).
     */
    @Test
    void employeeCannotChangeProductStatus() throws Exception {
        mvc.perform(put("/api/products/7/status").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * api-design errors table: "404 | unknown id". A stale row (deleted in
     * another tab) must not become a 500 or an empty 404 — the UI toasts
     * the same envelope it gets everywhere else.
     */
    @Test
    void unknownProductIsA404ProblemJson() throws Exception {
        mvc.perform(put("/api/products/999").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"GHOST\",\"name\":\"Ghost\",\"price\":1,\"categoryId\":7}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    /** The category link is a real reference: an unknown id is a 404, not a foreign-key 500. */
    @Test
    void unknownCategoryOnCreateIsA404ProblemJson() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"X-1\",\"name\":\"Órfão\",\"price\":1,\"categoryId\":999}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * Ticket 7's other half: a product names its supplier, and the link
     * travels back the same way the category one does — id for the edit
     * form, name for the row — so neither the UI nor the API needs a second
     * request to join them.
     */
    @Test
    void adminCreateAcceptsASupplierAndAnswersItAsIdAndName() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"CAFE-SUP","name":"Café do fornecedor","price":24.90,
                                 "categoryId":7,"supplierId":7}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierId").value(7))
                .andExpect(jsonPath("$.supplierName").value("Café Bom LTDA"));
    }

    /**
     * The link is optional (V6: nullable column) — a product may be listed
     * before anyone has decided who supplies it. "No supplier" is expressed
     * as an absent id, not as a sentinel number the UI would have to know
     * to filter out; the name is null for the same reason.
     */
    @Test
    void aProductWithNoSupplierAnswersAbsentFields() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"CAFE-SEM","name":"Sem fornecedor ainda","price":24.90,
                                 "categoryId":7}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierId").doesNotExist())
                .andExpect(jsonPath("$.supplierName").doesNotExist());
    }

    /**
     * api-design errors table: "404 | unknown id". The supplier link is a
     * real reference, so an unknown id is the same 404 the category gets —
     * not a foreign-key 500 leaking the SQL to the caller.
     */
    @Test
    void unknownSupplierOnCreateIsA404ProblemJson() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"X-2","name":"Fornecedor órfão","price":1,
                                 "categoryId":7,"supplierId":999}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    /**
     * api-design: products carry {@code ?search=} — an ILIKE over name and
     * sku. This slice pins the wiring (URL → service → boundary, term
     * intact); the real case-insensitive match against Postgres is seam B's.
     * The term arrives lowercased-insensitive here because the boundary is
     * asked with exactly what the URL held.
     */
    @Test
    void searchNarrowsTheListToTheMatchingRows() throws Exception {
        mvc.perform(get("/api/products?search=cafe").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                // The stubbed row is built through the entity like any other,
                // so it carries its canonical (lowercase) SKU.
                .andExpect(jsonPath("$.items[0].sku").value("cafe-1"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    /** A blank term is no filter — the list is the whole list. */
    @Test
    void blankSearchMeansNoFilter() throws Exception {
        mvc.perform(get("/api/products?search=").with(user("clerk").roles("EMPLOYEE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2));
    }
}
