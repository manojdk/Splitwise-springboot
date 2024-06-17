package com.splitwise.service;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.splitwise.dto.ExpenseDto;

@Service
public interface ReportService {
	public List<ExpenseDto> getDailyReport(long userId, Date date);

	public List<ExpenseDto> getMonthlyReport(Long userId, int month, int year);

}
