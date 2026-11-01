package com.litemall.controller;

import com.litemall.client.MarketingClient;
import com.litemall.config.OperationLogContext;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.IdVO;
import com.litemall.domain.dto.NoticeAdminDTO;
import com.litemall.domain.dto.NoticeDTO;
import com.litemall.support.AdminQuerySupport;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端公告接口（R2 范围：CN-34 列表 / CN-35 新建 / CN-36 编辑启停）。
 * </p>
 * <p>
 * {@code litemall_notice} 归 marketing 域：本类不持有任何 Mapper，三个端点分别转发
 * {@code CN-IN-05/06/07}（{@code /inner/market/admin/notice/*}）。校验/幂等/CAS 全在 marketing 侧，
 * admin 只做「分页归一 + 当前 adminId 透传 + 日志」（05 §4.3）。
 * </p>
 * <ul>
 *   <li><b>无 {@code sortOrder}</b>：库无 {@code sort_order} 列，排序固定 {@code add_time DESC, id DESC}（§5.13）；</li>
 *   <li><b>{@code adminName} 恒 null</b>：marketing 无法读 admin 表，不编造（§3.12）；</li>
 *   <li><b>停用 = {@code enabled=false}</b>：公告不提供删除，启停必须写日志，
 *       {@code action} 为「停用公告」/「启用公告」（§3.14 / §5.11）。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/admin/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final MarketingClient marketingClient;

    /**
     * CN-34 公告列表（转发 CN-IN-05）。
     *
     * @param pageNum  页码（默认 1）
     * @param pageSize 每页条数（默认 20，上限 100，超出按 100 不报错）
     * @param keyword  标题模糊关键字（≤50）
     * @param enabled  启用状态筛选（可空）
     * @return 分页公告
     */
    @GetMapping("/list")
    public Result<PageVO<NoticeAdminDTO>> list(@RequestParam(value = "pageNum", required = false) Integer pageNum,
                                               @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                               @RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return Result.ok(marketingClient.adminListNotices(
                AdminQuerySupport.pageNum(pageNum),
                AdminQuerySupport.pageSize(pageSize),
                keyword,
                enabled));
    }

    /**
     * CN-35 新建公告（转发 CN-IN-06）。
     * <p>
     * {@code adminId} 一律取网关上下文（{@link UserContext#getUser()}），<b>绝不用请求体里的值</b>；
     * 请求体里的 {@code id} 原样透传，由 marketing 判定「新建不得传 id」⇒ 400 {@code PARAM_INVALID}。
     * </p>
     *
     * @param body 公告内容（title / content / enabled）
     * @return {@code {id}}
     */
    @PostMapping("/create")
    public Result<IdVO> create(@RequestBody NoticeDTO body) {
        NoticeDTO dto = new NoticeDTO();
        dto.setId(body.getId());
        dto.setTitle(body.getTitle());
        dto.setContent(body.getContent());
        dto.setEnabled(body.getEnabled());
        dto.setAdminId(UserContext.getUser());
        Long id = marketingClient.adminCreateNotice(dto);
        OperationLogContext.mark(null, "notice", id, "新建公告");
        return Result.ok(IdVO.of(id));
    }

    /**
     * CN-36 编辑/启停公告（转发 CN-IN-07）。
     * <p>
     * {@code title}/{@code content}/{@code enabled} 未传即保持原值；{@code updateTime} 回传则作为
     * CAS 基准，被他人改过由 marketing 返回 409 {@code CONCURRENT_MODIFIED}。
     * </p>
     *
     * @param body 公告信息（id 必填）
     * @return {@code {id}}
     */
    @PostMapping("/update")
    public Result<IdVO> update(@RequestBody NoticeDTO body) {
        NoticeDTO dto = new NoticeDTO();
        dto.setId(body.getId());
        dto.setTitle(body.getTitle());
        dto.setContent(body.getContent());
        dto.setEnabled(body.getEnabled());
        dto.setUpdateTime(body.getUpdateTime());
        Long id = marketingClient.adminUpdateNotice(dto);
        // §3.14：启用/停用属「内容停用」类动作，action 落业务文案；仅改标题/正文则回退默认动作
        OperationLogContext.mark(null, "notice", id,
                body.getEnabled() == null ? null : (body.getEnabled() ? "启用公告" : "停用公告"));
        return Result.ok(IdVO.of(id));
    }
}