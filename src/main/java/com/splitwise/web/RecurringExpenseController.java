package com.splitwise.web;

import com.splitwise.security.CurrentUser;
import com.splitwise.service.RecurringExpenseService;
import com.splitwise.web.dto.CreateRecurringExpenseRequest;
import com.splitwise.web.dto.RecurringExpenseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recurring-expenses")
@Tag(name = "Recurring expenses")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Set up an expense that repeats weekly, monthly or yearly (first one today or later)")
    public RecurringExpenseResponse create(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody CreateRecurringExpenseRequest request) {
        return recurringExpenseService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @Operation(summary = "Recurring expenses in my groups and the non-group ones I am part of")
    public List<RecurringExpenseResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return recurringExpenseService.listForUser(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public RecurringExpenseResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return recurringExpenseService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Start a stopped or paused one again, from its next date on or after today (missed dates"
            + " are skipped)")
    public RecurringExpenseResponse resume(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return recurringExpenseService.resume(CurrentUser.id(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Stop future occurrences (expenses already created stay)")
    public void stop(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        recurringExpenseService.stop(CurrentUser.id(jwt), id);
    }
}
