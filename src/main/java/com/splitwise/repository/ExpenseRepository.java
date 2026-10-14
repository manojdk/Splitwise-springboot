package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Collection;
import com.splitwise.domain.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    Optional<Expense> findDetailedById(Long id);

    /** The group's expenses that are not deleted, newest first. */
    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    List<Expense> findByGroupIdAndDeletedAtIsNullOrderByExpenseDateDescIdDesc(Long groupId);

    /** Every non-deleted expense the user paid for or has a share in, across all groups and non-group expenses. */
    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    @Query("""
            select e from Expense e
            where e.deletedAt is null
              and (e.paidBy.id = :userId
                   or e.id in (select s.expense.id from ExpenseShare s where s.user.id = :userId))
            order by e.expenseDate desc, e.id desc
            """)
    List<Expense> findInvolvingUser(@Param("userId") Long userId);

    boolean existsByGroupId(Long groupId);

    /** Non-deleted expenses of several groups at once (one query instead of one per group). */
    @EntityGraph(attributePaths = {"paidBy", "group", "shares", "shares.user"})
    List<Expense> findByGroupIdInAndDeletedAtIsNull(Collection<Long> groupIds);

    /** The expense's group id (empty for a non-group or unknown expense), read without loading the expense. */
    @Query("select e.group.id from Expense e where e.id = :id and e.group is not null")
    Optional<Long> findGroupIdById(@Param("id") Long id);

    /** Whether the two users are both in some non-group expense (as payer or participant), deleted ones included. */
    @Query("""
            select count(e) > 0 from Expense e
            where e.group is null
              and (e.paidBy.id = :userId
                   or exists (select s from ExpenseShare s where s.expense = e and s.user.id = :userId))
              and (e.paidBy.id = :otherId
                   or exists (select s from ExpenseShare s where s.expense = e and s.user.id = :otherId))
            """)
    boolean haveNonGroupExpenseTogether(@Param("userId") Long userId, @Param("otherId") Long otherId);

    /** People sharing a non-group expense with the user (the user paid it or shares it too). */
    @Query("""
            select distinct s.user from ExpenseShare s
            where s.expense.group is null and s.user.id <> :userId
              and (s.expense.paidBy.id = :userId
                   or exists (select s2 from ExpenseShare s2 where s2.expense = s.expense and s2.user.id = :userId))
            """)
    List<com.splitwise.domain.User> findNonGroupSharers(@Param("userId") Long userId);

    /** People who paid a non-group expense the user shares. */
    @Query("""
            select distinct e.paidBy from Expense e
            where e.group is null and e.paidBy.id <> :userId
              and exists (select s from ExpenseShare s where s.expense = e and s.user.id = :userId)
            """)
    List<com.splitwise.domain.User> findNonGroupPayers(@Param("userId") Long userId);
}
