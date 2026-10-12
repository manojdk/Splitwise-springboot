package com.splitwise.activity;

import com.splitwise.domain.RecurringExpense;
import com.splitwise.domain.Activity;
import com.splitwise.domain.ActivityType;
import com.splitwise.domain.Expense;
import com.splitwise.domain.Group;
import com.splitwise.domain.Settlement;
import com.splitwise.domain.User;
import com.splitwise.repository.ActivityRepository;
import com.splitwise.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes activity entries. Every method requires an existing transaction ({@link Propagation#MANDATORY}): the
 * entry is saved together with the change it describes, or not at all, so the feed can never show something
 * that was rolled back or miss something that happened.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ActivityRecorder {

    private final ActivityRepository activityRepository;
    private final UserService userService;
    private final Clock clock;

    public ActivityRecorder(ActivityRepository activityRepository, UserService userService, Clock clock) {
        this.activityRepository = activityRepository;
        this.userService = userService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ expenses

    public void expenseAdded(Long actorId, Expense e) {
        User actor = userService.getById(actorId);
        save(ActivityType.EXPENSE_ADDED, actor, e, actor.getName() + " added \"" + e.getDescription() + "\" ("
                + money(e.getCurrency(), e.getAmount()) + ")", null);
    }

    /** Records the edit with one line per changed field or share; {@code before} is taken before the change. */
    public void expenseUpdated(Long actorId, Expense e, ExpenseSnapshot before) {
        User actor = userService.getById(actorId);
        List<String> changes = ExpenseDiff.changes(before, ExpenseSnapshot.of(e));
        save(ActivityType.EXPENSE_UPDATED, actor, e, actor.getName() + " updated \"" + e.getDescription() + "\"",
                changes.isEmpty() ? "No visible changes" : String.join("\n", changes));
    }

    public void expenseDeleted(Long actorId, Expense e) {
        User actor = userService.getById(actorId);
        save(ActivityType.EXPENSE_DELETED, actor, e, actor.getName() + " deleted \"" + e.getDescription() + "\" ("
                + money(e.getCurrency(), e.getAmount()) + ")", null);
    }

    public void expenseRestored(Long actorId, Expense e) {
        User actor = userService.getById(actorId);
        save(ActivityType.EXPENSE_RESTORED, actor, e, actor.getName() + " restored \"" + e.getDescription() + "\"",
                null);
    }

    private void save(ActivityType type, User actor, Expense e, String summary, String details) {
        activityRepository.save(new Activity(type, actor, groupId(e.getGroup()), e.getId(), null, summary, details,
                now(), e.involvedUserIds()));
    }

    // ------------------------------------------------------------------ settlements

    public void settlementAdded(Long actorId, Settlement s) {
        saveSettlement(ActivityType.SETTLEMENT_ADDED, actorId, s, payment(s));
    }

    public void settlementDeleted(Long actorId, Settlement s) {
        User actor = userService.getById(actorId);
        saveSettlement(ActivityType.SETTLEMENT_DELETED, actorId, s, actor.getName() + " deleted a payment: " + payment(s));
    }

    public void settlementRestored(Long actorId, Settlement s) {
        User actor = userService.getById(actorId);
        saveSettlement(ActivityType.SETTLEMENT_RESTORED, actorId, s,
                actor.getName() + " restored a payment: " + payment(s));
    }

    private void saveSettlement(ActivityType type, Long actorId, Settlement s, String summary) {
        activityRepository.save(new Activity(type, userService.getById(actorId), groupId(s.getGroup()), null, s.getId(),
                summary, null, now(), List.of(s.getPayer().getId(), s.getPayee().getId())));
    }

    private static String payment(Settlement s) {
        return s.getPayer().getName() + " paid " + s.getPayee().getName() + " " + money(s.getCurrency(), s.getAmount());
    }

    // ------------------------------------------------------------------ groups and members

    public void groupCreated(Long actorId, Group g) {
        User actor = userService.getById(actorId);
        List<String> others = g.getMembers().stream().filter(m -> !m.getId().equals(actorId)).map(User::getName)
                .sorted().toList();
        saveGroup(ActivityType.GROUP_CREATED, actor, g, actor.getName() + " created the group \"" + g.getName() + "\"",
                others.isEmpty() ? null : "Members: " + String.join(", ", others), Set.of());
    }

    public void groupRenamed(Long actorId, Group g, String oldName) {
        User actor = userService.getById(actorId);
        saveGroup(ActivityType.GROUP_RENAMED, actor, g, actor.getName() + " renamed the group \"" + oldName
                + "\" to \"" + g.getName() + "\"", null, Set.of());
    }

    public void memberAdded(Long actorId, Group g, User member) {
        User actor = userService.getById(actorId);
        saveGroup(ActivityType.MEMBER_ADDED, actor, g, actor.getName() + " added " + member.getName()
                + " to \"" + g.getName() + "\"", null, Set.of(member.getId()));
    }

    /** A member leaving ({@code actor == member}) or being removed by the creator. */
    public void memberRemoved(Long actorId, Group g, User member) {
        User actor = userService.getById(actorId);
        boolean left = actorId.equals(member.getId());
        saveGroup(left ? ActivityType.MEMBER_LEFT : ActivityType.MEMBER_REMOVED, actor, g,
                left ? member.getName() + " left \"" + g.getName() + "\""
                        : actor.getName() + " removed " + member.getName() + " from \"" + g.getName() + "\"",
                null, Set.of(member.getId()));
    }

    /**
     * The group's own history is deleted with it (ON DELETE CASCADE), so this entry is stored without a group
     * and made visible to everyone who was a member.
     */
    public void groupDeleted(Long actorId, Group g) {
        User actor = userService.getById(actorId);
        activityRepository.save(new Activity(ActivityType.GROUP_DELETED, actor, null, null, null,
                actor.getName() + " deleted the group \"" + g.getName() + "\"", null, now(),
                g.getMembers().stream().map(User::getId).toList()));
    }

    private void saveGroup(ActivityType type, User actor, Group g, String summary, String details,
                           Collection<Long> involved) {
        activityRepository.save(new Activity(type, actor, g.getId(), null, null, summary, details, now(), involved));
    }

    // ------------------------------------------------------------------ recurring expenses

    public void recurringCreated(Long actorId, RecurringExpense r) {
        User actor = userService.getById(actorId);
        saveRecurring(ActivityType.RECURRING_CREATED, actor, r, actor.getName() + " set up \"" + r.getDescription()
                + "\" (" + money(r.getCurrency(), r.getAmount()) + ") to repeat " + r.getFrequency().name().toLowerCase()
                + " from " + r.getStartDate());
    }

    public void recurringStopped(Long actorId, RecurringExpense r) {
        User actor = userService.getById(actorId);
        saveRecurring(ActivityType.RECURRING_STOPPED, actor, r,
                actor.getName() + " stopped the recurring \"" + r.getDescription() + "\"");
    }

    /** The scheduler could not create an occurrence; recorded in the name of the template's creator. */
    public void recurringPaused(RecurringExpense r, String reason) {
        saveRecurring(ActivityType.RECURRING_PAUSED, r.getCreatedBy(), r,
                "Recurring \"" + r.getDescription() + "\" was paused: " + reason);
    }

    private void saveRecurring(ActivityType type, User actor, RecurringExpense r, String summary) {
        Set<Long> involved = new HashSet<>(r.getShares().keySet());
        involved.add(r.getPaidBy().getId());
        activityRepository.save(new Activity(type, actor, groupId(r.getGroup()), null, null, summary, null, now(),
                involved));
    }

    // ------------------------------------------------------------------ helpers

    private Instant now() {
        return Instant.now(clock);
    }

    private static Long groupId(Group group) {
        return group == null ? null : group.getId();
    }

    private static String money(String currency, BigDecimal amount) {
        return currency + " " + ExpenseDiff.money(amount);
    }
}
