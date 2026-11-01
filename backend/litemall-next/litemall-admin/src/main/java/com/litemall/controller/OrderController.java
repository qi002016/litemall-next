package com.litemall.controller;

import com.litemall.client.OrderClient;
import com.litemall.domain.DTO.OrderCloseExpiredReqDTO;
import com.litemall.domain.DTO.OrderCloseReqDTO;
import com.litemall.domain.DTO.OrderShipReqDTO;
import com.litemall.domain.DTO.OrderUpdateShipReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.support.AdminQuerySupport;
import com.litemall.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 管理端订单接口（R1 范围：CI-26 / CN-33 / CI-27 / CN-10 / CN-11 / CN-12）。
 * </p>
 * <p>
 * H5：本类不持有任何 Mapper，所有读写出入 order 服务的 {@code /inner/order/admin/**}；
 * 状态合法性与流转（102→201、101→103、仅 201 可改物流）、退款、库存回补全部在 order 侧，
 * admin 只做「入参校验 + 转发 + 权限」（03 §6.2）。
 * </p>
 * <p>
 * 与改造前的差异：
 * </p>
 * <ul>
 *   <li>列表改用完整筛选签名 {@link OrderClient#adminOrderList}，并修复「{@code status} 传
 *       {@code canceled}/{@code completed} 等标识被静默忽略、返回全部订单」的缺陷——现在
 *       入参按 07 §1.1 的出参标识做白名单校验，非法标识直接 400；</li>
 *   <li>列表排序默认值按 P-11：<b>订单列表默认 {@code addTime desc}（新单在前）</b>，
 *       与其它列表（06 §3.1 默认升序）不同；显式传 {@code isAsc} 时以入参为准（D3 修复）；</li>
 *   <li>新增 {@code GET /admin/order/{id}}（CN-33，复用 order 侧详情）；</li>
 *   <li>新增 {@code close}（CN-10）、{@code close-expired}（CN-11）、{@code update-ship}（CN-12）；</li>
 *   <li>下线 {@code POST /admin/order/refund}（CX-02）：退款一律走售后单（CN-07）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class OrderController {

    /** 07 §1.1 订单主状态出参标识（含 R1 只读存量 refund/refunded） */
    private static final Set<String> ORDER_STATUS = Set.of(
            "unpaid", "undelivered", "shipped", "confirmed", "completed", "canceled", "refund", "refunded");

    /**
     * 07 §1.2 售后状态出参标识（8 态）。
     * <p>
     * 第 8 态在契约文档里写作 {@code canceled}（单 l），order 侧 {@code AftersaleStatus.ident()}
     * 实际输出 {@code cancelled}（双 l）。为免前端按文档传参被打回，这里两者都收，
     * 并在透传前统一归一为 order 侧口径（见 {@link #afterSaleStatusFilter(String)}）。
     * </p>
     */
    private static final Set<String> AFTERSALE_STATUS = Set.of(
            "applied", "waitReturn", "waitReceive", "refunding", "completed", "closed", "rejected",
            "canceled", "cancelled");

    /** 06 §3.1 排序白名单（07 §3.1：addTime 默认 | orderAmount） */
    private static final Set<String> SORT_BY = Set.of("addTime", "orderAmount");

    private final OrderClient orderClient;

    /**
     * CI-26 订单列表。
     *
     * @param pageNum         页码（≥1，默认 1）
     * @param pageSize        每页条数（默认 20，上限 100）
     * @param status          订单状态标识，逗号分隔（unpaid/undelivered/.../refunded）
     * @param aftersaleStatus 售后状态标识，逗号分隔（applied/.../canceled）
     * @param orderSn         订单号精确匹配
     * @param keyword         关键字（订单号/收货人/手机号，≤64）
     * @param userId          用户ID
     * @param addTimeMin      下单时间下限 yyyy-MM-dd HH:mm:ss
     * @param addTimeMax      下单时间上限 yyyy-MM-dd HH:mm:ss
     * @param sortBy          addTime（默认）| orderAmount
     * @param isAsc           是否升序（<b>默认 false</b>：按 P-11 订单列表默认 {@code addTime desc}，
     *                        新单在前，避免待发货积压时运营漏单；显式传值以入参为准）
     * @return 分页订单行
     */
    @GetMapping("/list")
    public Result<PageVO<OrderAdminDTO>> list(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String aftersaleStatus,
            @RequestParam(required = false) String orderSn,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String addTimeMin,
            @RequestParam(required = false) String addTimeMax,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Boolean isAsc) {
        return Result.ok(orderClient.adminOrderList(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                AdminQuerySupport.csvTokens(status, ORDER_STATUS, "status"),
                afterSaleStatusFilter(aftersaleStatus),
                AdminQuerySupport.text(orderSn, "orderSn"),
                AdminQuerySupport.text(keyword, "keyword"),
                userId,
                AdminQuerySupport.dateTime(addTimeMin, "addTimeMin"),
                AdminQuerySupport.dateTime(addTimeMax, "addTimeMax"),
                AdminQuerySupport.sortBy(sortBy, SORT_BY, "addTime"),
                AdminQuerySupport.bool(isAsc, false)));
    }

    /**
     * CN-33 订单详情（含订单行与行级售后状态）。
     *
     * @param id 订单ID
     * @return 详情；不存在由 order 侧 404 RESOURCE_NOT_FOUND
     */
    @GetMapping("/{id}")
    public Result<OrderAdminDetailDTO> detail(@PathVariable("id") Long id) {
        return Result.ok(orderClient.adminOrderDetail(id));
    }

    /**
     * CI-27 发货（102 → 201；重复发货由 order 侧 409）。
     *
     * @param body 订单ID + 快递公司 + 快递单号
     * @return 空
     */
    @PostMapping("/ship")
    public Result<Void> ship(@Valid @RequestBody OrderShipReqDTO body) {
        OrderShipDTO dto = new OrderShipDTO();
        dto.setOrderId(body.getOrderId());
        dto.setShipChannel(body.getShipChannel());
        dto.setShipSn(body.getShipSn());
        orderClient.adminShip(dto);
        return Result.ok();
    }

    /**
     * CN-10 立即关单（101 → 103，close_type=3；非 101 由 order 侧 409）。
     *
     * @param body 订单ID + 关单原因（可空）
     * @return 空
     */
    @PostMapping("/close")
    public Result<Void> close(@Valid @RequestBody OrderCloseReqDTO body) {
        OrderCloseDTO dto = new OrderCloseDTO();
        dto.setOrderId(body.getOrderId());
        dto.setReason(body.getReason());
        dto.setAdminId(UserContext.getUser());
        orderClient.adminClose(dto);
        return Result.ok();
    }

    /**
     * CN-11 超时关单（单批 1–200，默认 100；dryRun 只扫不写）。
     * <p>
     * 对外收 JSON body，对内 order 侧是 query 参数，此处做一次转换。
     * </p>
     *
     * @param body 批次上限与 dryRun
     * @return 逐单处理结果（部分失败仍 200，见 06 §1.3）
     */
    @PostMapping("/close-expired")
    public Result<CloseExpiredResultDTO> closeExpired(@Valid @RequestBody(required = false) OrderCloseExpiredReqDTO body) {
        OrderCloseExpiredReqDTO req = body == null ? new OrderCloseExpiredReqDTO() : body;
        Integer limit = req.getLimit() == null ? 100 : req.getLimit();
        return Result.ok(orderClient.adminCloseExpired(limit, AdminQuerySupport.bool(req.getDryRun(), false)));
    }

    /**
     * CN-12 修改物流单号（仅 201 且此前未改过；status/次数判定在 order 侧）。
     *
     * @param body 订单ID + 新快递信息 + 原因（可空）
     * @return 空
     */
    @PostMapping("/update-ship")
    public Result<Void> updateShip(@Valid @RequestBody OrderUpdateShipReqDTO body) {
        OrderShipDTO dto = new OrderShipDTO();
        dto.setOrderId(body.getOrderId());
        dto.setShipChannel(body.getShipChannel());
        dto.setShipSn(body.getShipSn());
        dto.setReason(body.getReason());
        orderClient.adminUpdateShip(dto);
        return Result.ok();
    }

    /**
     * 售后状态筛选：白名单校验 + 单 l 归一为 order 侧的双 l 口径。
     *
     * @param raw 逗号分隔的售后状态标识（可空）
     * @return 归一后的逗号串；全空返回 null
     */
    private static String afterSaleStatusFilter(String raw) {
        String csv = AdminQuerySupport.csvTokens(raw, AFTERSALE_STATUS, "aftersaleStatus");
        if (csv == null) {
            return null;
        }
        return Arrays.stream(csv.split(","))
                .map(token -> "canceled".equals(token) ? "cancelled" : token)
                .collect(Collectors.joining(","));
    }
}