package com.kafkademo.customer.publisher;

import com.kafkademo.customer.config.KafkaTopicsProperties;
import com.kafkademo.customer.dto.event.CustomerCreatedEvent;
import com.kafkademo.customer.entity.Customer;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@RequiredArgsConstructor
@Component
public class CustomerEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final KafkaTopicsProperties topics;
    private final Clock clock;

    public void publishCustomerCreated(Customer customer) {
        CustomerCreatedEvent event = new CustomerCreatedEvent(
                UUID.randomUUID(),
                CustomerCreatedEvent.TYPE,
                Instant.now(clock),
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail());
        String payload = jsonMapper.writeValueAsString(event);
        String key = customer.getId().toString();

        Runnable send =
                () -> kafkaTemplate.send(topics.customerEvents(), key, payload).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for customer {}", event.eventType(), key, ex);
                    } else {
                        log.info("Published {} for customer {} to {}", event.eventType(), key, topics.customerEvents());
                    }
                });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
