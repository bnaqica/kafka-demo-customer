package com.kafkademo.customer.publisher;

import com.kafkademo.customer.config.KafkaTopicsProperties;
import com.kafkademo.customer.dto.CustomerChange;
import com.kafkademo.customer.dto.event.CustomerChangeEvent;
import com.kafkademo.customer.dto.response.CustomerResponse;
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

    /** Publishes after the current transaction commits, so consumers never see a change that was rolled back. */
    public void publish(CustomerChange change, CustomerResponse customer) {
        CustomerChangeEvent event = new CustomerChangeEvent(
                UUID.randomUUID(),
                change.type(),
                Instant.now(clock),
                customer.id(),
                customer.firstName(),
                customer.lastName(),
                customer.email(),
                customer.address().state(),
                change.changes());
        String payload = jsonMapper.writeValueAsString(event);
        String key = customer.id().toString();
        String wireName = change.type().getWireName();

        Runnable send = () -> {
            // The change has already committed, so a publishing failure must not fail the request
            try {
                kafkaTemplate.send(topics.customerChangeEvent(), key, payload).whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for customer {}", wireName, key, ex);
                    } else {
                        log.info("Published {} for customer {}", wireName, key);
                    }
                });
            } catch (RuntimeException ex) {
                log.error("Failed to publish {} for customer {}", wireName, key, ex);
            }
        };

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
