package com.example.accountservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.accountservice.model.TransactionEntity;
import com.example.accountservice.model.TransactionType;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // Fulfills FR6.2 & FR6.4: Database-level pagination for transactions[cite: 4]
    Page<TransactionEntity> findByAccountId(Long accountId, Pageable pageable);

    // Fulfills FR6.3: Dynamic filtering by transaction type alongside pagination[cite: 4]
    Page<TransactionEntity> findByAccountIdAndTransactionType(Long accountId, TransactionType transactionType, Pageable pageable);
}