package com.example.transactionservice.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Wire-transfer ledger record, keyed by the client-facing confirmation UUID
 * (transaction_id) rather than the shared table's internal bigserial id.
 *
 * NOTE: as of this suite, no Flyway migration in this module actually adds a
 * "transaction_id" UUID column to the shared "transactions" table (only
 * V3__Update_Transactions_Schema.sql, which adds "status"). This entity
 * matches how ExternalWireService/InternalFraudController already use it in
 * code; the schema gap is a real finding for the upcoming database-connections
 * phase, not something papered over here.
 */
@Entity
@Table(name = "wire_transactions")
public class TransactionEntity {

    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false)
    private String description;

    public TransactionEntity() {}

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
