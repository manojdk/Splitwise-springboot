package com.splitwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "expense_group")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** ISO 4217 code; everything recorded in the group is in this currency. */
    @Column(nullable = false, length = 3)
    private String currency = Money.DEFAULT_CURRENCY;

    @ManyToMany
    @JoinTable(name = "group_member",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new LinkedHashSet<>();

    /** Membership changes bump the version too, so they cannot silently overwrite each other. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Group() {
    }

    public Group(String name, User createdBy) {
        this(name, createdBy, Money.DEFAULT_CURRENCY);
    }

    public Group(String name, User createdBy, String currency) {
        this.currency = currency;
        this.name = name;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.members.add(createdBy);
    }

    public boolean hasMember(Long userId) {
        return members.stream().anyMatch(m -> m.getId().equals(userId));
    }

    public void addMember(User user) {
        members.add(user);
    }

    public void removeMember(Long userId) {
        members.removeIf(m -> m.getId().equals(userId));
    }

    public boolean isCreator(Long userId) {
        return createdBy.getId().equals(userId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCurrency() {
        return currency;
    }

    public Set<User> getMembers() {
        return members;
    }
}
