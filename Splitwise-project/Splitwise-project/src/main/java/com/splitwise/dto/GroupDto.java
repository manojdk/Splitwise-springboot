package com.splitwise.dto;

import java.util.Set;

import lombok.Data;

@Data
public class GroupDto {
	private Long groupId;
	private String groupName;
	private String currency;
	private Set<Long> memberIds;

}
