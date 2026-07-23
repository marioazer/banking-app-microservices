package com.example.transactionservice.model;

/**
 * Lifecycle state of a transaction record, per FR8.2 and FR8.3.
 */
public enum TransactionStatus {
    COMPLETED,
    PENDING_APPROVAL,
    REJECTED,
    FAILED
}
