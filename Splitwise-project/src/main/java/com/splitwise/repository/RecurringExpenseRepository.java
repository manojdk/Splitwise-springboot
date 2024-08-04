package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.splitwise.entity.RecurringExpense;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {
}
