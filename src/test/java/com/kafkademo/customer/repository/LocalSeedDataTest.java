package com.kafkademo.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kafkademo.customer.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local")
class LocalSeedDataTest {

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CustomerPhoneRepository phones;

    @Test
    void localProfileLoadsSampleCustomersWithPhones() {
        assertThat(customers.count()).isEqualTo(12);
        assertThat(phones.count()).isGreaterThanOrEqualTo(12);
    }
}
