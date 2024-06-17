package com.splitwise.service.serviceImp;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.splitwise.dto.ExpenseDto;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.ReportService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ReportServiceImpl implements ReportService {

	private ExpenseService expenseService;

	public List<ExpenseDto> getDailyReport(long userId, Date date) {
		try {
			Calendar start = Calendar.getInstance();
			start.setTime(date);
			start.set(Calendar.HOUR_OF_DAY, 0);
			start.set(Calendar.MINUTE, 0);
			start.set(Calendar.SECOND, 0);
			start.set(Calendar.MILLISECOND, 0);

			Calendar end = Calendar.getInstance();
			end.setTime(date);
			end.set(Calendar.HOUR_OF_DAY, 23);
			end.set(Calendar.MINUTE, 59);
			end.set(Calendar.SECOND, 59);
			end.set(Calendar.MILLISECOND, 999);
			return expenseService.getExpensesByUserAndDate(userId, start.getTime(), end.getTime());
		} catch (Exception e) {
			throw new RuntimeException("Error fetching daily report", e);
		}

	}

	public List<ExpenseDto> getMonthlyReport(Long userId, int month, int year) {
		try {
			Calendar start = Calendar.getInstance();
			start.set(Calendar.MONTH, month);
			start.set(Calendar.YEAR, year);
			start.set(Calendar.DAY_OF_MONTH, 1);
			start.set(Calendar.HOUR_OF_DAY, 0);
			start.set(Calendar.MINUTE, 0);
			start.set(Calendar.SECOND, 0);
			start.set(Calendar.MILLISECOND, 0);

			Calendar end = Calendar.getInstance();
			end.set(Calendar.MONTH, month);
			end.set(Calendar.YEAR, year);
			end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
			end.set(Calendar.HOUR_OF_DAY, 23);
			end.set(Calendar.MINUTE, 59);
			end.set(Calendar.SECOND, 59);
			end.set(Calendar.MILLISECOND, 999);

			return expenseService.getExpensesByUserAndDate(userId, start.getTime(), end.getTime());
		} catch (Exception e) {
			throw new RuntimeException("Error fetching monthly report", e);
		}
	}
}
