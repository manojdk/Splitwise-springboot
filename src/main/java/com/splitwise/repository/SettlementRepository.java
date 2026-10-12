package com.splitwise.repository;

import com.splitwise.domain.Settlement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = {"payer", "payee", "group"})
    List<Settlement> findByGroupIdOrderBySettledOnDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = {"payer", "payee", "group"})
    @Query("""
            select s from Settlement s
            where s.payer.id = :userId or s.payee.id = :userId
            order by s.settledOn desc, s.id desc
            """)
    List<Settlement> findInvolvingUser(@Param("userId") Long userId);

    boolean existsByGroupId(Long groupId);
}
