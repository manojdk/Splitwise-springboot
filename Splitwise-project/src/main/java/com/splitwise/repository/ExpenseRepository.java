package com.splitwise.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.splitwise.entity.Expense;
import com.splitwise.entity.User;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
	List<Expense> findByCreatedByAndDateBetween(User createdBy, Date startDate, Date endDate);

}
