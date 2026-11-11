package com.kafkademo.customer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kafkademo.customer.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class NoSeedDataTest {

    @Autowired
    private CustomerRepository customers;

    @Test
    void otherProfilesLoadNoSampleCustomers() {
        assertThat(customers.count()).isZero();
    }
}
