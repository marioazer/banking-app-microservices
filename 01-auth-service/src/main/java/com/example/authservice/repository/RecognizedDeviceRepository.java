package com.example.authservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.authservice.model.RecognizedDevice;

@Repository
public interface RecognizedDeviceRepository extends JpaRepository<RecognizedDevice, Long> {

    /**
     * Spring Data JPA "Magic Method". 
     * Hibernate will automatically parse the method name and generate the following SQL:
     * SELECT * FROM recognized_devices WHERE user_id = ? AND device_hash = ?
     */
    Optional<RecognizedDevice> findByUserIdAndDeviceHash(Long userId, String deviceHash);
    
}