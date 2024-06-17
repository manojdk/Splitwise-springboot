package com.splitwise.service.serviceImp;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.splitwise.dto.UserDto;
import com.splitwise.entity.User;
import com.splitwise.repository.UserRepository;
import com.splitwise.service.UserService;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {

	private UserRepository userRepository;

	public UserDto getUserById(Long userId) {
		try {
			User user = userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));
			return toDto(user);
		} catch (Exception e) {
			throw new RuntimeException("Error fetching user by ID", e);
		}
	}

	public User getUserEntityById(Long userId) {
		try {
			return userRepository.findById(userId).orElseThrow(() -> new Exception("User not found"));
		} catch (Exception e) {
			throw new RuntimeException("Error fetching user by ID", e);
		}
	}

	public List<UserDto> getAllUsers() {
		try {
			List<User> users = userRepository.findAll();
			return users.stream().map(this::toDto).collect(Collectors.toList());
		} catch (Exception e) {
			throw new RuntimeException("Error fetching all users", e);
		}
	}

	public UserDto getUserByEmail(String email) {
		try {
			User user = userRepository.findByEmail(email);
			return toDto(user);
		} catch (Exception e) {
			throw new RuntimeException("Error fetching user by email", e);
		}
	}

	public UserDto saveUser(UserDto userDto) {
		try {
			User user = toEntity(userDto);
			User savedUser = userRepository.save(user);
			return toDto(savedUser);
		} catch (Exception e) {
			throw new RuntimeException("Error saving user", e);
		}
	}

	private UserDto toDto(User user) {
		UserDto userDTO = new UserDto();
		userDTO.setUserId(user.getUserId());
		userDTO.setUserName(user.getUserName());
		userDTO.setUserEmail(user.getUserEmail());
		return userDTO;
	}

	private User toEntity(UserDto userDTO) {
		User user = new User();
		user.setUserId(userDTO.getUserId());
		user.setUserName(userDTO.getUserName());
		user.setUserEmail(userDTO.getUserEmail());
		return user;
	}
}
