package com.splitwise.service;

import com.splitwise.domain.Expense;
import com.splitwise.domain.Group;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.SplitType;
import com.splitwise.domain.User;
import com.splitwise.exception.ForbiddenException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.web.dto.BalanceResponse;
import com.splitwise.web.dto.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final long GROUP_ID = 10L;

    @Mock
    private GroupService groupService;
    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private SettlementRepository settlementRepository;

    private BalanceService service;
    private User alice;
    private User bob;
    private User carol;
    private Group group;

    @BeforeEach
    void setUp() {
        service = new BalanceService(groupService, expenseRepository, settlementRepository);
        alice = user(1L, "Alice");
        bob = user(2L, "Bob");
        carol = user(3L, "Carol");
        group = new Group("Trip", alice);
        ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.addMember(bob);
        group.addMember(carol);
    }

    @Test
    void newGroupHasZeroBalanceForEveryMember() {
        givenGroupData(List.of(), List.of());

        List<BalanceResponse> balances = service.groupBalances(alice.getId(), GROUP_ID);

        assertThat(balances).extracting(BalanceResponse::userId).containsExactly(1L, 2L, 3L);
        assertThat(balances).allSatisfy(b -> assertThat(b.netBalance()).isEqualByComparingTo("0"));
        assertThat(service.simplifiedDebts(alice.getId(), GROUP_ID)).isEmpty();
    }

    @Test
    void payerIsCreditedAndParticipantsAreDebited() {
        // Alice pays 300 split equally between all three -> Alice +200, Bob -100, Carol -100
        givenGroupData(List.of(expense(alice, "300", Map.of(alice, "100", bob, "100", carol, "100"))), List.of());

        assertThat(netById(service.groupBalances(alice.getId(), GROUP_ID)))
                .containsExactly(Map.entry(1L, bd("200.00")), Map.entry(2L, bd("-100.00")),
                        Map.entry(3L, bd("-100.00")));
    }

    @Test
    void multipleExpensesNetAgainstEachOther() {
        // Alice pays 300 (100 each); Bob pays 60 split Alice 30 / Carol 30
        // Alice: +300 -100 -30 = 170, Bob: -100 +60 = -40, Carol: -100 -30 = -130
        givenGroupData(List.of(
                expense(alice, "300", Map.of(alice, "100", bob, "100", carol, "100")),
                expense(bob, "60", Map.of(alice, "30", carol, "30"))), List.of());

        assertThat(netById(service.groupBalances(alice.getId(), GROUP_ID)))
                .containsExactly(Map.entry(1L, bd("170.00")), Map.entry(2L, bd("-40.00")),
                        Map.entry(3L, bd("-130.00")));

        List<TransferResponse> debts = service.simplifiedDebts(alice.getId(), GROUP_ID);
        assertThat(debts).extracting(TransferResponse::fromName, TransferResponse::toName, TransferResponse::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Carol", "Alice", bd("130.00")),
                        org.assertj.core.groups.Tuple.tuple("Bob", "Alice", bd("40.00")));
    }

    @Test
    void settlementReducesDebt() {
        givenGroupData(
                List.of(expense(alice, "300", Map.of(alice, "100", bob, "100", carol, "100"))),
                List.of(settlement(bob, alice, "100")));

        assertThat(netById(service.groupBalances(alice.getId(), GROUP_ID)))
                .containsExactly(Map.entry(1L, bd("100.00")), Map.entry(2L, bd("0.00")),
                        Map.entry(3L, bd("-100.00")));
        assertThat(service.simplifiedDebts(alice.getId(), GROUP_ID))
                .extracting(TransferResponse::fromUserId, TransferResponse::toUserId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3L, 1L));
    }

    @Test
    void fullySettledGroupIsAllZero() {
        givenGroupData(
                List.of(expense(alice, "90", Map.of(alice, "30", bob, "30", carol, "30"))),
                List.of(settlement(bob, alice, "30"), settlement(carol, alice, "30")));

        assertThat(service.groupBalances(alice.getId(), GROUP_ID))
                .allSatisfy(b -> assertThat(b.netBalance()).isEqualByComparingTo("0"));
        assertThat(service.simplifiedDebts(alice.getId(), GROUP_ID)).isEmpty();
    }

    @Test
    void balancesAlwaysSumToZero() {
        givenGroupData(List.of(
                        expense(alice, "100", Map.of(alice, "33.34", bob, "33.33", carol, "33.33")),
                        expense(carol, "45.55", Map.of(bob, "45.55"))),
                List.of(settlement(bob, carol, "10.01")));

        BigDecimal sum = service.groupBalances(alice.getId(), GROUP_ID).stream()
                .map(BalanceResponse::netBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0");
    }

    @Test
    void overallBalanceCombinesEverythingInvolvingTheUser() {
        when(expenseRepository.findInvolvingUser(2L)).thenReturn(List.of(
                expense(alice, "300", Map.of(alice, "100", bob, "100", carol, "100")),
                expense(bob, "50", Map.of(bob, "25", carol, "25"))));
        when(settlementRepository.findInvolvingUser(2L)).thenReturn(List.of(settlement(bob, alice, "40")));

        // Bob: -100 (share) + 50 (paid) - 25 (own share) + 40 (paid Alice) = -35
        assertThat(service.overallBalance(2L).netBalance()).isEqualByComparingTo("-35");
    }

    @Test
    void nonMemberIsRejected() {
        when(groupService.requireMemberGroup(GROUP_ID, 99L)).thenThrow(new ForbiddenException("no"));

        assertThatThrownBy(() -> service.groupBalances(99L, GROUP_ID)).isInstanceOf(ForbiddenException.class);
    }

    // ---------------------------------------------------------------- helpers

    private void givenGroupData(List<Expense> expenses, List<Settlement> settlements) {
        when(groupService.requireMemberGroup(GROUP_ID, alice.getId())).thenReturn(group);
        when(expenseRepository.findByGroupIdAndDeletedAtIsNullOrderByExpenseDateDescIdDesc(GROUP_ID)).thenReturn(expenses);
        when(settlementRepository.findByGroupIdAndDeletedAtIsNullOrderBySettledOnDescIdDesc(GROUP_ID)).thenReturn(settlements);
    }

    private Expense expense(User payer, String amount, Map<User, String> shares) {
        Expense e = new Expense("test", bd(amount), "INR", payer, group, LocalDate.now(), SplitType.EXACT, payer);
        shares.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.getId().compareTo(b.getId())))
                .forEach(s -> e.addShare(s.getKey(), bd(s.getValue())));
        return e;
    }

    private Settlement settlement(User from, User to, String amount) {
        return new Settlement(from, to, bd(amount), "INR", group, LocalDate.now(), null, from);
    }

    private static Map<Long, BigDecimal> netById(List<BalanceResponse> balances) {
        Map<Long, BigDecimal> map = new java.util.LinkedHashMap<>();
        balances.forEach(b -> map.put(b.userId(), b.netBalance()));
        return map;
    }

    private static User user(long id, String name) {
        User u = new User(name, name.toLowerCase() + "@example.com", "hash");
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
