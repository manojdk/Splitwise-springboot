package com.splitwise.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ExpenseCreationDto {
	private Long userId;
    private String description;
    private Double amount;
    private List<Long> memberIds;
    private Map<Long, Double> shares;
}
