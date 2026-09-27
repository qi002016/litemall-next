package com.litemall.controller;

import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.GoodsProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1 验收项「库存不足回滚」（M2）集成测试。
 * <p>
 * 用真实 MySQL 验证批量扣减的事务边界：任一货品库存不足时必须整批回滚，
 * 不能出现"前面的货品已扣、后面的失败"的部分扣减。测试数据为临时插入行，结束即物理删除。
 */
@SpringBootTest
class StockDeductBatchTest {

    /** 哨兵商品ID，避免与真实商品数据关联（该表无外键约束） */
    private static final long TEST_GOODS_ID = 999999;

    @Autowired
    private GoodsInnerController goodsInnerController;

    @Autowired
    private GoodsProductMapper goodsProductMapper;

    @Test
    @DisplayName("验收：批量扣减中任一货品库存不足 → 整批回滚，不出现部分扣减")
    void insufficientStock_rollsBackWholeBatch() {
        GoodsProduct enough = insertProduct(10);
        GoodsProduct scarce = insertProduct(1);
        try {
            StockChangeDTO dto = change(item(enough.getId(), 5), item(scarce.getId(), 5));

            assertThatThrownBy(() -> goodsInnerController.deductStockBatch(dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("库存不足");

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

            goodsInnerController.deductStockBatch(dto);

            assertThat(goodsProductMapper.selectById(first.getId()).getNumber()).isEqualTo(7);
            assertThat(goodsProductMapper.selectById(second.getId()).getNumber()).isZero();
        } finally {
            goodsProductMapper.deleteById(first.getId());
            goodsProductMapper.deleteById(second.getId());
        }
    }

    private GoodsProduct insertProduct(int number) {
        GoodsProduct product = new GoodsProduct()
                .setGoodsId(TEST_GOODS_ID)
                .setSpecifications("[]")
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