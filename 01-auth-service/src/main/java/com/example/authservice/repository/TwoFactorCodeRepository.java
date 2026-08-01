package com.example.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.example.authservice.model.TwoFactorCode;

@Repository
public interface TwoFactorCodeRepository extends JpaRepository<TwoFactorCode, Long> {

    Optional<TwoFactorCode> findByUserId(Long userId);

    // this one has no @Query at all, spring data can auto derive a delete just from the method
    // name deleteByUserId, @Modifying is still required since a delete is not a plain read
    @Modifying
    void deleteByUserId(Long userId);
}