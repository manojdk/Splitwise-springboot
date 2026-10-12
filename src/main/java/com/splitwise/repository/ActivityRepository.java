package com.splitwise.repository;

import com.splitwise.domain.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /**
     * The user's feed: everything in the groups they are in now, plus everything they were involved in
     * (including non-group expenses and groups they have left).
     */
    @EntityGraph(attributePaths = {"actor"})
    @Query(value = """
            select a from Activity a
            where a.groupId in (select g.id from Group g join g.members m where m.id = :userId)
               or :userId member of a.involvedUserIds
            """,
            countQuery = """
            select count(a) from Activity a
            where a.groupId in (select g.id from Group g join g.members m where m.id = :userId)
               or :userId member of a.involvedUserIds
            """)
    Page<Activity> findFeedForUser(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"actor"})
    Page<Activity> findByGroupId(Long groupId, Pageable pageable);

    /** An expense's history, oldest first. */
    @EntityGraph(attributePaths = {"actor"})
    List<Activity> findByExpenseIdOrderByIdAsc(Long expenseId);
}
