package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.splitwise.entity.UserExpense;

@Repository
public interface UserExpenseRepository extends JpaRepository<UserExpense, Long> {

}
