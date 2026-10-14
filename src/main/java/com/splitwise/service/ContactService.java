package com.splitwise.service;

import com.splitwise.exception.ForbiddenException;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Who a user may record expenses and payments with outside groups: people they share a group with, or have already
 * split with or paid outside groups. Without this, anyone could put a debt on any account by guessing its id.
 */
@Service
public class ContactService {

    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public ContactService(GroupRepository groupRepository, ExpenseRepository expenseRepository,
                          SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    /**
     * Refuses unless every other user is a contact of the caller. Unknown ids get the same answer as strangers, so the
     * check does not reveal which accounts exist.
     */
    public void requireContacts(Long callerId, Collection<Long> userIds) {
        for (Long userId : userIds) {
            if (!userId.equals(callerId) && !isContact(callerId, userId)) {
                throw new ForbiddenException("Outside groups you can only split or settle with people you share a "
                        + "group with or have split with before");
            }
        }
    }

    private boolean isContact(Long callerId, Long userId) {
        return groupRepository.shareAGroup(callerId, userId)
                || expenseRepository.haveNonGroupExpenseTogether(callerId, userId)
                || settlementRepository.havePaidEachOtherOutsideGroups(callerId, userId);
    }
}
