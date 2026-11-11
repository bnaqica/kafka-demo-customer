package com.kafkademo.customer.api;

import com.kafkademo.customer.config.ApiRoles;
import com.kafkademo.customer.dto.request.IdempotencyKeyRequest;
import com.kafkademo.customer.dto.response.IdempotencyKeyResponse;
import com.kafkademo.customer.service.IdempotencyKeyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize(ApiRoles.ADMIN)
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/idempotency-keys")
public class IdempotencyKeyController {

    private final IdempotencyKeyService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdempotencyKeyResponse create(@Valid @RequestBody IdempotencyKeyRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<IdempotencyKeyResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{key}")
    public IdempotencyKeyResponse get(@PathVariable String key) {
        return service.get(key);
    }

    @PutMapping("/{key}")
    public IdempotencyKeyResponse update(@PathVariable String key, @Valid @RequestBody IdempotencyKeyRequest request) {
        return service.update(key, request);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key) {
        service.delete(key);
    }
}
