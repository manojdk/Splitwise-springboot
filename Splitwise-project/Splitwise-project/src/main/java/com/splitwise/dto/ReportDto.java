package com.splitwise.dto;

import java.util.List;

import lombok.Data;

@Data
public class ReportDto {
	private List<ExpenseDto> expenses;

}
