package com.splitwise.query;

import com.splitwise.exception.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validated page requests. Every paginated list uses the same limits, and each list has a fixed sort that ends
 * with the id, so the order is total and consecutive pages never repeat or skip an item.
 */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }
}
