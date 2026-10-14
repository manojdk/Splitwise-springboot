package com.splitwise.service;

import com.splitwise.exception.ConflictException;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two people pressing "settle up" with each other at the same moment must not pay twice.
 */
class FriendSettleUpConcurrencyTest extends ApiTestSupport {

    @Autowired
    private FriendBalanceService friendBalanceService;
    @Autowired
    private SettlementService settlementService;

    @RepeatedTest(3)
    void simultaneousSettleUpsPayOnlyOnce() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long flat = group(alice, bob);
        equalExpense(alice, flat, alice, "100.00", alice, bob);
        addExpense(alice, expenseBody(null, alice, "40.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated());

        settleUpTwiceAtOnce(alice, bob, 2);
    }

    /** Only non-group expenses: no group lock is involved, so only the lock on the two users protects this. */
    @RepeatedTest(3)
    void simultaneousSettleUpsWithoutAnyGroupPayOnlyOnce() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        contacts(alice, bob);
        addExpense(alice, expenseBody(null, alice, "40.00", "EQUAL", Share.of(alice), Share.of(bob)))
                .andExpect(status().isCreated());

        settleUpTwiceAtOnce(alice, bob, 1);
    }

    private void settleUpTwiceAtOnce(Session alice, Session bob, int expectedSettlements) throws Exception {
        List<Callable<Object>> tasks = List.of(
                () -> friendBalanceService.settleUp(alice.id(), bob.id()),
                () -> friendBalanceService.settleUp(bob.id(), alice.id()));

        int successes = 0;
        List<Throwable> failures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return task.call();
                }));
            }
            go.countDown();
            for (Future<Object> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes).isEqualTo(1);
        assertThat(failures).singleElement().isInstanceOf(ConflictException.class);
        // Exactly one payment per bucket, not two
        assertThat(settlementService.listForUser(alice.id(), null, null, 0, 100).totalElements())
                .isEqualTo(expectedSettlements);
        assertThat(friendBalanceService.balanceWith(alice.id(), bob.id()).balances()).isEmpty();
    }
}
