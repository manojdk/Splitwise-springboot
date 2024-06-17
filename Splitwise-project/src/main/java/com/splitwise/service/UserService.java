package com.splitwise.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.splitwise.dto.UserDto;
import com.splitwise.entity.User;

@Service
public interface UserService {

	public UserDto getUserById(Long userId) throws Exception;

	public List<UserDto> getAllUsers();

	public UserDto getUserByEmail(String email);

	public UserDto saveUser(UserDto userDto);

	public User getUserEntityById(Long userId);

}
