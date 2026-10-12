package com.splitwise.repository;

import com.splitwise.domain.Group;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    @EntityGraph(attributePaths = {"members", "createdBy"})
    Optional<Group> findWithMembersById(Long id);

    @Query("""
            select distinct g from Group g
            left join fetch g.members
            where g.id in (select g2.id from Group g2 join g2.members m where m.id = :userId)
            order by g.id
            """)
    List<Group> findAllForMember(@Param("userId") Long userId);
}
