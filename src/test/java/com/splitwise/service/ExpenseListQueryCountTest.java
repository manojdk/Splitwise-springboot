package com.splitwise.service;

import com.splitwise.query.ExpenseFilter;
import com.splitwise.support.ApiTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against N+1 queries: loading a page of 20 expenses (each with a payer, a group and shares with users)
 * must take a handful of SQL statements, not one or more per expense. Lazy associations are fetched in batches
 * ({@code hibernate.default_batch_fetch_size}).
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ExpenseListQueryCountTest extends ApiTestSupport {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void aPageOfExpensesTakesAFixedSmallNumberOfQueries() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        long groupId = group(alice, bob, carol);
        for (int i = 0; i < 20; i++) {
            equalExpense(alice, groupId, i % 2 == 0 ? alice : bob, "30.00", alice, bob, carol);
        }

        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var page = expenseService.listForGroup(alice.id(), groupId, ExpenseFilter.none(), 0, 20);

        assertThat(page.content()).hasSize(20);
        assertThat(page.content()).allSatisfy(e -> assertThat(e.shares()).hasSize(3));
        // membership check + page + count + batched payers/groups/shares/users: about 6, independent of page size
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(8);
    }
}
