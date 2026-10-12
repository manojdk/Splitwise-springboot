package com.splitwise.repository;

import com.splitwise.domain.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    Optional<Expense> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    List<Expense> findByGroupIdOrderByExpenseDateDescIdDesc(Long groupId);

    /** Every expense the user paid for or has a share in, across all groups and non-group expenses. */
    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    @Query("""
            select e from Expense e
            where e.paidBy.id = :userId
               or e.id in (select s.expense.id from ExpenseShare s where s.user.id = :userId)
            order by e.expenseDate desc, e.id desc
            """)
    List<Expense> findInvolvingUser(@Param("userId") Long userId);

    boolean existsByGroupId(Long groupId);
}
