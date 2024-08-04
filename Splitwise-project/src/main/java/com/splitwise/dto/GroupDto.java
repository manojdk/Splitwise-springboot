package com.splitwise.dto;

import com.splitwise.entity.User;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class GroupDto {

    private Long groupId;
    private String groupName;
    private String currency;
    private Set<Long> memberIds = new HashSet<>();

}
