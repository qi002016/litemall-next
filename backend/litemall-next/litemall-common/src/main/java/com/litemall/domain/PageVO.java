package com.litemall.domain;


import com.litemall.utils.BeanUtils;
import com.litemall.utils.CollUtils;
import com.litemall.utils.Convert;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageVO<T> {
    @Schema(description = "总条数", type = "string")
    protected Long total;
    @Schema(description = "总页数", type = "string")
    protected Long pages;
    protected List<T> list;

    public static <T> PageVO<T> empty(Long total, Long pages) {
        return new PageVO<>(total, pages, CollUtils.emptyList());
    }
    public static <T> PageVO<T> empty(com.baomidou.mybatisplus.extension.plugins.pagination.Page<?> page) {
        return new PageVO<>(page.getTotal(), page.getPages(), CollUtils.emptyList());
    }

    public static <T> PageVO<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        if(page == null){
            return new PageVO<>();
        }
        if (CollUtils.isEmpty(page.getRecords())) {
            return empty(page);
        }
        return new PageVO<>(page.getTotal(), page.getPages(), page.getRecords());
    }
    public static <T,R> PageVO<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<R> page, Function<R, T> mapper) {
        if(page == null){
            return new PageVO<>();
        }
        if (CollUtils.isEmpty(page.getRecords())) {
            return empty(page);
        }
        return new PageVO<>(page.getTotal(), page.getPages(),
                page.getRecords().stream().map(mapper).collect(Collectors.toList()));
    }
    public static <T> PageVO<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<?> page, List<T> list) {
        return new PageVO<>(page.getTotal(), page.getPages(), list);
    }

    public static <T, R> PageVO<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<R> page, Class<T> clazz) {
        return new PageVO<>(page.getTotal(), page.getPages(), BeanUtils.copyList(page.getRecords(), clazz));
    }

    public static <T, R> PageVO<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<R> page, Class<T> clazz, Convert<R, T> convert) {
        return new PageVO<>(page.getTotal(), page.getPages(), BeanUtils.copyList(page.getRecords(), clazz, convert));
    }
}
