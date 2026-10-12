package com.splitwise.service;

import com.splitwise.domain.User;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public User getByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new NotFoundException("No user with email " + email));
    }

    /** Loads all users by id, failing with 404 naming the first missing id. */
    public Map<Long, User> getAllById(Collection<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids);
        List<User> found = userRepository.findAllById(unique);
        Map<Long, User> byId = found.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        for (Long id : unique) {
            if (!byId.containsKey(id)) {
                throw new NotFoundException("User " + id + " not found");
            }
        }
        return byId;
    }
}
