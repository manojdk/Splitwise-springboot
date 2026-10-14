package com.splitwise.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Collection;
import java.util.Optional;
import com.splitwise.domain.Settlement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {

    @EntityGraph(attributePaths = {"payer", "payee", "group"})
    Optional<Settlement> findDetailedById(Long id);

    /** The group's settlements that are not deleted, newest first. */
    @EntityGraph(attributePaths = {"payer", "payee", "group"})
    List<Settlement> findByGroupIdAndDeletedAtIsNullOrderBySettledOnDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = {"payer", "payee", "group"})
    @Query("""
            select s from Settlement s
            where s.deletedAt is null and (s.payer.id = :userId or s.payee.id = :userId)
            order by s.settledOn desc, s.id desc
            """)
    List<Settlement> findInvolvingUser(@Param("userId") Long userId);

    boolean existsByGroupId(Long groupId);

    /** Non-deleted settlements of several groups at once. */
    @EntityGraph(attributePaths = {"payer", "payee", "group"})
    List<Settlement> findByGroupIdInAndDeletedAtIsNull(Collection<Long> groupIds);

    /** The settlement's group id (empty for a non-group or unknown settlement), read without loading it. */
    @Query("select s.group.id from Settlement s where s.id = :id and s.group is not null")
    Optional<Long> findGroupIdById(@Param("id") Long id);

    /** Whether one of the two users has paid the other outside groups, deleted payments included. */
    @Query("""
            select count(s) > 0 from Settlement s
            where s.group is null
              and ((s.payer.id = :userId and s.payee.id = :otherId) or (s.payer.id = :otherId and s.payee.id = :userId))
            """)
    boolean havePaidEachOtherOutsideGroups(@Param("userId") Long userId, @Param("otherId") Long otherId);

    @Query("select distinct s.payee from Settlement s where s.group is null and s.payer.id = :userId")
    List<com.splitwise.domain.User> findNonGroupPayees(@Param("userId") Long userId);

    @Query("select distinct s.payer from Settlement s where s.group is null and s.payee.id = :userId")
    List<com.splitwise.domain.User> findNonGroupPayers(@Param("userId") Long userId);
}
