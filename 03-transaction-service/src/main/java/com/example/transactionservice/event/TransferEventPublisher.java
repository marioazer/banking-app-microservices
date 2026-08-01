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

    // learned a plain @EventListener would fire the moment publishEvent() is called, even if the
    // surrounding transaction later rolled back, @TransactionalEventListener waits and only runs
    // if that transaction actually commits, exactly what you want before telling kafka money moved
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFundsTransferredEvent(FundsTransferredEvent event) {
        kafkaTemplate.send(TOPIC, event.transactionId().toString(), event);
    }
}