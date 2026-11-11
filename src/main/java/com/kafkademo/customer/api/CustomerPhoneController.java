package com.kafkademo.customer.api;

import com.kafkademo.customer.config.ApiRoles;
import com.kafkademo.customer.dto.IdempotentResult;
import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.service.CustomerChangeService;
import com.kafkademo.customer.service.CustomerPhoneService;
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
@RequestMapping("/api/customers/{customerId}/phones")
public class CustomerPhoneController {

    private final CustomerPhoneService phoneService;
    private final CustomerChangeService changeService;

    @PreAuthorize(ApiRoles.ADMIN)
    @PostMapping
    public ResponseEntity<PhoneResponse> add(
            @PathVariable UUID customerId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody PhoneRequest request) {
        IdempotentResult<PhoneResponse> result = changeService.addPhone(idempotencyKey, customerId, request);
        return ResponseEntity.status(result.status())
                .location(URI.create("/api/customers/" + customerId + "/phones/"
                        + result.body().id()))
                .body(result.body());
    }

    @PreAuthorize(ApiRoles.READ)
    @GetMapping
    public List<PhoneResponse> list(@PathVariable UUID customerId) {
        return phoneService.findByCustomer(customerId);
    }

    @PreAuthorize(ApiRoles.READ)
    @GetMapping("/{phoneId}")
    public PhoneResponse get(@PathVariable UUID customerId, @PathVariable UUID phoneId) {
        return phoneService.get(customerId, phoneId);
    }

    @PreAuthorize(ApiRoles.ADMIN)
    @PutMapping("/{phoneId}")
    public PhoneResponse update(
            @PathVariable UUID customerId, @PathVariable UUID phoneId, @Valid @RequestBody PhoneRequest request) {
        return changeService.updatePhone(customerId, phoneId, request);
    }

    @PreAuthorize(ApiRoles.ADMIN)
    @DeleteMapping("/{phoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID customerId, @PathVariable UUID phoneId) {
        changeService.deletePhone(customerId, phoneId);
    }
}
