package com.example.profileservice.repository;

import com.example.profileservice.model.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {
    Optional<UserPreferenceEntity> findByUserId(Long userId);
}