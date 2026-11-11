package com.kafkademo.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kafkademo.customer.dto.request.PhoneRequest;
import com.kafkademo.customer.dto.response.PhoneResponse;
import com.kafkademo.customer.entity.CustomerPhone;
import com.kafkademo.customer.entity.PhoneType;
import com.kafkademo.customer.exception.DuplicatePhoneException;
import com.kafkademo.customer.exception.PhoneNotFoundException;
import com.kafkademo.customer.repository.CustomerPhoneRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerPhoneServiceImplTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID PHONE = UUID.randomUUID();
    private static final PhoneRequest REQUEST = new PhoneRequest(PhoneType.CELL, " 555-0100 ");

    @Mock
    private CustomerPhoneRepository repository;

    @InjectMocks
    private CustomerPhoneServiceImpl service;

    @Test
    void createStoresTheTrimmedPhone() {
        when(repository.save(any(CustomerPhone.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneResponse created = service.create(CUSTOMER, REQUEST);

        assertThat(created.id()).isNotNull();
        assertThat(created.number()).isEqualTo("555-0100");
    }

    @Test
    void createRejectsADuplicate() {
        when(repository.existsByCustomerIdAndTypeAndNumber(CUSTOMER, PhoneType.CELL, "555-0100"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(CUSTOMER, REQUEST)).isInstanceOf(DuplicatePhoneException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void getFindsThePhoneOnlyUnderItsOwner() {
        when(repository.findByIdAndCustomerId(PHONE, CUSTOMER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(CUSTOMER, PHONE)).isInstanceOf(PhoneNotFoundException.class);
    }

    @Test
    void updateChangesTheStoredValues() {
        when(repository.findByIdAndCustomerId(PHONE, CUSTOMER)).thenReturn(Optional.of(existing()));
        when(repository.save(any(CustomerPhone.class))).thenAnswer(inv -> inv.getArgument(0));

        PhoneResponse updated = service.update(CUSTOMER, PHONE, new PhoneRequest(PhoneType.WORK, "555-0111"));

        assertThat(updated.id()).isEqualTo(PHONE);
        assertThat(updated.type()).isEqualTo(PhoneType.WORK);
        assertThat(updated.number()).isEqualTo("555-0111");
    }

    @Test
    void updateRejectsAValueAnotherPhoneHas() {
        when(repository.findByIdAndCustomerId(PHONE, CUSTOMER)).thenReturn(Optional.of(existing()));
        when(repository.existsByCustomerIdAndTypeAndNumberAndIdNot(CUSTOMER, PhoneType.WORK, "555-0111", PHONE))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(CUSTOMER, PHONE, new PhoneRequest(PhoneType.WORK, "555-0111")))
                .isInstanceOf(DuplicatePhoneException.class);
    }

    @Test
    void deleteRemovesThePhone() {
        CustomerPhone phone = existing();
        when(repository.findByIdAndCustomerId(PHONE, CUSTOMER)).thenReturn(Optional.of(phone));

        service.delete(CUSTOMER, PHONE);

        verify(repository).delete(phone);
    }

    @Test
    void findByCustomersGroupsPhonesByOwner() {
        UUID other = UUID.randomUUID();
        when(repository.findByCustomerIdIn(List.of(CUSTOMER, other))).thenReturn(List.of(existing()));

        assertThat(service.findByCustomers(List.of(CUSTOMER, other)))
                .containsOnlyKeys(CUSTOMER)
                .extractingByKey(CUSTOMER)
                .satisfies(phones -> assertThat(phones).hasSize(1));
    }

    private static CustomerPhone existing() {
        return CustomerPhone.builder()
                .id(PHONE)
                .customerId(CUSTOMER)
                .type(PhoneType.CELL)
                .number("555-0100")
                .build();
    }
}
