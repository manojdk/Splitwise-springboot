package com.splitwise.recurring;

import com.splitwise.service.RecurringExpenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creates the expenses of due recurring templates. Runs every hour by default ({@code app.recurring.cron}), so
 * an occurrence appears within the hour of its day starting, and a run missed while the app was down is caught
 * up by the next one. Each template is handled in its own transaction: one failure does not stop the others.
 */
@Component
public class RecurringExpenseJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseJob.class);

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseJob(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @Scheduled(cron = "${app.recurring.cron:0 5 * * * *}")
    public void run() {
        runDueNow();
    }

    /** @return the number of expenses created */
    public int runDueNow() {
        int created = 0;
        for (Long id : recurringExpenseService.findDueIds()) {
            try {
                created += recurringExpenseService.generateDue(id);
            } catch (RuntimeException e) {
                log.error("Could not create the due expenses of recurring expense {}", id, e);
            }
        }
        if (created > 0) {
            log.info("Created {} recurring expense(s)", created);
        }
        return created;
    }
}
