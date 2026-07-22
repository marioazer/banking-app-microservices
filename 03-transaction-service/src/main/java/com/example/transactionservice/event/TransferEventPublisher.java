package com.example.transactionservice.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransferEventPublisher {

    private final KafkaTemplate<String, FundsTransferredEvent> kafkaTemplate;
    private static final String TOPIC = "successful-transfers";

    public TransferEventPublisher(KafkaTemplate<String, FundsTransferredEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Intercepts the domain event published by TransferService.
     * Phase.AFTER_COMMIT ensures Kafka only receives this if the database transaction fully succeeds[cite: 1].
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFundsTransferredEvent(FundsTransferredEvent event) {
        kafkaTemplate.send(TOPIC, event.transactionId().toString(), event);
    }
}