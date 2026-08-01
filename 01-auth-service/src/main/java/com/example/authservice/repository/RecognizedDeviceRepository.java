package com.example.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.authservice.model.RecognizedDevice;

@Repository
public interface RecognizedDeviceRepository extends JpaRepository<RecognizedDevice, Long> {

    // learned the "And" in the middle of the method name is what tells spring data to combine
    // both fields with an and in the where clause, could swap it for "Or" and get very different sql
    Optional<RecognizedDevice> findByUserIdAndDeviceHash(Long userId, String deviceHash);
    
}