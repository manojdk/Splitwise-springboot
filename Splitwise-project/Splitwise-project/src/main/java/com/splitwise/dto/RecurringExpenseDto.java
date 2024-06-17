package com.splitwise.dto;

import java.util.Date;

import lombok.Data;

@Data
public class RecurringExpenseDto {
	private Long recurringId;
	private String recurringDescription;
	private Double amount;
	private Date startDate;
	private String recurringPeriod;
	private Long categoryId;
	private Long groupId;
	private Long createdById;

}
