package com.splitwise.web;

import com.splitwise.security.CurrentUser;
import com.splitwise.service.ExpenseService;
import com.splitwise.web.dto.CreateExpenseRequest;
import com.splitwise.web.dto.ExpenseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/expenses")
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an expense split EQUAL, EXACT or PERCENTAGE")
    public ExpenseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.addExpense(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long expenseId) {
        return expenseService.get(CurrentUser.id(jwt), expenseId);
    }
}
