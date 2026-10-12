package com.splitwise.repository;

import com.splitwise.domain.RecurringExpense;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    @EntityGraph(attributePaths = {"group", "paidBy", "createdBy"})
    Optional<RecurringExpense> findDetailedById(Long id);

    /** Locks the template row, so two scheduler runs cannot create the same occurrence at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecurringExpense r where r.id = :id")
    Optional<RecurringExpense> findByIdForUpdate(@Param("id") Long id);

    /** Templates with an occurrence due on or before {@code today}. */
    @Query("""
            select r.id from RecurringExpense r
            where r.active = true and r.nextRunDate <= :today
            order by r.nextRunDate, r.id
            """)
    List<Long> findDueIds(@Param("today") LocalDate today);

    @Query("select r.group.id from RecurringExpense r where r.id = :id and r.group is not null")
    Optional<Long> findGroupIdById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"group", "paidBy", "createdBy"})
    List<RecurringExpense> findByGroupIdInOrderByIdDesc(Collection<Long> groupIds);

    /** Non-group templates the user is part of (as a participant, the payer or the creator). */
    @EntityGraph(attributePaths = {"paidBy", "createdBy"})
    @Query("""
            select distinct r from RecurringExpense r join r.shares s
            where r.group is null
              and (s.userId = :userId or r.paidBy.id = :userId or r.createdBy.id = :userId)
            order by r.id desc
            """)
    List<RecurringExpense> findNonGroupForUser(@Param("userId") Long userId);
}
