package com.splitwise.domain;

import com.splitwise.recurring.Schedule;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A template for an expense that repeats (rent, wifi). The scheduler turns each due occurrence into a real
 * {@link Expense}; occurrence n falls on {@link Schedule#occurrence(LocalDate, Frequency, int)}.
 */
@Entity
@Table(name = "recurring_expense")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(length = 1000)
    private String notes;

    /** The participants and their split values (null for EQUAL, or an ADJUSTMENT left out). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recurring_expense_share", joinColumns = @JoinColumn(name = "recurring_expense_id"))
    private List<RecurringShare> shares = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Frequency frequency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Last day an occurrence may fall on (inclusive), or null for no end. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Index of the next occurrence to create. */
    @Column(name = "next_occurrence", nullable = false)
    private int nextOccurrence;

    /** Date of the next occurrence (null once finished or stopped); kept for the scheduler's query. */
    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "stopped_reason", length = 255)
    private String stoppedReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RecurringExpense() {
    }

    public RecurringExpense(Group group, String description, BigDecimal amount, String currency, User paidBy,
                            SplitType splitType, Category category, String notes, Map<Long, BigDecimal> shares,
                            Frequency frequency, LocalDate startDate, LocalDate endDate, User createdBy,
                            Instant createdAt) {
        this.group = group;
        this.description = description;
        this.amount = Money.of(amount);
        this.currency = currency;
        this.paidBy = paidBy;
        this.splitType = splitType;
        this.category = category;
        this.notes = notes;
        shares.forEach((userId, value) -> this.shares.add(new RecurringShare(userId, value)));
        this.frequency = frequency;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.active = true;
        this.nextOccurrence = 0;
        this.nextRunDate = startDate;
    }

    /** The date of the next occurrence to create (whether or not it is due yet). */
    public LocalDate nextDate() {
        return Schedule.occurrence(startDate, frequency, nextOccurrence);
    }

    /** True while there is an occurrence on or before {@code today} still to create. */
    public boolean isDue(LocalDate today) {
        return active && !nextDate().isAfter(today) && withinEnd(nextDate());
    }

    /** Moves on after an occurrence was created; finishes the template when the end date is passed. */
    public void advance() {
        nextOccurrence++;
        if (withinEnd(nextDate())) {
            nextRunDate = nextDate();
        } else {
            finish("Ended on " + endDate);
        }
    }

    /**
     * Runs again after a stop or a pause, from the first date on or after {@code today}: the dates missed in
     * between are skipped, not created late. Returns false, changing nothing, if no date is left before the end.
     */
    public boolean resumeFrom(LocalDate today) {
        int next = nextOccurrence;
        while (Schedule.occurrence(startDate, frequency, next).isBefore(today)) {
            next++;
        }
        if (!withinEnd(Schedule.occurrence(startDate, frequency, next))) {
            return false;
        }
        nextOccurrence = next;
        active = true;
        stoppedReason = null;
        nextRunDate = nextDate();
        return true;
    }

    public void finish(String reason) {
        this.active = false;
        this.stoppedReason = reason;
        this.nextRunDate = null;
    }

    private boolean withinEnd(LocalDate date) {
        return endDate == null || !date.isAfter(endDate);
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public Category getCategory() {
        return category;
    }

    public String getNotes() {
        return notes;
    }

    /** Split value per user id, in user id order (values may be null). */
    public Map<Long, BigDecimal> getShares() {
        Map<Long, BigDecimal> byUser = new TreeMap<>();
        shares.forEach(s -> byUser.put(s.getUserId(), s.getValue()));
        return byUser;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getNextOccurrence() {
        return nextOccurrence;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public boolean isActive() {
        return active;
    }

    public String getStoppedReason() {
        return stoppedReason;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
