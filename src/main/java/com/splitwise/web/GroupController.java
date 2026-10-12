package com.splitwise.web;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import com.splitwise.web.dto.GroupReportResponse;
import com.splitwise.service.ReportService;
import org.springframework.web.bind.annotation.RequestParam;
import com.splitwise.web.dto.PageResponse;
import com.splitwise.web.dto.ActivityResponse;
import com.splitwise.service.ActivityService;
import com.splitwise.security.CurrentUser;
import com.splitwise.service.BalanceService;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.GroupService;
import com.splitwise.service.SettlementService;
import com.splitwise.web.dto.AddMemberRequest;
import com.splitwise.web.dto.BalanceResponse;
import com.splitwise.web.dto.CreateGroupRequest;
import com.splitwise.web.dto.ExpenseResponse;
import com.splitwise.web.dto.GroupResponse;
import com.splitwise.web.dto.SettlementResponse;
import com.splitwise.web.dto.TransferResponse;
import com.splitwise.web.dto.UpdateGroupRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Group management plus the group-scoped read views. Every endpoint requires group membership. */
@RestController
@RequestMapping("/api/groups")
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final SettlementService settlementService;
    private final BalanceService balanceService;
    private final ActivityService activityService;
    private final ReportService reportService;

    public GroupController(GroupService groupService, ExpenseService expenseService,
                           SettlementService settlementService, BalanceService balanceService,
                           ActivityService activityService, ReportService reportService) {
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.settlementService = settlementService;
        this.balanceService = balanceService;
        this.activityService = activityService;
        this.reportService = reportService;
    }

    @GetMapping("/{groupId}/report")
    @Operation(summary = "Spending summary: total, per category and per member (optional from/to, inclusive)")
    public GroupReportResponse report(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId,
                                      @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to) {
        return reportService.groupReport(CurrentUser.id(jwt), groupId, from, to);
    }

    @GetMapping(value = "/{groupId}/expenses.csv", produces = "text/csv")
    @Operation(summary = "The group's expenses as CSV, one column per person (optional from/to, inclusive)")
    public ResponseEntity<String> csv(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId,
                                      @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to) {
        String body = reportService.groupCsv(CurrentUser.id(jwt), groupId, from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("group-" + groupId + "-expenses.csv").build().toString())
                .body(body);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group; the caller becomes a member")
    public GroupResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping
    @Operation(summary = "Groups the caller belongs to")
    public List<GroupResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return groupService.listForUser(CurrentUser.id(jwt));
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        return groupService.get(CurrentUser.id(jwt), groupId);
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Rename a group")
    public GroupResponse rename(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId,
                                @Valid @RequestBody UpdateGroupRequest request) {
        return groupService.rename(CurrentUser.id(jwt), groupId, request);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an empty group (creator only)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        groupService.delete(CurrentUser.id(jwt), groupId);
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "Add a registered user to the group by email")
    public GroupResponse addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId,
                                   @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(CurrentUser.id(jwt), groupId, request);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "Remove a member (creator only) or leave (your own id); the balance in the group must be zero")
    public GroupResponse removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId,
                                      @PathVariable Long userId) {
        return groupService.removeMember(CurrentUser.id(jwt), groupId, userId);
    }

    @GetMapping("/{groupId}/activity")
    @Operation(summary = "The group's activity, newest first (members only)")
    public PageResponse<ActivityResponse> activity(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return activityService.feedForGroup(CurrentUser.id(jwt), groupId, page, size);
    }

    @GetMapping("/{groupId}/expenses")
    public List<ExpenseResponse> expenses(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        return expenseService.listForGroup(CurrentUser.id(jwt), groupId);
    }

    @GetMapping("/{groupId}/settlements")
    public List<SettlementResponse> settlements(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        return settlementService.listForGroup(CurrentUser.id(jwt), groupId);
    }

    @GetMapping("/{groupId}/balances")
    @Operation(summary = "Net balance per member (positive = is owed, negative = owes)")
    public List<BalanceResponse> balances(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        return balanceService.groupBalances(CurrentUser.id(jwt), groupId);
    }

    @GetMapping("/{groupId}/debts/simplified")
    @Operation(summary = "Suggested payments that settle the whole group with few transfers")
    public List<TransferResponse> simplifiedDebts(@AuthenticationPrincipal Jwt jwt, @PathVariable Long groupId) {
        return balanceService.simplifiedDebts(CurrentUser.id(jwt), groupId);
    }
}
