package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.DTO.AftersaleReturnShipDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.VO.AftersaleApplyVO;
import com.litemall.domain.VO.AftersaleDetailVO;
import com.litemall.domain.VO.AftersaleListItemVO;
import com.litemall.domain.VO.AftersaleReturnShipVO;
import com.litemall.domain.dto.AftersaleAdminDTO;
import com.litemall.domain.dto.AftersaleAdminDetailDTO;
import com.litemall.domain.dto.AftersaleApproveDTO;
import com.litemall.domain.dto.AftersaleApproveResultDTO;
import com.litemall.domain.dto.AftersaleCloseDTO;
import com.litemall.domain.dto.AftersaleCloseResultDTO;
import com.litemall.domain.dto.AftersaleIdDTO;
import com.litemall.domain.dto.AftersaleReceiveResultDTO;
import com.litemall.domain.dto.AftersaleRefundDTO;
import com.litemall.domain.dto.AftersaleRefundResultDTO;
import com.litemall.domain.dto.AftersaleRejectDTO;
import com.litemall.domain.dto.AftersaleRejectResultDTO;
import com.litemall.domain.dto.AftersaleSkipReceiveDTO;
import com.litemall.domain.dto.AutoCloseResultDTO;
import com.litemall.domain.PageVO;

/**
 * 售后域服务（R1-03 / 07 §4）。
 * <p>
 * C 端：CI-91 申请、CN-15 列表、CN-16 详情、CN-17 撤销、CN-18 填寄回单号；
 * 管理端：CN-01~CN-09（对内由 admin 服务转发）；系统：CN-IN-03 待寄回超时关闭。
 * </p>
 */
public interface IAftersaleService extends IService<Aftersale> {

    /* ==================== C 端 ==================== */

    /** CI-91 申请售后（按订单行） */
    AftersaleApplyVO apply(AftersaleApplyDTO dto);

    /** CN-15 我的售后列表（status 逗号多值，sortBy=applyTime|amount） */
    PageVO<AftersaleListItemVO> list(String status, Integer pageNo, Integer pageSize, String sortBy, Boolean isAsc);

    /** CN-16 售后详情（不存在或非本人 → 404） */
    AftersaleDetailVO detail(Long aftersaleId);

    /** CN-17 撤销申请（仅状态 0） */
    void cancel(Long aftersaleId);

    /** CN-18 用户填写退货物流（仅状态 1 且未过截止时间） */
    AftersaleReturnShipVO returnShip(AftersaleReturnShipDTO dto);

    /* ==================== 管理端（CN-01~CN-09） ==================== */

    /** CN-01 管理端售后列表 */
    PageVO<AftersaleAdminDTO> adminList(Integer pageNo, Integer pageSize, String status, Integer type,
                                        String aftersaleSn, String orderSn, String keyword,
                                        String applyTimeMin, String applyTimeMax,
                                        String sortBy, Boolean isAsc);

    /** CN-02 管理端售后详情 */
    AftersaleAdminDetailDTO adminDetail(Long aftersaleId);

    /** CN-03 审核通过（type=1 → refunding；type=2 → waitReturn + 寄回截止时间） */
    AftersaleApproveResultDTO approve(AftersaleApproveDTO dto);

    /** CN-04 驳回（仅状态 0） */
    AftersaleRejectResultDTO reject(AftersaleRejectDTO dto);

    /** CN-05 确认收货（2 → 3，不直接完成） */
    AftersaleReceiveResultDTO confirmReceive(AftersaleIdDTO dto);

    /** CN-06 跳过收货（1/2 → 3，留日志） */
    AftersaleReceiveResultDTO skipReceive(AftersaleSkipReceiveDTO dto);

    /** CN-07 发起退款（两段式编排；回补失败 → 200 + restockPending） */
    AftersaleRefundResultDTO refund(AftersaleRefundDTO dto);

    /** CN-08 重试回补（仅 status=3 且 restock_status=2） */
    AftersaleRefundResultDTO retryRestock(AftersaleIdDTO dto);

    /** CN-09 关闭售后（仅 0/1/2） */
    AftersaleCloseResultDTO close(AftersaleCloseDTO dto);

    /* ==================== 系统任务 ==================== */

    /** CN-IN-03 待寄回超时自动关闭（每小时/每分钟调度；无命中 closed=0） */
    AutoCloseResultDTO autoClose();
}