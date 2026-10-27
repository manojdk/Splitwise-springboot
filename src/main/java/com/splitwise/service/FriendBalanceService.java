package com.splitwise.service;

import java.util.TreeMap;
import com.splitwise.web.dto.CurrencyAmount;
import com.splitwise.balance.FriendBalanceCalculator;
import com.splitwise.balance.FriendBalanceCalculator.Entry;
import com.splitwise.balance.FriendBalanceCalculator.Item;
import com.splitwise.domain.Expense;
import com.splitwise.domain.ExpenseShare;
import com.splitwise.domain.Group;
import com.splitwise.domain.Money;
import com.splitwise.domain.Settlement;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ConflictException;
import com.splitwise.exception.NotFoundException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import com.splitwise.web.dto.CreateSettlementRequest;
import com.splitwise.web.dto.FriendBalanceResponse;
import com.splitwise.web.dto.FriendBalancesResponse;
import com.splitwise.web.dto.SettleUpResponse;
import com.splitwise.web.dto.SettlementResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Balances between the current user and each friend, and settling up with a friend.
 *
 * <p>The arithmetic lives in {@link FriendBalanceCalculator} (pure, property-tested); this service only loads
 * the data and turns the result into responses. Only groups the user currently belongs to count: a user can
 * only have left a group with a zero balance, so a former group never holds an open balance.
 */
@Service
@Transactional(readOnly = true)
public class FriendBalanceService {

    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupService groupService;
    private final UserService userService;
    private final SettlementService settlementService;

    public FriendBalanceService(GroupRepository groupRepository, ExpenseRepository expenseRepository,
                                SettlementRepository settlementRepository, GroupService groupService,
                                UserService userService, SettlementService settlementService) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
        this.userService = userService;
        this.settlementService = settlementService;
    }

    /**
     * Everyone the user has an open balance with (largest amount first) plus totals per currency. Amounts in
     * different currencies are never added together.
     */
    public FriendBalancesResponse balances(Long userId) {
        Ledger ledger = loadLedger(userId);
        Map<Long, List<Item>> byFriend = new LinkedHashMap<>();
        FriendBalanceCalculator.forUser(userId, ledger.entries())
                .forEach(item -> byFriend.computeIfAbsent(item.friendId(), f -> new ArrayList<>()).add(item));

        List<FriendBalanceResponse> friends = new ArrayList<>();
        byFriend.forEach((friendId, items) -> friends.add(toResponse(friendId, items, ledger)));
        friends.sort(Comparator.comparing(FriendBalanceService::largestAmount).reversed()
                .thenComparing(FriendBalanceResponse::userId));

        Map<String, BigDecimal[]> sums = new TreeMap<>(); // currency -> [owed to you, you owe]
        for (FriendBalanceResponse friend : friends) {
            for (CurrencyAmount balance : friend.balances()) {
                BigDecimal[] sum = sums.computeIfAbsent(balance.currency(), c -> new BigDecimal[] {Money.ZERO, Money.ZERO});
                if (balance.amount().signum() > 0) {
                    sum[0] = sum[0].add(balance.amount());
                } else {
                    sum[1] = sum[1].add(balance.amount().negate());
                }
            }
        }
        List<FriendBalancesResponse.Totals> totals = new ArrayList<>();
        sums.forEach((currency, sum) -> totals.add(
                new FriendBalancesResponse.Totals(currency, sum[0], sum[1], sum[0].subtract(sum[1]))));
        return new FriendBalancesResponse(totals, friends);
    }

    private static BigDecimal largestAmount(FriendBalanceResponse friend) {
        return friend.breakdown().stream().map(i -> i.amount().abs()).max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    /** The balance with one friend (zero and an empty breakdown when settled up). */
    public FriendBalanceResponse balanceWith(Long userId, Long friendId) {
        Ledger ledger = loadLedger(userId);
        requireFriend(userId, friendId, ledger);
        return toResponse(friendId, itemsWith(userId, friendId, ledger), ledger);
    }

    /**
     * Records the payments that clear every open balance with the friend: one settlement per group (in that
     * group, so its balances stay right) and one for non-group expenses, each in the direction of that
     * bucket's debt. All or nothing.
     *
     * <p>Concurrency: both users' rows are locked first (the lock a non-group settlement also takes), then the
     * shared groups, so two settle-ups for the same pair run one after the other and the second finds nothing
     * left to settle (409) instead of paying twice.
     */
    @Transactional
    public SettleUpResponse settleUp(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new BadRequestException("You cannot settle up with yourself");
        }
        // Lock first, read after: ids only until every lock is held, so nothing is read stale
        userService.lockAll(Set.of(userId, friendId));
        groupRepository.findIdsForMember(userId).stream()
                .sorted()
                .forEach(groupId -> groupService.lockMemberGroup(groupId, userId));

        Ledger ledger = loadLedger(userId);
        requireFriend(userId, friendId, ledger);
        List<Item> items = itemsWith(userId, friendId, ledger);
        if (items.isEmpty()) {
            throw new ConflictException("You and " + userService.getById(friendId).getName() + " are already settled up");
        }

        List<SettlementResponse> recorded = new ArrayList<>();
        for (Item item : items) {
            boolean friendPays = item.amount().signum() > 0;
            recorded.add(settlementService.createSettleUp(userId, new CreateSettlementRequest(
                    friendPays ? friendId : userId,
                    friendPays ? userId : friendId,
                    item.amount().abs(),
                    item.groupId(),
                    null,
                    "Settle up",
                    item.currency())));
        }
        Ledger after = loadLedger(userId);
        return new SettleUpResponse(recorded, toResponse(friendId, itemsWith(userId, friendId, after), after));
    }

    // ------------------------------------------------------------------ loading

    /** Everything needed for one user's friend balances: entries, names, group names, shared groups. */
    private record Ledger(List<Entry> entries, Map<Long, String> userNames, Map<Long, String> groupNames,
                          Map<Long, Group> groups) {
    }

    private Ledger loadLedger(Long userId) {
        Map<Long, Group> groups = new LinkedHashMap<>();
        groupRepository.findAllForMember(userId).forEach(g -> groups.put(g.getId(), g));

        List<Expense> expenses = new ArrayList<>();
        List<Settlement> settlements = new ArrayList<>();
        if (!groups.isEmpty()) {
            expenses.addAll(expenseRepository.findByGroupIdInAndDeletedAtIsNull(groups.keySet()));
            settlements.addAll(settlementRepository.findByGroupIdInAndDeletedAtIsNull(groups.keySet()));
        }
        expenseRepository.findInvolvingUser(userId).stream().filter(e -> e.getGroup() == null).forEach(expenses::add);
        settlementRepository.findInvolvingUser(userId).stream().filter(s -> s.getGroup() == null)
                .forEach(settlements::add);

        Map<Long, String> names = new HashMap<>();
        Map<Long, String> groupNames = new HashMap<>();
        groups.values().forEach(g -> {
            groupNames.put(g.getId(), g.getName());
            g.getMembers().forEach(m -> names.put(m.getId(), m.getName()));
        });
        List<Entry> entries = new ArrayList<>();
        for (Expense e : expenses) {
            Map<Long, BigDecimal> debts = new LinkedHashMap<>();
            for (ExpenseShare share : e.getShares()) {
                debts.put(share.getUser().getId(), share.getAmount());
                names.put(share.getUser().getId(), share.getUser().getName());
            }
            names.put(e.getPaidBy().getId(), e.getPaidBy().getName());
            entries.add(new Entry(groupId(e.getGroup()), e.getCurrency(), e.getPaidBy().getId(), debts));
        }
        for (Settlement s : settlements) {
            names.put(s.getPayer().getId(), s.getPayer().getName());
            names.put(s.getPayee().getId(), s.getPayee().getName());
            entries.add(new Entry(groupId(s.getGroup()), s.getCurrency(), s.getPayer().getId(),
                    Map.of(s.getPayee().getId(), s.getAmount())));
        }
        return new Ledger(entries, names, groupNames, groups);
    }

    private static Long groupId(Group group) {
        return group == null ? null : group.getId();
    }

    private static List<Item> itemsWith(Long userId, Long friendId, Ledger ledger) {
        return FriendBalanceCalculator.forUser(userId, ledger.entries()).stream()
                .filter(item -> item.friendId() == friendId)
                .toList();
    }

    /**
     * A "friend" is someone you share a group with, or have a non-group expense or settlement with. Anyone else
     * is a 404, so the endpoint cannot be used to probe for user ids.
     */
    private void requireFriend(Long userId, Long friendId, Ledger ledger) {
        if (userId.equals(friendId)) {
            throw new BadRequestException("That is you");
        }
        boolean sharesGroup = ledger.groups().values().stream().anyMatch(g -> g.hasMember(friendId));
        boolean sharesHistory = ledger.userNames().containsKey(friendId);
        if (!sharesGroup && !sharesHistory) {
            throw new NotFoundException("No shared groups or expenses with user " + friendId);
        }
    }

    private FriendBalanceResponse toResponse(Long friendId, List<Item> items, Ledger ledger) {
        Map<String, BigDecimal> totals = new TreeMap<>();
        List<FriendBalanceResponse.Item> breakdown = new ArrayList<>();
        for (Item item : items) {
            totals.merge(item.currency(), item.amount(), BigDecimal::add);
            breakdown.add(new FriendBalanceResponse.Item(item.groupId(),
                    item.groupId() == null ? null : ledger.groupNames().get(item.groupId()), item.currency(),
                    item.amount()));
        }
        List<CurrencyAmount> balances = new ArrayList<>();
        totals.forEach((currency, amount) -> {
            if (amount.signum() != 0) {
                balances.add(new CurrencyAmount(currency, amount));
            }
        });
        String name = ledger.userNames().containsKey(friendId)
                ? ledger.userNames().get(friendId)
                : userService.getById(friendId).getName();
        return new FriendBalanceResponse(friendId, name, balances, breakdown);
    }
}
