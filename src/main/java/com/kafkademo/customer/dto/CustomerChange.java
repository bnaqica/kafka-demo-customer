package com.kafkademo.customer.dto;

import com.kafkademo.customer.dto.event.ChangedAddress;
import com.kafkademo.customer.dto.event.ChangedPhone;
import com.kafkademo.customer.dto.event.CustomerChanges;
import com.kafkademo.customer.dto.event.CustomerEventType;
import java.util.List;

/** The kind of change a call made and its details, before it becomes an event. */
public record CustomerChange(CustomerEventType type, CustomerChanges changes) {

    private static final CustomerChanges NOTHING = new CustomerChanges(null, null, null, null);

    public static CustomerChange addition() {
        return new CustomerChange(CustomerEventType.ADDITION, NOTHING);
    }

    public static CustomerChange delete() {
        return new CustomerChange(CustomerEventType.DELETE, NOTHING);
    }

    public static CustomerChange fields(List<String> fields) {
        return new CustomerChange(CustomerEventType.CHANGE, new CustomerChanges(fields, null, null, null));
    }

    public static CustomerChange address(ChangedAddress address) {
        return new CustomerChange(CustomerEventType.ADDRESS_CHANGE, new CustomerChanges(null, address, null, null));
    }

    public static CustomerChange phoneAdd(ChangedPhone phone) {
        return new CustomerChange(CustomerEventType.PHONE_ADD, new CustomerChanges(null, null, phone, null));
    }

    public static CustomerChange phoneDelete(ChangedPhone phone) {
        return new CustomerChange(CustomerEventType.PHONE_DELETE, new CustomerChanges(null, null, phone, null));
    }

    public static CustomerChange phoneChange(ChangedPhone previous, ChangedPhone phone) {
        return new CustomerChange(CustomerEventType.PHONE_CHANGE, new CustomerChanges(null, null, phone, previous));
    }
}
