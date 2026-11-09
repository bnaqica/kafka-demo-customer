package com.kafkademo.customer.repository;

import com.kafkademo.customer.entity.ApiAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiAuditRepository extends JpaRepository<ApiAudit, UUID> {}
