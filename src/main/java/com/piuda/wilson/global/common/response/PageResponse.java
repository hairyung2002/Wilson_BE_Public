package com.piuda.wilson.global.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** api-spec 1장 Pagination 응답 형식. */
public record PageResponse<T>(int page, int size, long totalElements, int totalPages, List<T> content) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent()
        );
    }
}
