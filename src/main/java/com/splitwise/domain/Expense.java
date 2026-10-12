package com.splitwise.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ExpenseShare> shares = new ArrayList<>();

    /** Optimistic lock: incremented on every change, so concurrent edits cannot overwrite each other. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    /** Soft delete: a deleted expense is excluded from lists and balances but can be restored. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private User deletedBy;

    protected Expense() {
    }

    public Expense(String description, BigDecimal amount, String currency, User paidBy, Group group,
                   LocalDate expenseDate, SplitType splitType, User createdBy) {
        this.description = description;
        this.amount = Money.of(amount);
        this.currency = currency;
        this.paidBy = paidBy;
        this.group = group;
        this.expenseDate = expenseDate;
        this.splitType = splitType;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public void addShare(User user, BigDecimal shareAmount) {
        shares.add(new ExpenseShare(this, user, shareAmount));
    }

    /** Replaces every editable field. The caller has already validated the split. */
    public void update(String description, BigDecimal amount, String currency, User paidBy, LocalDate expenseDate,
                       SplitType splitType, Map<User, BigDecimal> newShares, User editor, Instant now) {
        this.description = description;
        this.amount = Money.of(amount);
        this.currency = currency;
        this.paidBy = paidBy;
        this.expenseDate = expenseDate;
        this.splitType = splitType;
        replaceShares(newShares);
        this.updatedBy = editor;
        this.updatedAt = now;
    }

    /**
     * Updates the shares in place: a user who stays keeps their row (only the amount changes), users who drop out
     * are removed and new ones added. Removing and re-inserting the same user would break the unique
     * (expense_id, user_id) constraint, because Hibernate runs inserts before orphan deletes.
     */
    private void replaceShares(Map<User, BigDecimal> newShares) {
        Map<Long, BigDecimal> byUserId = new HashMap<>();
        Map<Long, User> users = new HashMap<>();
        newShares.forEach((user, value) -> {
            byUserId.put(user.getId(), value);
            users.put(user.getId(), user);
        });
        shares.removeIf(share -> !byUserId.containsKey(share.getUser().getId()));
        Set<Long> kept = new HashSet<>();
        for (ExpenseShare share : shares) {
            share.setAmount(byUserId.get(share.getUser().getId()));
            kept.add(share.getUser().getId());
        }
        byUserId.forEach((userId, value) -> {
            if (!kept.contains(userId)) {
                shares.add(new ExpenseShare(this, users.get(userId), value));
            }
        });
    }

    public void markDeleted(User by, Instant now) {
        this.deletedBy = by;
        this.deletedAt = now;
    }

    public void restore() {
        this.deletedBy = null;
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** The payer and everyone with a share. */
    public Set<Long> involvedUserIds() {
        Set<Long> ids = new HashSet<>();
        ids.add(paidBy.getId());
        shares.forEach(s -> ids.add(s.getUser().getId()));
        return ids;
    }

    public Long getId() {
        return id;
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

    public Group getGroup() {
        return group;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ExpenseShare> getShares() {
        return shares;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public User getUpdatedBy() {
        return updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public User getDeletedBy() {
        return deletedBy;
    }
}
