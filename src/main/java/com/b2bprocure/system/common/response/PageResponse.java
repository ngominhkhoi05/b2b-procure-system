package com.b2bprocure.system.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int pageNo;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private boolean first;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }

    public static <T, E> PageResponse<T> of(Page<E> page, List<T> content) {
        return PageResponse.<T>builder()
                .content(content)
                .pageNo(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }

    /**
     * Build a PageResponse from content + Pageable + totalElements without
     * holding a Page object. Used by code paths that page over ids first
     * and then batch-fetch the entities (e.g. FTS two-step query in
     * ProductServiceImpl.getProducts).
     *
     * <p>totalPages is rounded up so a non-empty result on the last partial
     * page is still represented accurately. {@code last}/{@code first} are
     * derived from pageNo, which is correct when pageNo is 0-based (Spring
     * Data convention).
     */
    public static <T> PageResponse<T> of(List<T> content, Pageable pageable, long totalElements) {
        int pageNo = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int totalPages = pageSize <= 0
                ? 0
                : (int) Math.ceil((double) totalElements / pageSize);
        return PageResponse.<T>builder()
                .content(content)
                .pageNo(pageNo)
                .pageSize(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(pageNo == 0)
                .last(pageNo >= totalPages - 1)
                .build();
    }
}
