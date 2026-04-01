package com.workshop.dto;

import java.util.List;

/**
 * Serializable wrapper for a paginated result set.
 * Returned by list endpoints that support ?page=&size= query params.
 */
public record PageResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size
) {
    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
