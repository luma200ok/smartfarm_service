package com.smartfarm.service.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Spring Page 직접 노출 금지 — 계약(contract §4)대로 매핑하는 공용 페이지 응답. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
