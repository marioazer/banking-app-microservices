package com.example.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.example.authservice.model.TwoFactorCode;

@Repository
public interface TwoFactorCodeRepository extends JpaRepository<TwoFactorCode, Long> {

    Optional<TwoFactorCode> findByUserId(Long userId);

    /**
     * Deletes any existing 2FA codes for a user.
     * The @Modifying annotation is required by Spring Data JPA whenever a query
     * modifies the database (INSERT, UPDATE, DELETE) rather than just reading from it.
     */
    @Modifying
    void deleteByUserId(Long userId);
}