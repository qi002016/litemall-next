package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.PayClient;
import com.litemall.constant.AftersaleStatus;
import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.DTO.AftersaleReturnShipDTO;
import com.litemall.domain.DTO.RefundBriefDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.AftersaleApplyVO;
import com.litemall.domain.VO.AftersaleDetailVO;
import com.litemall.domain.VO.AftersaleReturnShipVO;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleRejectDTO;
import com.litemall.domain.dto.AftersaleRejectResultDTO;
import com.litemall.domain.dto.AftersaleSkipReceiveDTO;
import com.litemall.domain.dto.AutoCloseResultDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 售后状态机（R1-03 / 07 §1.2、§4）：申请资格矩阵、8 态 CAS 跃迁、超时自动关闭。
 * <p>口径：全部 CAS，affected=0 → 409；他人售后单 → 404（防探测）；金额整数分。</p>
 */
@ExtendWith(MockitoExtension.class)
class AftersaleStateMachineTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Aftersale.class);
        TableInfoHelper.initTableInfo(assistant, Order.class);
        TableInfoHelper.initTableInfo(assistant, OrderGoods.class);
    }

    private static final Long USER_ID = 1L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderGoodsMapper orderGoodsMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private AftersaleMapper aftersaleMapper;
    @Mock
    private PayClient payClient;
    @Mock
    private GoodsClient goodsClient;
    @Mock
    private MarketingClient marketingClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AftersaleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AftersaleServiceImpl(orderMapper, orderGoodsMapper, orderLogMapper, payClient,
                goodsClient, marketingClient, transactionTemplate);
        ReflectionTestUtils.setField(service, "baseMapper", aftersaleMapper);
        ReflectionTestUtils.setField(service, "entityClass", Aftersale.class);
        ReflectionTestUtils.setField(service, "configuredReturnAddress", "浙江省杭州市西湖区退货仓");
        UserContext.setUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /* ==================== CN-91 申请资格矩阵 ==================== */

    @Test
    @DisplayName("CI-91：已发货退货退款 → 落库 applied，金额按行实付/件数比例算分")
    void applyReturnGoods_success() {
        Order order = order(10L, 201, null);
        OrderGoods line = line(100L, 10L, 2, 0, "59.90");
        when(orderGoodsMapper.selectById(100L)).thenReturn(line);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(aftersaleMapper.selectCount(any())).thenReturn(0L);
        when(aftersaleMapper.insert(any(Aftersale.class))).thenAnswer(inv -> {
            ((Aftersale) inv.getArgument(0)).setId(9001L);
            return 1;
        });

        AftersaleApplyVO vo = service.apply(applyDTO(100L, 2, 1, List.of("https://img/a.png")));

        assertThat(vo.getAftersaleId()).isEqualTo(9001L);
        assertThat(vo.getAftersaleSn()).startsWith("AS");
        assertThat(vo.getStatus()).isEqualTo("applied");
        assertThat(vo.getStatusText()).isEqualTo("申请中");
        assertThat(vo.getTypeText()).isEqualTo("退货退款");
        // 59.90 元 / 2 件 × 1 件 = 29.95 元 = 2995 分
        assertThat(vo.getAmount()).isEqualTo(2995);
        assertThat(vo.getNumber()).isEqualTo(1);
        assertThat(vo.getOrderGoodsId()).isEqualTo(100L);
        assertThat(vo.getApplyTime()).isNotBlank();
    }

    @Test
    @DisplayName("CI-91：未付款订单申请 → 409 ORDER_STATUS_ILLEGAL")
    void applyUnpaid_conflict() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 101, null));

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 1, 1, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("不可申请售后")
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("ORDER_STATUS_ILLEGAL");
    }

    @Test
    @DisplayName("CI-91：已发货未收货申请退货退款 → 409 AFTERSALE_NOT_DELIVERED")
    void applyReturnGoodsBeforeDelivery_conflict() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 102, null));

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 2, 1, List.of("https://img/a.png"))))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_NOT_DELIVERED");
    }

    @Test
    @DisplayName("CI-91：确认收货超过 7 天 → 409 AFTERSALE_WINDOW_CLOSED")
    void applyOutOfWindow_conflict() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 301, LocalDateTime.now().minusDays(8)));

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 1, 1, null)))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_WINDOW_CLOSED");
    }

    @Test
    @DisplayName("CI-91：同行已有未完结售后 → 409 AFTERSALE_EXISTS")
    void applyDuplicateActive_conflict() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 301, LocalDateTime.now().minusDays(1)));
        when(aftersaleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 1, 1, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("已有处理中的售后")
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_EXISTS");
    }

    @Test
    @DisplayName("CI-91：申请件数超可退件数 → 400 AFTERSALE_QTY_EXCEED（扣已退）")
    void applyQtyExceed_badRequest() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 2, 1, "20.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 201, null));
        when(aftersaleMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 1, 2, null)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("AFTERSALE_QTY_EXCEED");
    }

    @Test
    @DisplayName("CI-91：退货退款未上传凭证 → 400 PARAM_INVALID")
    void applyReturnGoodsWithoutImages_badRequest() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 301, LocalDateTime.now().minusDays(1)));

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 2, 1, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("凭证图片");
    }

    @Test
    @DisplayName("CI-91：传了 amount 但与服务端算额不一致 → 400 REFUND_AMOUNT_MISMATCH")
    void applyAmountMismatch_badRequest() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 201, null));
        when(aftersaleMapper.selectCount(any())).thenReturn(0L);
        AftersaleApplyDTO dto = applyDTO(100L, 1, 1, null);
        dto.setAmount(1);

        assertThatThrownBy(() -> service.apply(dto))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("REFUND_AMOUNT_MISMATCH");
    }

    @Test
    @DisplayName("CI-91：type=0（历史存量）入参 → 400 AFTERSALE_TYPE_ILLEGAL")
    void applyLegacyType_badRequest() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 201, null));

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 0, 1, null)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("AFTERSALE_TYPE_ILLEGAL");
    }

    @Test
    @DisplayName("CI-91：他人的订单行 → 403（订单归属明确时可用 403）")
    void applyForeignOrder_forbidden() {
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));
        Order foreign = order(10L, 201, null);
        foreign.setUserId(2L);
        when(orderMapper.selectById(10L)).thenReturn(foreign);

        assertThatThrownBy(() -> service.apply(applyDTO(100L, 1, 1, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    /* ==================== CN-03/04/05/06/09 CAS 跃迁 ==================== */

    @Test
    @DisplayName("CN-03：退货退款审核通过 → 0→1 + 写 return_deadline")
    void approveReturnGoods_toWaitReturn() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.APPLIED, 2));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleApproveDTO dto = new AftersaleApproveDTO();
        dto.setAftersaleId(1L);
        dto.setAdminId(9L);
        dto.setRemark("同意退货");
        AftersaleApproveResultDTO result = service.approve(dto);

        assertThat(result.getStatus()).isEqualTo("waitReturn");
        assertThat(result.getStatusText()).isEqualTo("待寄回");
        assertThat(result.getReturnAddress()).isEqualTo("浙江省杭州市西湖区退货仓");
        assertThat(result.getReturnDeadline()).isNotBlank();
        verify(aftersaleMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("CN-03：仅退款审核通过 → 0→3，返回地址与截止均为 null")
    void approveRefundOnly_toRefunding() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.APPLIED, 1));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleApproveDTO dto = new AftersaleApproveDTO();
        dto.setAftersaleId(1L);
        dto.setAdminId(9L);
        AftersaleApproveResultDTO result = service.approve(dto);

        assertThat(result.getStatus()).isEqualTo("refunding");
        assertThat(result.getReturnAddress()).isNull();
        assertThat(result.getReturnDeadline()).isNull();
    }

    @Test
    @DisplayName("CN-03：重复审核（CAS 未命中）→ 409 AFTERSALE_STATUS_ILLEGAL")
    void approveTwice_conflict() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.WAIT_RETURN, 2));

        AftersaleApproveDTO dto = new AftersaleApproveDTO();
        dto.setAftersaleId(1L);
        dto.setAdminId(9L);

        assertThatThrownBy(() -> service.approve(dto))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_STATUS_ILLEGAL");
        verify(aftersaleMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("CN-03：退货地址既未配置也未传 → 400 PARAM_INVALID")
    void approveWithoutReturnAddress_badRequest() {
        ReflectionTestUtils.setField(service, "configuredReturnAddress", "");
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.APPLIED, 2));

        AftersaleApproveDTO dto = new AftersaleApproveDTO();
        dto.setAftersaleId(1L);
        dto.setAdminId(9L);

        assertThatThrownBy(() -> service.approve(dto))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("PARAM_INVALID");
    }

    @Test
    @DisplayName("CN-04：驳回 → 0→6，理由回显（写入 close_reason 供 C 端详情展示）")
    void reject_toRejected() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.APPLIED, 1));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleRejectDTO dto = new AftersaleRejectDTO();
        dto.setAftersaleId(1L);
        dto.setReason("商品已使用");
        dto.setAdminId(9L);
        AftersaleRejectResultDTO result = service.reject(dto);

        assertThat(result.getStatus()).isEqualTo("rejected");
        assertThat(result.getReason()).isEqualTo("商品已使用");
    }

    @Test
    @DisplayName("CN-04：驳回理由为空 → 400 PARAM_INVALID")
    void rejectWithoutReason_badRequest() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.APPLIED, 1));

        AftersaleRejectDTO dto = new AftersaleRejectDTO();
        dto.setAftersaleId(1L);
        dto.setReason("   ");

        assertThatThrownBy(() -> service.reject(dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("驳回原因");
    }

    @Test
    @DisplayName("CN-05：待收货确认收货 → 2→3 + receive_time")
    void confirmReceive_toRefunding() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.WAIT_RECEIVE, 2));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleIdDTO dto = new AftersaleIdDTO();
        dto.setAftersaleId(1L);
        dto.setAdminId(9L);
        var result = service.confirmReceive(dto);

        assertThat(result.getStatus()).isEqualTo("refunding");
        assertThat(result.getReceiveTime()).isNotBlank();
        assertThat(result.getSkippedReceive()).isFalse();
    }

    @Test
    @DisplayName("CN-05：非待收货确认收货 → 409（不能直接 completed）")
    void confirmReceiveIllegalStatus_conflict() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.APPLIED, 2));

        AftersaleIdDTO dto = new AftersaleIdDTO();
        dto.setAftersaleId(1L);

        assertThatThrownBy(() -> service.confirmReceive(dto))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_STATUS_ILLEGAL");
    }

    @Test
    @DisplayName("CN-06：待寄回跳过收货 → 1→3，skippedReceive=true 且理由必填")
    void skipReceive_toRefunding() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.WAIT_RETURN, 2));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleSkipReceiveDTO dto = new AftersaleSkipReceiveDTO();
        dto.setAftersaleId(1L);
        dto.setReason("用户自寄，无需退货");
        dto.setAdminId(9L);
        var result = service.skipReceive(dto);

        assertThat(result.getStatus()).isEqualTo("refunding");
        assertThat(result.getSkippedReceive()).isTrue();
    }

    @Test
    @DisplayName("CN-09：关闭售后 → 0/1/2→5，退款中不可关闭")
    void close_onlyBeforeRefunding() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.REFUNDING, 1));

        AftersaleCloseDTO dto = new AftersaleCloseDTO();
        dto.setAftersaleId(1L);
        dto.setReason("用户放弃");

        assertThatThrownBy(() -> service.close(dto))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getErrorCode())
                .isEqualTo("AFTERSALE_STATUS_ILLEGAL");

        when(aftersaleMapper.selectById(2L)).thenReturn(aftersale(2L, USER_ID, AftersaleStatus.WAIT_RECEIVE, 1));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        dto.setAftersaleId(2L);
        AftersaleCloseResultDTO result = service.close(dto);
        assertThat(result.getStatus()).isEqualTo("closed");
        assertThat(result.getCloseReason()).isEqualTo("用户放弃");
    }

    /* ==================== CN-17/18 C 端 ==================== */

    @Test
    @DisplayName("CN-17：撤销 0→7；非申请中 → 409")
    void cancel_onlyApplied() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, USER_ID, AftersaleStatus.WAIT_RETURN, 1));
        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("不能撤销");

        when(aftersaleMapper.selectById(2L)).thenReturn(aftersale(2L, USER_ID, AftersaleStatus.APPLIED, 1));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        service.cancel(2L);
        verify(aftersaleMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("CN-17：他人售后单 → 404 AFTERSALE_NOT_FOUND（防探测）")
    void cancelForeign_notFound() {
        when(aftersaleMapper.selectById(1L)).thenReturn(aftersale(1L, 2L, AftersaleStatus.APPLIED, 1));

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getErrorCode())
                .isEqualTo("AFTERSALE_NOT_FOUND");
    }

    @Test
    @DisplayName("CN-18：填写退货物流 → 1→2 + return_ship_time；超期 → 409")
    void returnShip_toWaitReceive() {
        Aftersale valid = aftersale(1L, USER_ID, AftersaleStatus.WAIT_RETURN, 2);
        valid.setReturnDeadline(LocalDateTime.now().plusDays(3));
        when(aftersaleMapper.selectById(1L)).thenReturn(valid);
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        AftersaleReturnShipDTO dto = new AftersaleReturnShipDTO();
        dto.setAftersaleId(1L);
        dto.setReturnShipCompany("顺丰");
        dto.setReturnShipSn("SF1234567890");
        AftersaleReturnShipVO vo = service.returnShip(dto);

        assertThat(vo.getStatus()).isEqualTo("waitReceive");
        assertThat(vo.getReturnShipTime()).isNotBlank();

        Aftersale expired = aftersale(2L, USER_ID, AftersaleStatus.WAIT_RETURN, 2);
        expired.setReturnDeadline(LocalDateTime.now().minusMinutes(1));
        when(aftersaleMapper.selectById(2L)).thenReturn(expired);
        dto.setAftersaleId(2L);
        assertThatThrownBy(() -> service.returnShip(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("寄回截止");
    }

    /* ==================== CN-16 详情 ==================== */

    @Test
    @DisplayName("CN-16：详情 images 恒为数组、退货地址仅 type=2 返回、时间线升序")
    void detail_contractFields() {
        Aftersale a = aftersale(1L, USER_ID, AftersaleStatus.WAIT_RETURN, 2);
        a.setPictures(null);
        a.setHandleTime(LocalDateTime.now().minusHours(2));
        a.setReason("不合适");
        when(aftersaleMapper.selectById(1L)).thenReturn(a);
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 201, null));
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 0, "10.00"));

        AftersaleDetailVO vo = service.detail(1L);

        assertThat(vo.getImages()).isNotNull().isEmpty();
        assertThat(vo.getReturnAddress()).isEqualTo("浙江省杭州市西湖区退货仓");
        assertThat(vo.getTimeline()).isNotEmpty();
        assertThat(vo.getRestockPending()).isFalse();
    }

    /* ==================== CN-IN-03 自动关闭 ==================== */

    @Test
    @DisplayName("CN-IN-03：待寄回超期逐单 CAS 关闭，未命中计入 failed")
    void autoClose_casPerRow() {
        Aftersale expired = aftersale(1L, USER_ID, AftersaleStatus.WAIT_RETURN, 2);
        expired.setReturnDeadline(LocalDateTime.now().minusDays(1));
        Aftersale raced = aftersale(2L, USER_ID, AftersaleStatus.WAIT_RETURN, 2);
        raced.setReturnDeadline(LocalDateTime.now().minusHours(1));
        when(aftersaleMapper.selectList(any())).thenReturn(List.of(expired, raced));
        when(aftersaleMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1, 0);

        AutoCloseResultDTO result = service.autoClose();

        assertThat(result.getClosed()).isEqualTo(1);
        assertThat(result.getFailed()).hasSize(1);
        assertThat(result.getFailed().get(0).getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("CN-IN-03：无命中返回 closed=0（不报 404）")
    void autoClose_emptyOk() {
        when(aftersaleMapper.selectList(any())).thenReturn(List.of());
        AutoCloseResultDTO result = service.autoClose();
        assertThat(result.getClosed()).isZero();
        assertThat(result.getFailed()).isEmpty();
    }

    /* ==================== CN-15 列表 ==================== */

    @Test
    @DisplayName("CN-15：status 传标识与数字混用都接受；非法 token → 400")
    void list_statusTokens() {
        when(aftersaleMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Aftersale> page = inv.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        PageVO<?> page = service.list("applied,1,refunding", 1, 10, "applyTime", false);
        assertThat(page.getTotal()).isZero();

        assertThatThrownBy(() -> service.list("applied,unknown", 1, 10, null, null))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("PARAM_INVALID");
    }

    @Test
    @DisplayName("CN-15：未登录 → 401")
    void list_withoutLogin_unauthorized() {
        UserContext.removeUser();
        assertThatThrownBy(() -> service.list(null, 1, 10, null, null))
                .isInstanceOf(com.litemall.exception.UnauthorizedException.class);
    }

    /* ==================== 后台出参映射（CN-01 / CN-02 / CN-33 缺陷回归） ==================== */

    @Test
    @DisplayName("CN-01：后台列表出参 status 为字符串标识，specText 由 specifications JSON 派生")
    void adminList_mapsStatusIdentAndSpecText() {
        AftersaleAdminDTO row = new AftersaleAdminDTO();
        row.setAftersaleId(4L);
        row.setStatus("0");
        row.setType(AftersaleStatus.TYPE_REFUND_ONLY);
        row.setSpecText("[\"白色\",\"S\"]");
        when(aftersaleMapper.selectAdminPage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        PageVO<AftersaleAdminDTO> page = service.adminList(1, 3, null, null, null, null, null, null, null, null, null);

        assertThat(page.getList()).hasSize(1);
        AftersaleAdminDTO got = page.getList().get(0);
        // 07 §1.2：出参 status 是字符串标识，不是库里的 tinyint 数字串
        assertThat(got.getStatus()).isEqualTo("applied");
        assertThat(got.getStatusText()).isEqualTo("申请中");
        assertThat(got.getSpecText()).isEqualTo("白色 / S");
        assertThat(got.getTypeText()).isEqualTo("仅退款");
    }

    @Test
    @DisplayName("CN-02：后台详情 status 与 order.status 均为字符串标识（07:909-931 不含 refundId）")
    void adminDetail_statusIdents() {
        Aftersale a = aftersale(4L, USER_ID, AftersaleStatus.COMPLETED, AftersaleStatus.TYPE_RETURN_GOODS);
        when(aftersaleMapper.selectById(4L)).thenReturn(a);
        when(orderMapper.selectById(10L)).thenReturn(order(10L, 201, null));
        when(orderGoodsMapper.selectById(100L)).thenReturn(line(100L, 10L, 1, 1, "10.00"));
        RefundBriefDTO refund = new RefundBriefDTO();
        refund.setRefundId(9L);
        refund.setStatus(1);
        refund.setRefundTime(LocalDateTime.now().minusMinutes(5));
        when(aftersaleMapper.selectRefundBrief(4L)).thenReturn(refund);

        AftersaleAdminDetailDTO dto = service.adminDetail(4L);

        assertThat(dto.getStatus()).isEqualTo("completed");
        // 07 §3.2 CN-02 示例 order.status 为标识（"shipped"），库里是 201/102 数字
        assertThat(dto.getOrder().getStatus()).isEqualTo("shipped");
        assertThat(dto.getOrder().getStatusText()).isEqualTo("已发货");
        assertThat(dto.getRefundTime()).isNotBlank();
    }

    /* ==================== 工厂 ==================== */

    private AftersaleApplyDTO applyDTO(Long orderGoodsId, Integer type, Integer number, List<String> images) {
        AftersaleApplyDTO dto = new AftersaleApplyDTO();
        dto.setOrderGoodsId(orderGoodsId);
        dto.setType(type);
        dto.setNumber(number);
        dto.setReason("商品有瑕疵");
        dto.setComment("包装破损");
        dto.setImages(images);
        return dto;
    }

    private Order order(Long id, Integer status, LocalDateTime confirmTime) {
        Order order = new Order();
        order.setId(id);
        order.setOrderSn("SN" + id);
        order.setUserId(USER_ID);
        order.setOrderStatus(status);
        order.setConfirmTime(confirmTime);
        order.setActualPrice(new BigDecimal("59.90"));
        order.setCouponPrice(BigDecimal.ZERO);
        order.setConsignee("张三");
        order.setMobile("13800138000");
        order.setAddress("浙江省杭州市");
        order.setDeleted(false);
        return order;
    }

    private OrderGoods line(Long id, Long orderId, Integer number, Integer refundedNumber, String actualAmount) {
        OrderGoods og = new OrderGoods();
        og.setId(id);
        og.setOrderId(orderId);
        og.setGoodsName("测试商品");
        og.setNumber(number);
        og.setRefundedNumber(refundedNumber);
        og.setRefundStatus(refundedNumber == 0 ? 0 : 1);
        og.setProductId(500L);
        og.setPrice(new BigDecimal("59.90"));
        og.setActualAmount(new BigDecimal(actualAmount));
        og.setSpecifications("[\"红色\",\"M\"]");
        og.setDeleted(false);
        return og;
    }

    private Aftersale aftersale(Long id, Long userId, Integer status, Integer type) {
        Aftersale a = new Aftersale();
        a.setId(id);
        a.setAftersaleSn("AS2026110100000" + id);
        a.setOrderId(10L);
        a.setOrderGoodsId(100L);
        a.setUserId(userId);
        a.setType(type);
        a.setNumber(1);
        a.setReason("商品有瑕疵");
        a.setAmount(new BigDecimal("10.00"));
        a.setStatus(status);
        a.setRestockStatus(AftersaleStatus.RESTOCK_NONE);
        a.setAddTime(LocalDateTime.now().minusDays(1));
        a.setUpdateTime(LocalDateTime.now());
        a.setDeleted(false);
        return a;
    }
}