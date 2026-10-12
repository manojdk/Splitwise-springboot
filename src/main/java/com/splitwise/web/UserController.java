package com.splitwise.web;

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

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Current user")
public class UserController {

    private final UserService userService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    private final BalanceService balanceService;

    public UserController(UserService userService, ExpenseService expenseService,
                          SettlementService settlementService, BalanceService balanceService) {
        this.userService = userService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
        this.balanceService = balanceService;
    }

    @GetMapping
    @Operation(summary = "The authenticated user's profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.getById(CurrentUser.id(jwt)));
    }

    @GetMapping("/expenses")
    @Operation(summary = "All expenses the user paid for or has a share in")
    public List<ExpenseResponse> myExpenses(@AuthenticationPrincipal Jwt jwt) {
        return expenseService.listForUser(CurrentUser.id(jwt));
    }

    @GetMapping("/settlements")
    @Operation(summary = "All settlements the user paid or received")
    public List<SettlementResponse> mySettlements(@AuthenticationPrincipal Jwt jwt) {
        return settlementService.listForUser(CurrentUser.id(jwt));
    }

    @GetMapping("/balance")
    @Operation(summary = "Overall net balance across all groups and non-group expenses (positive = owed to you)")
    public UserBalanceResponse myBalance(@AuthenticationPrincipal Jwt jwt) {
        return balanceService.overallBalance(CurrentUser.id(jwt));
    }
}
