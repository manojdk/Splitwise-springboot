package com.splitwise.dto;

import java.util.Date;

import lombok.Data;

@Data
public class RecurringExpenseDto {
    private Long id;
    private String description;
    private Double amount;
    private Date startDate;
    private String recurrencePeriod;
    private Long categoryId;
    private Long groupId;
    private Long createdById;

}
