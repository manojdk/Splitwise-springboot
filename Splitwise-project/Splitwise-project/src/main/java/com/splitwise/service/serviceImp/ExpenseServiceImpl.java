package com.splitwise.service.serviceImp;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.splitwise.dto.ExpenseCreationDto;
import com.splitwise.dto.ExpenseDto;
import com.splitwise.dto.RecurringExpenseDto;
import com.splitwise.entity.Expense;
import com.splitwise.entity.RecurringExpense;
import com.splitwise.entity.User;
import com.splitwise.entity.UserExpense;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.UserExpenseRepository;
import com.splitwise.service.UserService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ExpenseServiceImpl {
	private ExpenseRepository expenseRepository;

	private UserExpenseRepository userExpenseRepository;

	private UserService userService;

	public ExpenseDto addExpense(ExpenseCreationDto expenseCreationDTO) {
		try {
			User user = userService.getUserEntityById(expenseCreationDTO.getUserId());
			Expense expense = new Expense();
			expense.setExpenseDescription(expenseCreationDTO.getDescription());
			expense.setExpenseAmount(expenseCreationDTO.getAmount());
			expense.setExpenseDate(new Date());
			expense.setCreatedBy(user);
			expense = expenseRepository.save(expense);

			double totalShare = 0.0;
			for (Long memberId : expenseCreationDTO.getMemberIds()) {
				User member = userService.getUserEntityById(memberId);
				Double share = expenseCreationDTO.getShares().get(memberId);
				totalShare += share;
				UserExpense userExpense = new UserExpense();
				userExpense.setUser(member);
				userExpense.setExpense(expense);
				userExpense.setShare(share);
				userExpenseRepository.save(userExpense);
			}

			if (totalShare != expenseCreationDTO.getAmount()) {
				throw new IllegalArgumentException("Total share does not match expense amount");
			}

			return toDto(expense);
		} catch (Exception e) {
			throw new RuntimeException("Error adding expense", e);
		}
	}

	public List<ExpenseDto> getExpensesByUserAndDate(Long userId, Date startDate, Date endDate) {
		try {
			User user = userService.getUserEntityById(userId);
			List<Expense> expenses = expenseRepository.findByCreatedByAndDateBetween(user, startDate, endDate);
			return expenses.stream().map(this::toDto).collect(Collectors.toList());
		} catch (Exception e) {
			throw new RuntimeException("Error fetching expenses by user and date", e);
		}
	}

	public void addRecurringExpense(RecurringExpenseDto recurringExpenseDTO) {
		try {
			User user = userService.getUserEntityById(recurringExpenseDTO.getCreatedById());
			RecurringExpense recurringExpense = new RecurringExpense();
			recurringExpense.setRecurringDescription(recurringExpenseDTO.getRecurringDescription());
			recurringExpense.setRecurringAmount(recurringExpenseDTO.getAmount());
			recurringExpense.setStartDate(recurringExpenseDTO.getStartDate());
			recurringExpense.setRecurringPeriod(recurringExpenseDTO.getRecurringPeriod());
			// Set category and group
			// recurringExpense.setCategory(categoryService.getCategoryById(recurringExpenseDTO.getCategoryId()));
			// recurringExpense.setGroup(groupService.getGroupById(recurringExpenseDTO.getGroupId()));

			recurringExpense.setCreatedBy(user);
			// Save recurring expense
		} catch (Exception e) {
			throw new RuntimeException("Error adding recurring expense", e);
		}
	}

	private ExpenseDto toDto(Expense expense) {
		ExpenseDto expenseDTO = new ExpenseDto();
		expenseDTO.setExpenseId(expense.getExpenseId());
		expenseDTO.setExpenseDescription(expense.getExpenseDescription());
		expenseDTO.setExpenseAmount(expense.getExpenseAmount());
		expenseDTO.setExpenseDate(expense.getExpenseDate());
		return expenseDTO;
	}

}