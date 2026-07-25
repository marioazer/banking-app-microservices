package com.example.transactionservice.repository;

import com.example.transactionservice.model.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

// <TransactionEntity, UUID> since this entity's primary key is the client facing confirmation
// uuid itself, not an auto generated number like most of the other entities in this project use
@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
}
