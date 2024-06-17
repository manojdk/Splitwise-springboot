package com.splitwise.dto;

import java.util.Date;

import lombok.Data;

@Data
public class ExpenseDto {
	private Long expenseId;

	private String expenseDescription;

	private Double expenseAmount;

	private Date expenseDate;

}
