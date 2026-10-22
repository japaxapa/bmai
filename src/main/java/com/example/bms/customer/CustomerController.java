package com.example.bms.customer;

import com.example.bms.shared.PagedEnvelope;
import com.example.bms.shared.PageSize;
import com.example.bms.shared.StatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customers HTTP surface (api-design: CRUD + status + search/filter/
 * pagination). Each verb arrives with the test that pins it — this slice has
 * the read side.
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public PagedEnvelope<CustomerResponse> list(
            @RequestParam(defaultValue = "0") int page,
            // api-design size ≤ 100: the default and PageWindow's clamp share PageSize.
            @RequestParam(defaultValue = PageSize.DEFAULT_PAGE_SIZE) int size,
            // api-design: search over the party's name and document; blank (or absent) = no filter.
            @RequestParam(defaultValue = "") String search) {
        return customers.list(page, size, search);
    }

    /** 201, not 200: a caller must be able to tell a created row from an edited one. */
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        var created = customers.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 200 with the updated row: the UI re-renders what the API answers. */
    @PutMapping("/{id}")
    public CustomerResponse edit(@PathVariable long id,
                                 @Valid @RequestBody CustomerRequest request) {
        return customers.edit(id, request);
    }

    /**
     * api-design: the deactivation convention every collection follows — the
     * shared {@code StatusRequest}. 200 with the updated row — same shape as
     * edit, so the UI has one path.
     */
    @PutMapping("/{id}/status")
    public CustomerResponse changeStatus(@PathVariable long id,
                                         @RequestBody StatusRequest request) {
        return customers.changeStatus(id, request.active());
    }
}
