package com.hireflow.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Small, stable JSON shape for every paginated list endpoint ({@code /api/applications},
 * {@code /api/postings}, {@code /api/admin/users}), instead of returning Spring Data's
 * {@code Page<T>} directly. {@code Page} is a Jackson-serializable interface, but its wire
 * format has changed across Spring Data versions (flattened {@code number}/{@code size}/... vs.
 * a nested {@code page} object) and pulls in framework-shaped fields (e.g. {@code pageable},
 * {@code sort}) that aren't useful to a frontend. This record is deliberately minimal and
 * version-stable.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
