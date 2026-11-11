package com.kafkademo.customer.processors;

import com.kafkademo.customer.dto.CustomerChange;
import com.kafkademo.customer.dto.event.ChangedAddress;
import com.kafkademo.customer.dto.event.ChangedPhone;
import com.kafkademo.customer.dto.request.AddressRequest;
import com.kafkademo.customer.dto.request.CustomerRequest;
import com.kafkademo.customer.dto.response.AddressResponse;
import com.kafkademo.customer.dto.response.CustomerResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Works out what a customer PUT changes. Expects a normalized request. */
@Component
public class CustomerChangeClassifier {

    /** The most specific change covering everything the request alters, or empty if it alters nothing. */
    public Optional<CustomerChange> classify(CustomerResponse before, CustomerRequest request) {
        Set<ChangedPhone> phonesBefore = phonesOf(before);
        Set<ChangedPhone> phonesAfter = phonesOf(request);
        List<String> profile = changedProfileFields(before, request);
        boolean addressChanged = addressChanged(before, request);
        boolean phonesChanged = !phonesBefore.equals(phonesAfter);

        if (profile.isEmpty() && !addressChanged && !phonesChanged) {
            return Optional.empty();
        }
        if (profile.isEmpty() && addressChanged && !phonesChanged) {
            return Optional.of(CustomerChange.address(toChangedAddress(request.address())));
        }
        if (profile.isEmpty() && !addressChanged) {
            Optional<CustomerChange> singlePhone = singlePhoneChange(phonesBefore, phonesAfter);
            if (singlePhone.isPresent()) {
                return singlePhone;
            }
        }
        List<String> fields = new ArrayList<>(profile);
        if (addressChanged) {
            fields.add("address");
        }
        if (phonesChanged) {
            fields.add("phones");
        }
        return Optional.of(CustomerChange.fields(fields));
    }

    /** Whether the customer row itself differs, as opposed to only its phones. */
    public boolean customerFieldsChanged(CustomerResponse before, CustomerRequest request) {
        return !changedProfileFields(before, request).isEmpty() || addressChanged(before, request);
    }

    private static Optional<CustomerChange> singlePhoneChange(Set<ChangedPhone> before, Set<ChangedPhone> after) {
        Set<ChangedPhone> added = new HashSet<>(after);
        added.removeAll(before);
        Set<ChangedPhone> removed = new HashSet<>(before);
        removed.removeAll(after);

        if (added.size() == 1 && removed.isEmpty()) {
            return Optional.of(CustomerChange.phoneAdd(added.iterator().next()));
        }
        if (removed.size() == 1 && added.isEmpty()) {
            return Optional.of(CustomerChange.phoneDelete(removed.iterator().next()));
        }
        if (added.size() == 1 && removed.size() == 1) {
            ChangedPhone previous = removed.iterator().next();
            ChangedPhone phone = added.iterator().next();
            if (previous.type() == phone.type()) {
                return Optional.of(CustomerChange.phoneChange(previous, phone));
            }
        }
        return Optional.empty();
    }

    private static List<String> changedProfileFields(CustomerResponse before, CustomerRequest request) {
        List<String> fields = new ArrayList<>();
        addIfChanged(fields, "firstName", before.firstName(), request.firstName());
        addIfChanged(fields, "middleName", before.middleName(), request.middleName());
        addIfChanged(fields, "lastName", before.lastName(), request.lastName());
        addIfChanged(fields, "dateOfBirth", before.dateOfBirth(), request.dateOfBirth());
        addIfChanged(fields, "gender", before.gender(), request.gender());
        addIfChanged(fields, "email", before.email(), request.email());
        return fields;
    }

    private static void addIfChanged(List<String> fields, String name, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            fields.add(name);
        }
    }

    private static boolean addressChanged(CustomerResponse before, CustomerRequest request) {
        AddressRequest address = request.address();
        return !before.address()
                .equals(new AddressResponse(
                        address.street(), address.apartment(), address.city(), address.state(), address.zip()));
    }

    private static ChangedAddress toChangedAddress(AddressRequest address) {
        return new ChangedAddress(
                address.street(), address.apartment(), address.city(), address.state(), address.zip());
    }

    private static Set<ChangedPhone> phonesOf(CustomerResponse customer) {
        return customer.phones().stream()
                .map(phone -> new ChangedPhone(phone.type(), phone.number()))
                .collect(Collectors.toSet());
    }

    private static Set<ChangedPhone> phonesOf(CustomerRequest request) {
        return request.phones().stream()
                .map(phone -> new ChangedPhone(phone.type(), phone.number()))
                .collect(Collectors.toSet());
    }
}
