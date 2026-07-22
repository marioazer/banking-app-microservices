package com.example.transactionservice.repository;

import com.example.transactionservice.model.AccountEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Fulfills FR7.2 AC1 & AC2: High-Performance Locking.[cite: 3]
     * Issues a 'SELECT ... FOR UPDATE' to exclusively lock the account row.
     * The lock is held until the surrounding @Transactional block completes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") Long id);
    
    // Standard non-locking read for basic dashboard queries
    Optional<AccountEntity> findById(Long id);
}