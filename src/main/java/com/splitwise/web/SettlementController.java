package com.splitwise.web;

import com.splitwise.security.CurrentUser;
import com.splitwise.service.SettlementService;
import com.splitwise.web.dto.CreateSettlementRequest;
import com.splitwise.web.dto.SettlementResponse;
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

@RestController
@RequestMapping("/api/settlements")
@Tag(name = "Settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a payment from one user to another (settle up)")
    public SettlementResponse create(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody CreateSettlementRequest request) {
        return settlementService.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "Get a settlement, including a deleted one (flagged deleted=true)")
    public SettlementResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long settlementId) {
        return settlementService.get(CurrentUser.id(jwt), settlementId);
    }

    @DeleteMapping("/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Undo a settlement recorded by mistake (soft delete, can be restored)")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long settlementId) {
        settlementService.delete(CurrentUser.id(jwt), settlementId);
    }

    @PostMapping("/{settlementId}/restore")
    @Operation(summary = "Restore a deleted settlement")
    public SettlementResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable Long settlementId) {
        return settlementService.restore(CurrentUser.id(jwt), settlementId);
    }
}
