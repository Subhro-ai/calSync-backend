package com.CalSync.calSync.repository;

import com.CalSync.calSync.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySubscriptionToken(String subscriptionToken);
    Optional<User> findByUsername(String username);
}