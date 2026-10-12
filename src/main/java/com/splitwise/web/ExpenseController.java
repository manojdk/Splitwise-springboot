package com.splitwise.web;

import java.util.List;
import com.splitwise.web.dto.ActivityResponse;
import com.splitwise.service.ActivityService;
import com.splitwise.security.CurrentUser;
import com.splitwise.service.ExpenseService;
import com.splitwise.web.dto.CreateExpenseRequest;
import com.splitwise.web.dto.ExpenseResponse;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.splitwise.web.dto.UpdateExpenseRequest;
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
    private final ActivityService activityService;

    public ExpenseController(ExpenseService expenseService, ActivityService activityService) {
        this.expenseService = expenseService;
        this.activityService = activityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an expense split EQUAL, EXACT or PERCENTAGE")
    public ExpenseResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.addExpense(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{expenseId}")
    @Operation(summary = "Get an expense, including a deleted one (flagged deleted=true)")
    public ExpenseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long expenseId) {
        return expenseService.get(CurrentUser.id(jwt), expenseId);
    }

    @GetMapping("/{expenseId}/activity")
    @Operation(summary = "The expense's history, oldest first: who added, edited (with each change) or deleted it")
    public List<ActivityResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long expenseId) {
        return activityService.expenseHistory(CurrentUser.id(jwt), expenseId);
    }

    @PutMapping("/{expenseId}")
    @Operation(summary = "Edit an expense; send the version you read, a stale version gives 409")
    public ExpenseResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long expenseId,
                                  @Valid @RequestBody UpdateExpenseRequest request) {
        return expenseService.updateExpense(CurrentUser.id(jwt), expenseId, request);
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an expense (soft delete, can be restored)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long expenseId) {
        expenseService.deleteExpense(CurrentUser.id(jwt), expenseId);
    }

    @PostMapping("/{expenseId}/restore")
    @Operation(summary = "Restore a deleted expense")
    public ExpenseResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable Long expenseId) {
        return expenseService.restoreExpense(CurrentUser.id(jwt), expenseId);
    }
}
