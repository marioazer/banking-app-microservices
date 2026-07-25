package com.example.transactionservice.model;

/**
 * Lifecycle state of a transaction record, per FR8.2 and FR8.3.
 */
// this is a different enum from account-service's own TransactionType (CREDIT/DEBIT), easy to
// confuse the two names at first glance, this one tracks pending/approved/rejected review state
public enum TransactionStatus {
    COMPLETED,
    PENDING_APPROVAL,
    REJECTED,
    FAILED
}
