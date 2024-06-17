package com.splitwise.controller;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.splitwise.dto.ExpenseCreationDto;
import com.splitwise.dto.ExpenseDto;
import com.splitwise.dto.RecurringExpenseDto;
import com.splitwise.service.ExpenseService;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

	@Autowired
	private ExpenseService expenseService;

	@PostMapping("/saveorupdate")
	public ResponseEntity<ExpenseDto> createExpense(@RequestBody ExpenseCreationDto expenseCreationDTO) {
		try {
			ExpenseDto expense = expenseService.addExpense(expenseCreationDTO);
			return new ResponseEntity<>(expense, HttpStatus.CREATED);
		} catch (IllegalArgumentException e) {
			return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/list/{id}")
	public ResponseEntity<ExpenseDto> getExpenseById(@PathVariable Long id) {
		try {
			ExpenseDto expense = expenseService.getExpensesByUserAndDate(id, new Date(0), new Date()).get(0);
			return new ResponseEntity<>(expense, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping("/daily")
	public ResponseEntity<List<ExpenseDto>> getDailyReport(@RequestParam Long userId,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
		try {
			List<ExpenseDto> expenses = expenseService.getExpensesByUserAndDate(userId, date, date);
			return new ResponseEntity<>(expenses, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/monthly")
	public ResponseEntity<List<ExpenseDto>> getMonthlyReport(@RequestParam Long userId, @RequestParam int month,
			@RequestParam int year) {
		try {
			List<ExpenseDto> expenses = expenseService.getExpensesByUserAndDate(userId,
					new Date(year - 1900, month - 1, 1), new Date(year - 1900, month, 0));
			return new ResponseEntity<>(expenses, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/recurring")
	public ResponseEntity<Void> addRecurringExpense(@RequestBody RecurringExpenseDto recurringExpenseDTO) {
		try {
			expenseService.addRecurringExpense(recurringExpenseDTO);
			return new ResponseEntity<>(HttpStatus.CREATED);
		} catch (Exception e) {
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
