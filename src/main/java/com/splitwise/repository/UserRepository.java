package com.splitwise.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.splitwise.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** The user's current token version, without loading the entity (checked on every authenticated request). */
    @Query("select u.tokenVersion from User u where u.id = :id")
    Optional<Integer> findTokenVersion(@Param("id") Long id);

    /** Loads the user and locks the row until the transaction ends (used to serialise settle-ups per pair). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
