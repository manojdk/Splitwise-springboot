package com.splitwise.service;

import com.splitwise.exception.BadRequestException;
import com.splitwise.repository.ActivityRepository;
import com.splitwise.web.dto.ActivityResponse;
import com.splitwise.web.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reading the activity feed. Writing is done by {@link com.splitwise.activity.ActivityRecorder} inside the
 * transactions that make the changes.
 */
@Service
@Transactional(readOnly = true)
public class ActivityService {

    public static final int MAX_PAGE_SIZE = 100;
    /** Newest first; the id breaks ties between entries written in the same instant. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final ActivityRepository activityRepository;
    private final GroupService groupService;
    private final ExpenseService expenseService;

    public ActivityService(ActivityRepository activityRepository, GroupService groupService,
                           ExpenseService expenseService) {
        this.activityRepository = activityRepository;
        this.groupService = groupService;
        this.expenseService = expenseService;
    }

    /** Everything in the user's current groups, plus everything they were involved in. */
    public PageResponse<ActivityResponse> feedForUser(Long userId, int page, int size) {
        return PageResponse.of(activityRepository.findFeedForUser(userId, pageable(page, size)), ActivityResponse::from);
    }

    /** A group's activity; members only. */
    public PageResponse<ActivityResponse> feedForGroup(Long callerId, Long groupId, int page, int size) {
        groupService.requireMemberGroup(groupId, callerId);
        return PageResponse.of(activityRepository.findByGroupId(groupId, pageable(page, size)), ActivityResponse::from);
    }

    /** An expense's history, oldest first; same access rule as reading the expense (deleted ones included). */
    public List<ActivityResponse> expenseHistory(Long callerId, Long expenseId) {
        expenseService.get(callerId, expenseId);
        return activityRepository.findByExpenseIdOrderByIdAsc(expenseId).stream().map(ActivityResponse::from).toList();
    }

    static Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, NEWEST_FIRST);
    }
}
