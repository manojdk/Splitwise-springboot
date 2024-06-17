package com.splitwise.controller;

import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.splitwise.dto.ExpenseDto;
import com.splitwise.service.ReportService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
public class ReportController {

	private ReportService reportService;

	@GetMapping("/daily")
	public ResponseEntity<List<ExpenseDto>> getDailyReport(@RequestParam Long userId,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
		try {
			List<ExpenseDto> expenses = reportService.getDailyReport(userId, date);
			return new ResponseEntity<>(expenses, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/monthly")
	public ResponseEntity<List<ExpenseDto>> getMonthlyReport(@RequestParam Long userId, @RequestParam int month,
			@RequestParam int year) {
		try {
			List<ExpenseDto> expenses = reportService.getMonthlyReport(userId, month, year);
			return new ResponseEntity<>(expenses, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
