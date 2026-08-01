package com.example.accountservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.accountservice.model.TransactionEntity;
import com.example.accountservice.model.TransactionType;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // learned passing a pageable straight into a derived query method is enough for spring data
    // to add the limit/offset and sorting itself, no manual sql pagination math required
    Page<TransactionEntity> findByAccountId(Long accountId, Pageable pageable);

    Page<TransactionEntity> findByAccountIdAndTransactionType(Long accountId, TransactionType transactionType, Pageable pageable);
}