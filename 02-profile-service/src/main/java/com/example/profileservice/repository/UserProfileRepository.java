package com.example.profileservice.repository;

import com.example.profileservice.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// no custom query methods needed here at all, findById/save/etc from JpaRepository itself
// already cover everything ProfileManagementService and the controller actually call
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}