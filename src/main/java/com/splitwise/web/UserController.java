package com.splitwise.web;

import java.time.LocalDate;
import com.splitwise.query.ExpenseFilter;
import com.splitwise.domain.Category;
import org.springframework.web.bind.annotation.RequestParam;
import com.splitwise.web.dto.PageResponse;
import com.splitwise.web.dto.ActivityResponse;
import com.splitwise.service.ActivityService;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import com.splitwise.web.dto.SettleUpResponse;
import com.splitwise.web.dto.FriendBalancesResponse;
import com.splitwise.web.dto.FriendBalanceResponse;
import com.splitwise.service.FriendBalanceService;
import com.splitwise.security.CurrentUser;
import com.splitwise.service.BalanceService;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.SettlementService;
import com.splitwise.service.UserService;
import com.splitwise.web.dto.ExpenseResponse;
import com.splitwise.web.dto.SettlementResponse;
import com.splitwise.web.dto.UserBalanceResponse;
import com.splitwise.web.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Current user")
public class UserController {

    private final UserService userService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    private final BalanceService balanceService;
    private final FriendBalanceService friendBalanceService;
    private final ActivityService activityService;

    public UserController(UserService userService, ExpenseService expenseService,
                          SettlementService settlementService, BalanceService balanceService,
                          FriendBalanceService friendBalanceService, ActivityService activityService) {
        this.userService = userService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
        this.balanceService = balanceService;
        this.friendBalanceService = friendBalanceService;
        this.activityService = activityService;
    }

    @GetMapping("/activity")
    @Operation(summary = "Activity feed: your groups plus everything you were involved in, newest first")
    public PageResponse<ActivityResponse> activity(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return activityService.feedForUser(CurrentUser.id(jwt), page, size);
    }

    @GetMapping
    @Operation(summary = "The authenticated user's profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.getById(CurrentUser.id(jwt)));
    }

    @GetMapping("/expenses")
    @Operation(summary = "All expenses the user paid for or has a share in")
    public PageResponse<ExpenseResponse> myExpenses(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) Category category,
                                                @RequestParam(required = false) Long paidBy,
                                                @RequestParam(required = false) String q) {
        return expenseService.listForUser(CurrentUser.id(jwt), new ExpenseFilter(from, to, category, paidBy, q),
                page, size);
    }

    @GetMapping("/settlements")
    @Operation(summary = "All settlements the user paid or received")
    public PageResponse<SettlementResponse> mySettlements(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) LocalDate from,
                                                      @RequestParam(required = false) LocalDate to) {
        return settlementService.listForUser(CurrentUser.id(jwt), from, to, page, size);
    }

    @GetMapping("/balance")
    @Operation(summary = "Overall net balance across all groups and non-group expenses (positive = owed to you)")
    public UserBalanceResponse myBalance(@AuthenticationPrincipal Jwt jwt) {
        return balanceService.overallBalance(CurrentUser.id(jwt));
    }

    @GetMapping("/friends")
    @Operation(summary = "Everyone you have an open balance with, per group, plus totals (positive = owed to you)")
    public FriendBalancesResponse friends(@AuthenticationPrincipal Jwt jwt) {
        return friendBalanceService.balances(CurrentUser.id(jwt));
    }

    @GetMapping("/friends/{friendId}")
    @Operation(summary = "Your balance with one friend, per group")
    public FriendBalanceResponse friend(@AuthenticationPrincipal Jwt jwt, @PathVariable Long friendId) {
        return friendBalanceService.balanceWith(CurrentUser.id(jwt), friendId);
    }

    @PostMapping("/friends/{friendId}/settle-up")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record the payments that clear every open balance with a friend (one per group)")
    public SettleUpResponse settleUp(@AuthenticationPrincipal Jwt jwt, @PathVariable Long friendId) {
        return friendBalanceService.settleUp(CurrentUser.id(jwt), friendId);
    }
}
