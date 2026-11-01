package com.litemall.exception;

import java.util.List;

/**
 * <p>
 * 批量扣减库存时「某一行库存不足」的内部控制流异常（08 v4 CI-44 / 03 §4.7 D-24）。
 * </p>
 *
 * <p>
 * 存在的意义：批量扣减要求<b>任一行不足则整批回滚</b>，而回滚只能由事务边界上的异常触发；
 * 同时又要把「哪些行不足、差多少」带回给调用方。故由 service 抛出、由 controller 捕获后
 * 转成 {@code DeductBatchResultDTO{deducted:false}}——异常不越过 controller，不会落到全局异常处理器。
 * </p>
 *
 * <p>
 * 故意<b>不</b>继承 {@code CommonException}：它不是业务错误码，不允许被 advice 转成 HTTP 错误响应。
 * </p>
 */
public class InsufficientStockException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<com.litemall.domain.dto.DeductBatchResultDTO.Item> items;

    public InsufficientStockException(List<com.litemall.domain.dto.DeductBatchResultDTO.Item> items) {
        super("库存不足");
        this.items = items;
    }

    public List<com.litemall.domain.dto.DeductBatchResultDTO.Item> getItems() {
        return items;
    }
}