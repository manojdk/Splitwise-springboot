package com.splitwise.repository;

import java.util.Optional;
import com.splitwise.domain.Settlement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

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

    /** The settlement's group id (empty for a non-group or unknown settlement), read without loading it. */
    @Query("select s.group.id from Settlement s where s.id = :id and s.group is not null")
    Optional<Long> findGroupIdById(@Param("id") Long id);
}
