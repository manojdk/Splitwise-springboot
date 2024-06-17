package com.splitwise.service;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.splitwise.dto.ExpenseCreationDto;
import com.splitwise.dto.ExpenseDto;

@Service
public interface ExpenseService {

	public ExpenseDto addExpense(ExpenseCreationDto expenseCreationDTO);

	public List<ExpenseDto> getExpensesByUserAndDate(Long userId, Date startDate, Date endDate);

}
