package com.example.accountservice.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.AccountStatus;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Retrieves all accounts for a specific user, excluding those with a specific status.
     * Fulfills FR5.3 AC4: Automatically filter out any accounts where the status is CLOSED.[cite: 3]
     */
    // "Not" in the method name flips the comparison to not equal, spring data derives the
    // whole where clause from this name alone, no @Query needed for something this simple
    List<AccountEntity> findByUserIdAndStatusNot(Long userId, AccountStatus status);

    /**
     * Fulfills FR7.2 AC1 & AC2: High-Performance Locking. Issues a 'SELECT ... FOR UPDATE' to
     * exclusively lock the account row; the lock is held until the surrounding @Transactional
     * block completes. Now that account-service is the sole owner of this table, transfer/wire
     * mutations go through this repository directly instead of a copy in transaction-service.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AccountEntity a WHERE a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * Aggregate balance per user for the notification service's daily-summary batch job
     * (04-notification-service's AccountServiceClient.getAggregateBalancesBatch), avoiding N+1
     * calls by summing in one query instead of one HTTP round trip per user.
     */
    @Query("SELECT a.userId, SUM(a.availableBalance) FROM AccountEntity a " +
           "WHERE a.userId IN :userIds AND a.status <> com.example.accountservice.model.AccountStatus.CLOSED " +
           "GROUP BY a.userId")
    List<Object[]> sumAvailableBalanceByUserIds(@Param("userIds") List<Long> userIds);
}