package com.splitwise.service;

import com.splitwise.domain.SplitType;
import com.splitwise.exception.ApiException;
import com.splitwise.exception.BadRequestException;
import com.splitwise.exception.ConflictException;
import com.splitwise.support.ApiTestSupport;
import com.splitwise.web.dto.CreateExpenseRequest;
import com.splitwise.web.dto.ShareRequest;
import com.splitwise.web.dto.UpdateExpenseRequest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
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

/**
 * Requests that race each other on the same expense or group. Each outcome must be one of the allowed ones,
 * never a lost update, an unexpected error, or money left behind for someone who has left.
 */
class ConcurrentEditsIntegrationTest extends ApiTestSupport {

    @Autowired
    private ExpenseService expenseService;
    @Autowired
    private GroupService groupService;
    @Autowired
    private BalanceService balanceService;

    @Test
    void twoEditsOfTheSameVersionAtOnceExactlyOneWins() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);
        long id = equalExpense(alice, groupId, alice, "100.00", alice, bob).get("id").asLong();

        List<Callable<Object>> edits = new ArrayList<>();
        for (String amount : List.of("111.00", "222.00", "333.00", "444.00")) {
            edits.add(() -> expenseService.updateExpense(alice.id(), id, new UpdateExpenseRequest("Edit", new BigDecimal(amount),
                    null, alice.id(), null, SplitType.EQUAL,
                    List.of(new ShareRequest(alice.id(), null), new ShareRequest(bob.id(), null)), 0L)));
        }

        List<Throwable> failures = new ArrayList<>();
        int successes = runTogether(edits, failures);

        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(3).allMatch(f -> f instanceof ConflictException
                || f instanceof OptimisticLockingFailureException);
        assertThat(expenseService.get(alice.id(), id).version()).isEqualTo(1);
    }

    @RepeatedTest(5)
    void aMemberLeavingWhileAnExpenseForThemIsAddedNeverLeavesMoneyBehind() throws Exception {
        Session alice = user("Alice");
        Session bob = user("Bob");
        long groupId = group(alice, bob);

        Callable<Object> addExpenseForBob = () -> expenseService.addExpense(alice.id(), new CreateExpenseRequest(
                "Taxi", new BigDecimal("60.00"), null, alice.id(), groupId, null, SplitType.EQUAL,
                List.of(new ShareRequest(alice.id(), null), new ShareRequest(bob.id(), null))));
        Callable<Object> bobLeaves = () -> groupService.removeMember(bob.id(), groupId, bob.id());

        List<Throwable> failures = new ArrayList<>();
        int successes = runTogether(List.of(addExpenseForBob, bobLeaves), failures);

        // Either the expense came first (Bob owes 30 and may not leave: 409), or Bob left first (the expense
        // names a non-member: 400). Never both.
        assertThat(successes).isEqualTo(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0)).isInstanceOfAny(ConflictException.class, BadRequestException.class);
        boolean bobStillMember = groupService.get(alice.id(), groupId).members().stream()
                .anyMatch(m -> m.id().equals(bob.id()));
        BigDecimal aliceBalance = balanceService.groupBalances(alice.id(), groupId).stream()
                .filter(b -> b.userId().equals(alice.id())).findFirst().orElseThrow().netBalance();
        if (bobStillMember) {
            assertThat(aliceBalance).isEqualByComparingTo("30.00");
        } else {
            assertThat(aliceBalance).isEqualByComparingTo("0.00");
        }
    }

    /** Starts all tasks behind a latch so they really run at the same moment. */
    private static int runTogether(List<Callable<Object>> tasks, List<Throwable> failures) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int successes = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (!(cause instanceof ApiException) && !(cause instanceof OptimisticLockingFailureException)) {
                        throw new AssertionError("Unexpected failure", cause);
                    }
                    failures.add(cause);
                }
            }
            return successes;
        } finally {
            pool.shutdownNow();
        }
    }
}
