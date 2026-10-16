package com.example.bms.product;

import com.example.bms.auth.AuthService;
import com.example.bms.auth.UserRepository;
import com.example.bms.shared.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seam A (agreed with the user): the categories HTTP surface, observed through
 * MockMvc exactly as curl would see it — status, content type and body shape
 * only. The database boundary is stubbed here; the real unique index and the
 * real filter chain are exercised end to end by {@link CategoryEndToEndTest}.
 *
 * <p>testing-strategy §2 "cell delegation": this slice's 409 cell (duplicate
 * name, refused by V3's {@code lower(name)} index) is delegated to that E2E —
 * a stubbed repository has no index to refuse with — and 401 lives once
 * repo-wide in {@code AuthEndpointTest}. The per-endpoint cells (400/422/403)
 * are all here.
 *
 * <p>The decisions this file pins down, each settled with the user before a
 * line of it was written:
 * <ul>
 *   <li><b>status</b> means the {@code active} flag, so migration V3 adds the
 *       column the ticket's "change status" AC needs and the endpoint is
 *       {@code PUT /{id}/status} with {@code {active}} — the deactivation
 *       convention api-design.md already gives customers, products, suppliers
 *       and users.</li>
 *   <li><b>reads are open to any signed-in role, writes are ADMIN-only.</b>
 *       business-rules §4 protects who controls the vocabulary, not who sees
 *       it; MANAGER and EMPLOYEE still need the list to fill the category
 *       dropdown on the product forms of tickets #6/#7.</li>
 *   <li><b>collections answer in the paged envelope</b>
 *       ({@code items, page, size, total, totalPages}), as every other
 *       collection does.</li>
 *   <li><b>{@code fieldErrors}</b> is an RFC 9457 body whose extension maps a
 *       field name to its messages: {@code {"fieldErrors": {"name": ["…"]}}}.</li>
 * </ul>
 *
 * <p>What is deliberately <em>not</em> here: the duplicate-name 409. With the
 * repository stubbed there is no unique constraint to collide with, so the
 * slice could only re-state the service's own check — a test that passes by
 * construction. Seam B makes Postgres say no.
 */
@WebMvcTest
@Import({SecurityConfig.class, AuthService.class, CategoryService.class, ProductService.class})
class CategoryEndpointTest {

    @Autowired
    private MockMvc mvc;

    /**
     * {@code @WebMvcTest} scans controllers only — but it scans <em>all</em> of
     * them, so AuthController arrives with its service, and the service needs
     * the database boundary. Stubbed exactly as AuthEndpointTest stubs it;
     * login is not under test here (it has its own seam), and nobody can pass
     * through this stub, since it answers "unknown user" to everyone.
     */
    @TestConfiguration
    static class StubbedUsers {
        @Bean
        UserRepository userRepository() {
            return email -> Optional.empty();
        }
    }

    /**
     * The category database boundary, stubbed the same way: an in-memory
     * store holding one known row (id 7) — the row the edit and status tests
     * load, and what the 404 test's 999 is not. The real unique index and the
     * real SQL are seam B's job ({@link CategoryEndToEndTest}).
     */
    @TestConfiguration
    static class StubbedCategories {
        @Bean
        CategoryRepository categoryRepository() {
            var rows = new ConcurrentHashMap<Long, Category>();
            rows.put(7L, new Category(7, "Padaria", true));
            return new CategoryRepository() {
                @Override
                public List<Category> findAll(int page, int size) {
                    return rows.values().stream()
                            .sorted(Comparator.comparing(category -> category.name().toLowerCase()))
                            .skip((long) page * size)
                            .limit(size)
                            .toList();
                }

                @Override
                public long count() {
                    return rows.size();
                }

                @Override
                public Optional<Category> findById(long id) {
                    return Optional.ofNullable(rows.get(id));
                }

                @Override
                public Category save(Category category) {
                    var id = category.id() == 0 ? ids.getAndIncrement() : category.id();
                    var stored = new Category(id, category.name(), category.active());
                    rows.put(id, stored);
                    return stored;
                }
            };
        }

        private static final AtomicLong ids = new AtomicLong(1);
    }

    /**
     * The products boundary arrives with its own ticket (#6) — this slice
     * only needs the bean to exist, because the all-controllers scan now
     * instantiates {@code ProductController} too. No behaviour is faked
     * here; nothing in this file ever calls it.
     */
    @TestConfiguration
    static class StubbedProducts {
        @Bean
        ProductRepository productRepository() {
            return new ProductRepository() {
                @Override
                public List<ProductWithCategory> findAll(int page, int size, String search) {
                    return List.of();
                }

                @Override
                public long count(String search) {
                    return 0;
                }

                @Override
                public Optional<ProductWithCategory> findById(long id) {
                    return Optional.empty();
                }

                @Override
                public Product save(Product product) {
                    return product;
                }
            };
        }
    }

    /**
     * The agreed read side: the permission matrix gates *managing* categories
     * (ADMIN), not *seeing* them — otherwise the product form's dropdown is
     * empty for everyone but the owner. The envelope keys are asserted even
     * though the table starts empty, because the shape is the contract the
     * frontend table binds to.
     */
    @Test
    void anySignedInRoleMayListCategoriesInAPagedEnvelope() throws Exception {
        mvc.perform(get("/api/categories").with(user("clerk").roles("EMPLOYEE")))
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
     * created category from an edited one, and {@code active} comes back true:
     * a category is born usable, and the UI renders what the API answers
     * rather than re-deriving it.
     */
    @Test
    void adminCreateAnswers201WithTheStoredCategory() throws Exception {
        mvc.perform(post("/api/categories").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bebidas\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Bebidas"))
                .andExpect(jsonPath("$.active").value(true));
    }

    /**
     * AC: "Validation errors show field-level messages in the UI" — which is
     * only possible if the API says *which field* refused, rather than a bare
     * sentence the form would have to guess about. Whitespace-only is the
     * shape a user actually produces by hitting space in the box.
     */
    @Test
    void blankNameIsRefusedWithAFieldErrorNamingTheField() throws Exception {
        mvc.perform(post("/api/categories").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").isArray())
                .andExpect(jsonPath("$.fieldErrors.name[0]").isNotEmpty());
    }

    /**
     * business-rules §5: the name is 1–120 chars — the blank half is covered
     * above, this is the cap. Asserting the exact {@code @Size} message proves
     * the refusal came from the length rule itself (V4's DB CHECK is the
     * backstop behind it, pinned in {@code FlywayMigrationsTest}), and
     * testing-strategy wants every domain rule to have its own test.
     */
    @Test
    void overlongNameIsRefusedWithTheCapMessage() throws Exception {
        mvc.perform(post("/api/categories").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name[0]")
                        .value("Name must be at most 120 characters."));
    }

    /**
     * business-rules §4, row "Categories": ADMIN only — the first production
     * {@code @PreAuthorize} in the system, and the reason ticket #4 switched
     * {@code @EnableMethodSecurity} on. Both non-admin roles are named
     * because the middle one is the one a reader would assume is privileged.
     *
     * <p>The refusal carries a body: the frontend renders 403s as a toast
     * (frontend-architecture §Forms) and a toast with no sentence has nothing
     * to say.
     */
    @ParameterizedTest
    @ValueSource(strings = {"EMPLOYEE", "MANAGER"})
    void creatingACategoryIsRefusedForEveryNonAdminRole(String role) throws Exception {
        mvc.perform(post("/api/categories").with(user("someone").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bebidas\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminEditAnswersWithTheUpdatedCategory() throws Exception {
        mvc.perform(put("/api/categories/7").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bebidas naturais\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Bebidas naturais"));
    }

    /**
     * The ticket's "change status", as a deactivation rather than a delete:
     * products point at categories with ON DELETE RESTRICT, so retiring a
     * vocabulary row has to leave it in history (GLOSSARY "Deactivated").
     */
    @Test
    void statusEndpointTurnsACategoryOffAndAnswersWithIt() throws Exception {
        mvc.perform(put("/api/categories/7/status").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.active").value(false));
    }

    /** The status endpoint is a write like any other: same matrix, same body. */
    @Test
    void employeeCannotChangeACategoryStatus() throws Exception {
        mvc.perform(put("/api/categories/7/status").with(user("clerk").roles("EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * api-design.md's errors table: "404 | unknown id". The UI must be able
     * to toast the same envelope it gets everywhere else, so a stale row
     * (deleted in another tab) cannot become a 500 or an empty 404.
     */
    @Test
    void unknownCategoryIsA404ProblemJson() throws Exception {
        mvc.perform(put("/api/categories/999").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }
}
