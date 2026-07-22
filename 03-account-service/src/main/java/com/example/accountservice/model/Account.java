package com.example.accountservice.model;

// Maps to account_type_enum[cite: 3]
public enum AccountType {
    CHECKING,
    SAVINGS
}

// Maps to account_status_enum[cite: 3]
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}

// Maps to transaction_type_enum[cite: 4]
public enum TransactionType {
    CREDIT,
    DEBIT
}