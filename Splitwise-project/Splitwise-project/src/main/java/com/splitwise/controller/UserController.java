package com.splitwise.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.splitwise.dto.UserDto;
import com.splitwise.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

	@Autowired
	private UserService userService;

	@GetMapping("/list/user/{id}")
	public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
		try {
			UserDto user = userService.getUserById(id);
			return new ResponseEntity<>(user, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
		}
	}

	@GetMapping("/list")
	public ResponseEntity<List<UserDto>> getAllUsers() {
		try {
			List<UserDto> users = userService.getAllUsers();
			return new ResponseEntity<>(users, HttpStatus.OK);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@PostMapping("/saveorupdate")
	public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto) {
		try {
			UserDto createdUser = userService.saveUser(userDto);
			return new ResponseEntity<>(createdUser, HttpStatus.CREATED);
		} catch (Exception e) {
			return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
