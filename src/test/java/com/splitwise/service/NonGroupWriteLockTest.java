package com.splitwise.service;

import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every write to a non-group expense or payment takes the lock on the people involved that a friend settle-up
 * holds, so a settle-up never pays a balance that changes underneath it. Each test holds that lock (as a settle-up
 * would) and checks the write waits for it instead of going ahead.
 */
class NonGroupWriteLockTest extends ApiTestSupport {

    @Autowired
    private UserService userService;
    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void addingAnExpenseWaitsForASettleUp() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        contacts(alice, bob);

        waitsWhileLocked(Set.of(bob.id()), () ->
                addExpense(alice, expenseBody(null, alice, "40.00", "EQUAL", Share.of(alice), Share.of(bob))))
                .andExpect(status().isCreated());
    }

    /** The lock covers the people taken out of the expense too: their balance changes as well. */
    @Test
    void editingAnExpenseWaitsForEveryoneBeforeAndAfter() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        Session carol = user("Carol");
        contacts(alice, bob, carol);
        long id = nonGroupExpense(alice, bob);

        waitsWhileLocked(Set.of(bob.id()), () -> updateExpense(alice, id,
                updateBody(0, alice, "40.00", "EQUAL", Share.of(alice), Share.of(carol))))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAndRestoringAnExpenseWaitForASettleUp() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        contacts(alice, bob);
        long id = nonGroupExpense(alice, bob);

        waitsWhileLocked(Set.of(bob.id()), () -> call(alice, delete("/api/expenses/" + id)))
                .andExpect(status().isNoContent());
        waitsWhileLocked(Set.of(bob.id()), () -> call(alice, post("/api/expenses/" + id + "/restore")))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAndRestoringAPaymentWaitForASettleUp() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        contacts(alice, bob);
        long id = read(settle(alice, null, bob, alice, "20.00").andExpect(status().isCreated())).get("id").asLong();

        waitsWhileLocked(Set.of(bob.id()), () -> call(alice, delete("/api/settlements/" + id)))
                .andExpect(status().isNoContent());
        waitsWhileLocked(Set.of(bob.id()), () -> call(alice, post("/api/settlements/" + id + "/restore")))
                .andExpect(status().isOk());
    }

    @Test
    void aGroupExpenseDoesNotWaitForTheUsers() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);

        // Group writes are guarded by the group lock; settle-up takes the users first and the groups after
        Future<ResultActions> write = whileLocked(List.of(bob.id()), () ->
                addExpense(alice, expenseBody(flat, alice, "40.00", "EQUAL", Share.of(alice), Share.of(bob))),
                false);
        write.get(10, TimeUnit.SECONDS).andExpect(status().isCreated());
    }

    private long nonGroupExpense(Session alice, Session bob) throws Exception {
        return read(addExpense(alice, expenseBody(null, alice, "40.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Runs the write while the users' rows are locked; it must still be waiting half a second later. */
    private ResultActions waitsWhileLocked(Set<Long> userIds, Callable<ResultActions> write) throws Exception {
        return whileLocked(userIds, write, true).get(10, TimeUnit.SECONDS);
    }

    private Future<ResultActions> whileLocked(Collection<Long> userIds, Callable<ResultActions> write,
                                              boolean mustWait) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            return new TransactionTemplate(transactions).execute(tx -> {
                userService.lockAll(userIds);
                Future<ResultActions> future = pool.submit(write);
                if (mustWait) {
                    assertThatThrownBy(() -> future.get(500, TimeUnit.MILLISECONDS))
                            .as("the write went ahead while the users were locked")
                            .isInstanceOf(TimeoutException.class);
                } else {
                    assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
                }
                return future;
            });
        } finally {
            pool.shutdown();
        }
    }
}
