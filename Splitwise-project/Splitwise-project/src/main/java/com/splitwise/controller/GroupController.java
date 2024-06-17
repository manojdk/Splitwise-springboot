package com.splitwise.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.splitwise.dto.GroupDto;
import com.splitwise.service.GroupService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/groups")
@AllArgsConstructor
public class GroupController {

	private GroupService groupService;

	@PostMapping("/saveorupdate")
	public ResponseEntity<GroupDto> createGroup(@RequestBody GroupDto groupDTO) {
		try {
			GroupDto createdGroup = groupService.createGroup(groupDTO);
			return new ResponseEntity<>(createdGroup, HttpStatus.CREATED);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
