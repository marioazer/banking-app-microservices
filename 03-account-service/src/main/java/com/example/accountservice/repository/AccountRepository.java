package com.example.accountservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.accountservice.model.AccountEntity;
import com.example.accountservice.model.AccountStatus;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Retrieves all accounts for a specific user, excluding those with a specific status.
     * Fulfills FR5.3 AC4: Automatically filter out any accounts where the status is CLOSED.[cite: 3]
     */
    List<AccountEntity> findByUserIdAndStatusNot(Long userId, AccountStatus status);
}