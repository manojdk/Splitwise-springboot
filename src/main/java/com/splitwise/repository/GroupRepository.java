package com.splitwise.repository;

import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
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

    /**
     * Loads the group and locks its row (SELECT ... FOR UPDATE) until the transaction ends. Writes that depend on
     * who is a member (adding an expense, removing a member) take this lock first, so they cannot interleave.
     * No fetch join here: PostgreSQL does not allow FOR UPDATE on the nullable side of an outer join; members are
     * loaded afterwards, under the lock.
     */
    /** Ids of the groups the user belongs to, without loading (and caching) the groups. */
    @Query("select g.id from Group g join g.members m where m.id = :userId")
    List<Long> findIdsForMember(@Param("userId") Long userId);

    /** Whether both users are members of at least one group. */
    @Query("select count(g) > 0 from Group g join g.members a join g.members b where a.id = :userId and b.id = :otherId")
    boolean shareAGroup(@Param("userId") Long userId, @Param("otherId") Long otherId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.id = :id")
    Optional<Group> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select distinct g from Group g
            left join fetch g.members
            where g.id in (select g2.id from Group g2 join g2.members m where m.id = :userId)
            order by g.id
            """)
    List<Group> findAllForMember(@Param("userId") Long userId);
}
