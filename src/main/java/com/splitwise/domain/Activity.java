package com.splitwise.domain;

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

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * One entry of the activity feed: who did what, when. Immutable once written; the summary and details are a
 * snapshot taken at the time (later renames do not rewrite history).
 */
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "expense_id")
    private Long expenseId;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(length = 4000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** People involved, who see this entry even without being in the group. */
    @ElementCollection
    @CollectionTable(name = "activity_user", joinColumns = @JoinColumn(name = "activity_id"))
    @Column(name = "user_id")
    private Set<Long> involvedUserIds = new HashSet<>();

    protected Activity() {
    }

    public Activity(ActivityType type, User actor, Long groupId, Long expenseId, Long settlementId, String summary,
                    String details, Instant createdAt, Collection<Long> involvedUserIds) {
        this.type = type;
        this.actor = actor;
        this.groupId = groupId;
        this.expenseId = expenseId;
        this.settlementId = settlementId;
        this.summary = summary;
        this.details = details;
        this.createdAt = createdAt;
        this.involvedUserIds.addAll(involvedUserIds);
        this.involvedUserIds.add(actor.getId());
    }

    public Long getId() {
        return id;
    }

    public ActivityType getType() {
        return type;
    }

    public User getActor() {
        return actor;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Long> getInvolvedUserIds() {
        return involvedUserIds;
    }
}
