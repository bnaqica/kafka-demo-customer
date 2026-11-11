package com.kafkademo.customer.api;

import com.kafkademo.customer.config.ApiRoles;
import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.response.CustomerResponse;
import com.kafkademo.customer.service.CustomerChangeService;
import com.kafkademo.customer.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerChangeService changeService;

    @PreAuthorize(ApiRoles.ADMIN)
    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody CustomerRequest request) {
        IdempotentResult<CustomerResponse> result = changeService.create(idempotencyKey, request);
        return ResponseEntity.status(result.status())
                .location(URI.create("/api/customers/" + result.body().id()))
                .body(result.body());
    }

    @PreAuthorize(ApiRoles.READ)
    @GetMapping
    public List<CustomerResponse> list() {
        return customerService.findAll();
    }

    @PreAuthorize(ApiRoles.READ)
    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return customerService.get(id);
    }

    @PreAuthorize(ApiRoles.ADMIN)
    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return changeService.update(id, request);
    }

    @PreAuthorize(ApiRoles.ADMIN)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        changeService.delete(id);
    }
}
