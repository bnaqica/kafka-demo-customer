package com.kafkademo.customer.repository;

import com.kafkademo.customer.entity.CustomerPhone;
import com.kafkademo.customer.entity.PhoneType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPhoneRepository extends JpaRepository<CustomerPhone, UUID> {

    List<CustomerPhone> findByCustomerId(UUID customerId);

    List<CustomerPhone> findByCustomerIdIn(Collection<UUID> customerIds);

    Optional<CustomerPhone> findByIdAndCustomerId(UUID id, UUID customerId);

    boolean existsByCustomerIdAndTypeAndNumber(UUID customerId, PhoneType type, String number);

    boolean existsByCustomerIdAndTypeAndNumberAndIdNot(UUID customerId, PhoneType type, String number, UUID id);
}
