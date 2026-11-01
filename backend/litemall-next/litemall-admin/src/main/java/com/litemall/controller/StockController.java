package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.config.OperationLogContext;
import com.litemall.domain.DTO.StockUpdateReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.StockUpdateVO;
import com.litemall.domain.dto.StockAdminDTO;
import com.litemall.support.AdminQuerySupport;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * <p>
 * 管理端库存视图接口（R1 范围：CN-13 列表 / CN-14 调整）。
 * </p>
 * <p>
 * 库存的唯一权威在 goods 域：本类只转发 {@code /inner/goods/admin/stock/**}，
 * {@code number} 为绝对值、{@code locked} 来自 order 的锁量（CN-IN-04）、
 * {@code sellable = number - locked} 与低库存阈值 5 均由 goods 侧计算（08 v4 / 03 §6.2）。
 * 锁量不可用时 goods 侧返回 {@code lockedUnavailable=true} 且 {@code locked/sellable=null}，
 * admin 原样透传，不自行补算。
 * </p>
 */
@RestController
@RequestMapping("/admin/stock")
@RequiredArgsConstructor
public class StockController {

    /** 08 v4 CN-13 排序白名单（默认 sellable 升序） */
    private static final Set<String> SORT_BY = Set.of("sellable", "number", "updateTime");

    /** 08 v4 CI-09/CN-13 商品状态筛选白名单 */
    private static final Set<String> GOODS_STATUS = Set.of("0", "1", "2");

    private final GoodsClient goodsClient;

    /**
     * CN-13 库存列表（按 SKU 行返回 number / locked / sellable）。
     *
     * @param pageNum      页码（≥1，默认 1）
     * @param pageSize     每页条数（默认 20，上限 100）
     * @param keyword      商品名或 SKU 码模糊匹配（≤50）
     * @param categoryId   类目ID
     * @param goodsStatus  商品状态，逗号分隔（0,1,2）
     * @param lowStockOnly 只看低库存（sellable ≤ 5）
     * @param soldOutOnly  只看售罄（sellable = 0）
     * @param sortBy       sellable | number | updateTime（默认 sellable 升序）
     * @return 分页库存行
     */
    @GetMapping("/list")
    public Result<PageVO<StockAdminDTO>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String goodsStatus,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) Boolean soldOutOnly,
            @RequestParam(required = false) String sortBy) {
        return Result.ok(goodsClient.adminStockList(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                AdminQuerySupport.text(keyword, 50, "keyword"),
                categoryId,
                AdminQuerySupport.csvTokens(goodsStatus, GOODS_STATUS, "goodsStatus"),
                lowStockOnly,
                soldOutOnly,
                AdminQuerySupport.sortBy(sortBy, SORT_BY, "sellable")));
    }

    /**
     * CN-14 库存调整（绝对值；{@code number < locked} 由 goods 侧 409 {@code STOCK_ADJUST_CONFLICT}）。
     * <p>
     * BR-ADM-03：调整成功后把备注与操作对象补进本次操作日志（{@link OperationLogContext}）。
     * </p>
     *
     * @param body 货品ID + 绝对值库存 + 备注/期望值
     * @return 调整后的库存结果
     */
    @PutMapping("/update")
    public Result<StockUpdateVO> update(@Valid @RequestBody StockUpdateReqDTO body) {
        StockAdminDTO dto = new StockAdminDTO();
        dto.setProductId(body.getProductId());
        dto.setNumber(body.getNumber());
        dto.setExpectNumber(body.getExpectNumber());
        dto.setRemark(body.getRemark());
        StockAdminDTO updated = goodsClient.adminStockUpdate(dto);
        OperationLogContext.mark(body.getRemark(), "sku", body.getProductId());
        return Result.ok(StockUpdateVO.of(updated, body.getProductId(), body.getNumber()));
    }
}