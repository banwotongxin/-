package com.cqu.campus.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 分页响应，pageNum 从 1 开始，pageSize 默认 10、上限 100
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;

    public static <T> PageResult<T> of(List<T> records, long total, Integer pageNum, Integer pageSize) {
        return new PageResult<T>(records == null ? Collections.<T>emptyList() : records,
                total, safePageNum(pageNum), safePageSize(pageSize));
    }

    public static <T> PageResult<T> empty(Integer pageNum, Integer pageSize) {
        return new PageResult<T>(Collections.<T>emptyList(), 0L, safePageNum(pageNum), safePageSize(pageSize));
    }

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<T>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static long safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    public static long safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return pageSize > 100 ? 100L : pageSize;
    }
}
