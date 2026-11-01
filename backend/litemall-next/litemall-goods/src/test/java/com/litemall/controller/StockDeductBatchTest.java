package com.litemall.controller;

import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.mapper.GoodsProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 验收项「库存不足回滚」（M2）集成测试。
 *
 * <p>
 * 用真实 MySQL 验证批量扣减的事务边界：任一货品库存不足时必须整批回滚，
 * 不能出现"前面的货品已扣、后面的失败"的部分扣减。测试数据为临时插入行，结束即物理删除。
 * </p>
 *
 * <p>
 * R1 契约变更（03 §4.7 D-24 / P-31）：库存不足不再抛 400，而是返回
 * {@code DeductBatchResultDTO{deducted:false, items[].shortage}}，HTTP 200。
 * 本测试据此断言返回值 + 数据库未被改动。
 * </p>
 */
@SpringBootTest
class StockDeductBatchTest {

    /** 哨兵商品ID，避免与真实商品数据关联（该表无外键约束） */
    private static final long TEST_GOODS_ID = 999999;

    /**
     * 规格组合序号。
     * <p>
     * R1 数据模型给 {@code litemall_goods_product} 加了两个唯一键
     * （{@code uk_goods_product_combo(goods_id, spec_value_ids)}、
     * {@code uk_goods_product_sku_code(sku_code)}）。同一商品的多个 SKU 必须具有不同的规格组合，
     * 因此夹具不能再用默认空串——否则同一 {@code goods_id} 下插入第二条即撞键。
     * 这里用自增序号保证同一用例内、以及跨次运行都不重复。
     */
    private static final java.util.concurrent.atomic.AtomicInteger SPEC_SEQ =
            new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    private GoodsInnerController goodsInnerController;

    @Autowired
    private GoodsProductMapper goodsProductMapper;

    @Test
    @DisplayName("验收：批量扣减中任一货品库存不足 → 整批回滚 + deducted=false + shortage")
    void insufficientStock_rollsBackWholeBatch() {
        GoodsProduct enough = insertProduct(10);
        GoodsProduct scarce = insertProduct(1);
        try {
            StockChangeDTO dto = change(item(enough.getId(), 5), item(scarce.getId(), 5));

            DeductBatchResultDTO result = goodsInnerController.deductStockBatch(dto);

            // 契约：HTTP 200 + deducted=false，不抛异常（order 侧据此把订单留在 101）
            assertThat(result.isDeducted()).isFalse();
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems()).allMatch(i -> !i.isDeducted());
            // 缺口 = 需要 5 − 现有 1
            assertThat(result.getItems().stream()
                    .filter(i -> i.getProductId().equals(scarce.getId()))
                    .findFirst().orElseThrow().getShortage()).isEqualTo(4);
            // 关键断言：第一个货品虽然扣减成功过，但必须随事务回滚
            assertThat(goodsProductMapper.selectById(enough.getId()).getNumber()).isEqualTo(10);
            assertThat(goodsProductMapper.selectById(scarce.getId()).getNumber()).isEqualTo(1);
        } finally {
            goodsProductMapper.deleteById(enough.getId());
            goodsProductMapper.deleteById(scarce.getId());
        }
    }

    @Test
    @DisplayName("验收：库存充足时批量扣减全部生效（一次 RPC 覆盖多件商品）")
    void sufficientStock_deductsAll() {
        GoodsProduct first = insertProduct(10);
        GoodsProduct second = insertProduct(8);
        try {
            StockChangeDTO dto = change(item(first.getId(), 3), item(second.getId(), 8));

            DeductBatchResultDTO result = goodsInnerController.deductStockBatch(dto);

            assertThat(result.isDeducted()).isTrue();
            assertThat(result.getItems()).allMatch(DeductBatchResultDTO.Item::isDeducted);
            assertThat(goodsProductMapper.selectById(first.getId()).getNumber()).isEqualTo(7);
            assertThat(goodsProductMapper.selectById(second.getId()).getNumber()).isZero();
        } finally {
            goodsProductMapper.deleteById(first.getId());
            goodsProductMapper.deleteById(second.getId());
        }
    }

    private GoodsProduct insertProduct(int number) {
        // 同一 goods_id 下每个 SKU 必须有唯一规格组合与唯一 sku_code（R1 新增的两个唯一键）
        String specValueIds = String.valueOf(SPEC_SEQ.incrementAndGet());
        String skuCode = TEST_GOODS_ID + "-" + specValueIds + "-" + System.nanoTime();
        GoodsProduct product = new GoodsProduct()
                .setGoodsId(TEST_GOODS_ID)
                .setSpecifications("[]")
                .setSpecValueIds(specValueIds)
                .setSkuCode(skuCode)
                .setPrice(new BigDecimal("1.00"))
                .setNumber(number)
                .setAddTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setDeleted(false);
        goodsProductMapper.insert(product);
        return product;
    }

    private StockChangeDTO.Item item(Long productId, int number) {
        StockChangeDTO.Item item = new StockChangeDTO.Item();
        item.setProductId(productId);
        item.setNumber(number);
        return item;
    }

    private StockChangeDTO change(StockChangeDTO.Item... items) {
        StockChangeDTO dto = new StockChangeDTO();
        dto.setItems(List.of(items));
        return dto;
    }
}