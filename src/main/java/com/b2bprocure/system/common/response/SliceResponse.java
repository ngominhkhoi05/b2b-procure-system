package com.b2bprocure.system.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * Lightweight pagination wrapper for Slice-style responses.
 *
 * <p>Unlike {@link PageResponse}, this class does NOT carry
 * {@code totalElements} / {@code totalPages}. Those require a separate
 * {@code count(*)} SQL query, which on tables with millions of rows
 * is the dominant cost of a list endpoint.
 *
 * <p>Use this for infinite-scroll / "load more" UIs (BUYER browse page).
 * Admin / Supplier list views that need to display "page X of N total"
 * should continue using {@link PageResponse}.
 *
 * <p>The {@code hasNext} flag is sufficient for the FE to decide whether
 * to fetch another page; there is no need to know the absolute total.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SliceResponse<T> {

    private List<T> content;
    private int pageNo;
    private int pageSize;
    private boolean hasNext;
    private boolean first;
    private boolean last;

    /**
     * Build a SliceResponse from a Spring Data {@link Slice} (already has hasNext).
     */
    public static <T> SliceResponse<T> from(Slice<T> slice) {
        return SliceResponse.<T>builder()
                .content(slice.getContent())
                .pageNo(slice.getNumber())
                .pageSize(slice.getSize())
                .hasNext(slice.hasNext())
                .first(slice.isFirst())
                .last(slice.isLast())
                .build();
    }

    /**
     * Build a SliceResponse from raw content + Pageable + hasNext.
     *
     * <p>Used by code paths that fetch pageSize + 1 rows from the repository
     * and compute {@code hasNext} themselves by comparing the result size
     * to the page size.
     *
     * @param content  list of items (already trimmed to pageSize if hasNext=true)
     * @param pageable the pageable used for the request
     * @param hasNext  true if there is at least one more page after this one
     */
    public static <T> SliceResponse<T> of(List<T> content, Pageable pageable, boolean hasNext) {
        int pageNo = pageable.getPageNumber();
        return SliceResponse.<T>builder()
                .content(content)
                .pageNo(pageNo)
                .pageSize(pageable.getPageSize())
                .hasNext(hasNext)
                .first(pageNo == 0)
                .last(!hasNext)
                .build();
    }
}