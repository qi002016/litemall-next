package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.config.OperationLogContext;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.DTO.AdminCreateDTO;
import com.litemall.domain.DTO.AdminResetPwdDTO;
import com.litemall.domain.DTO.AdminStatusDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdminStatusVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.CommonException;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.support.AdminSessionVersion;
import com.litemall.utils.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * <p>
 * 管理端账号管理接口（admin 域自有表 litemall_admin；H5 后原 {@code Admin} 实体已删除）。
 * </p>
 * <p>
 * R2 收口（05-R2 实施口径 §3.15 / §5.12 / §5.14）：
 * </p>
 * <ul>
 *   <li>CN-37 {@code POST /status}：账号启停，固定校验顺序 + 目标态幂等 CAS + 禁用后
 *       {@code INCR auth:ver:{id}}（旧会话立即失效，Redis 失败不得静默成功）；</li>
 *   <li>CI-04 {@code PUT /reset-password}：路径/方法/入参/出参不变，补 {@code 404} 与
 *       {@code INCR auth:ver}（BR-ADM-05「重置后旧会话失效」）；</li>
 *   <li>CI-01 出参补 {@code statusText}（§1.5.1 字典）；CI-02 唯一键冲突改 {@code 409
 *       ADMIN_USERNAME_DUPLICATED}（§6 现行缺陷）；</li>
 *   <li>§5.14：不能删除/禁用自己；D-35：不能把最后一个启用账号禁用/删除。</li>
 * </ul>
 *
 * @since 2026-09-27
 */
@Slf4j
@RestController
@RequestMapping("/admin/account")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AdminSessionVersion sessionVersion;

    @GetMapping("/list")
    public Result<PageVO<AdminAccount>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        Page<AdminAccount> p = adminAccountMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AdminAccount>()
                        .eq(AdminAccount::getDeleted, false)
                        .like(StringUtils.hasText(username), AdminAccount::getUsername, username)
                        .orderByDesc(AdminAccount::getId));
        p.getRecords().forEach(a -> {
            a.setPassword(null);
            // §1.5.1：后台账号 status 1 启用 / 0 禁用，出参附 statusText
            a.setStatusText(AdminStatusVO.text(a.getStatus()));
        });
        return Result.ok(PageVO.of(p));
    }

    @PostMapping("/create")
    public Result<Void> create(@Valid @RequestBody AdminCreateDTO body) {
        String username = body.getUsername();
        AdminAccount exists = adminAccountMapper.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getUsername, username).eq(AdminAccount::getDeleted, false));
        if (exists != null) {
            throw new CommonException("账号名已存在", 409, "ADMIN_USERNAME_DUPLICATED");
        }
        AdminAccount a = new AdminAccount();
        a.setUsername(username);
        a.setPassword(passwordEncoder.encode(body.getPassword()));
        a.setAvatar(body.getAvatar());
        a.setStatus(AdminStatusVO.ENABLED);
        a.setAddTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        a.setDeleted(false);
        try {
            adminAccountMapper.insert(a);
        } catch (DuplicateKeyException e) {
            // §6：uk_admin_username(username)（不含 deleted）是最终裁判，并发创建必须 409 而不是 500
            throw new CommonException("账号名已存在", 409, "ADMIN_USERNAME_DUPLICATED");
        }
        return Result.ok();
    }

    /**
     * CI-03 编辑账号（§6.5）。
     * <p>{@code password} 传空 ⇒ 不改密码（原口径）；但当 {@code id == 当前登录账号}
     * 且 {@code password} 非空时，视为<b>自助改密</b>：必须带 {@code oldPassword} 并通过校验，
     * 成功后 {@code INCR auth:ver:{id}} 使该账号旧 token 立即失效（§5.12 三处 INCR 之一）。
     * 管理员改他人密码仍走 CI-04 {@code /reset-password}，本方法对其静默忽略 {@code password}。</p>
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody AdminAccount a) {
        boolean self = a.getId() != null && a.getId().equals(UserContext.getUser());
        if (self && StringUtils.hasText(a.getPassword())) {
            return changeOwnPassword(a);
        }
        // §6.5：password 传空即不改；非自助改密一律走 CI-04，这里保持原有「置空忽略」口径
        a.setPassword(null);
        a.setOldPassword(null);
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        return Result.ok();
    }

    /**
     * 自助改密：校验旧密码 → INCR auth:ver → 改库。
     * <p>先 INCR 再改库：Redis 不可用时抛 500，库内口令保持不变（与 CI-04 同一顺序）。</p>
     */
    private Result<Void> changeOwnPassword(AdminAccount a) {
        String newPassword = a.getPassword();
        if (newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BadRequestException("新密码长度需为 6-32 位", BadRequestException.PARAM_INVALID);
        }
        AdminAccount current = adminAccountMapper.selectById(a.getId());
        if (current == null || Boolean.TRUE.equals(current.getDeleted())) {
            throw new CommonException("账号不存在", 404, "RESOURCE_NOT_FOUND");
        }
        if (!StringUtils.hasText(a.getOldPassword())
                || !passwordEncoder.matches(a.getOldPassword(), current.getPassword())) {
            throw new BadRequestException("原密码不正确", BadRequestException.PARAM_INVALID);
        }
        OperationLogContext.mark(null, "admin", a.getId(), "修改密码");
        sessionVersion.increase(a.getId());
        a.setPassword(passwordEncoder.encode(newPassword));
        a.setOldPassword(null);
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        log.info("后台账号 {} 自助改密成功，旧会话已失效", a.getId());
        return Result.ok();
    }

    /**
     * CN-37：账号启停（固定校验顺序见 §3.15）。
     *
     * @param body {@code id} 必填、{@code status} 必填（1 启用 / 0 禁用）
     * @return {@code {id, status, statusText}}
     */
    @PostMapping("/status")
    public Result<AdminStatusVO> status(@Valid @RequestBody AdminStatusDTO body) {
        Long id = body.getId();
        Integer target = body.getStatus();
        // 1. 参数：status ∈ {0,1}、id 非空（注解已挡一层，直接调用时仍需显式兜底）
        if (id == null) {
            throw new BadRequestException("账号ID不能为空", BadRequestException.PARAM_INVALID);
        }
        if (target == null || (target != AdminStatusVO.ENABLED && target != AdminStatusVO.DISABLED)) {
            throw new BadRequestException("账号状态只能是 0 或 1", BadRequestException.PARAM_INVALID);
        }
        String action = target == AdminStatusVO.DISABLED ? "禁用账号" : "启用账号";
        // 审计：动作/对象先挂上，后续任何失败也按「尝试禁用/启用」记失败日志（§5.11）
        OperationLogContext.mark(null, "admin", id, action);

        // 2. 不能禁用自己（§5.14）
        Long currentAdminId = UserContext.getUser();
        if (target == AdminStatusVO.DISABLED && id.equals(currentAdminId)) {
            throw new CommonException("不能禁用自己", 403, "ADMIN_SELF_FORBIDDEN");
        }

        // 3. 目标账号存在（deleted=0）
        AdminAccount account = adminAccountMapper.selectById(id);
        if (account == null || Boolean.TRUE.equals(account.getDeleted())) {
            throw new CommonException("账号不存在", 404, "RESOURCE_NOT_FOUND");
        }
        int currentStatus = account.getStatus() == null ? AdminStatusVO.ENABLED : account.getStatus();

        // 4. 目标态幂等：已是目标状态 ⇒ 200，不改库、也不重复 INCR（重复失效会话无意义）
        if (currentStatus == target) {
            return Result.ok(AdminStatusVO.of(id, target));
        }

        // 5. D-35：不能把最后一个启用账号禁用（默认实现，成本低）
        if (target == AdminStatusVO.DISABLED && currentStatus == AdminStatusVO.ENABLED) {
            assertNotLastEnabled(id);
        }

        // 4'. 目标态幂等 CAS：affected=0 表示被并发改过，回读判定是「已达目标态」还是「行不存在」
        int affected = adminAccountMapper.update(null, new LambdaUpdateWrapper<AdminAccount>()
                .set(AdminAccount::getStatus, target)
                .set(AdminAccount::getUpdateTime, LocalDateTime.now())
                .eq(AdminAccount::getId, id)
                .eq(AdminAccount::getDeleted, false)
                .ne(AdminAccount::getStatus, target));
        if (affected == 0) {
            AdminAccount latest = adminAccountMapper.selectById(id);
            if (latest == null || Boolean.TRUE.equals(latest.getDeleted())) {
                throw new CommonException("账号不存在", 404, "RESOURCE_NOT_FOUND");
            }
            int latestStatus = latest.getStatus() == null ? AdminStatusVO.ENABLED : latest.getStatus();
            if (latestStatus == target) {
                return Result.ok(AdminStatusVO.of(id, target));
            }
            throw new CommonException("操作失败，请重试", 500);
        }

        // 6. 禁用成功 ⇒ 旧会话立即失效；Redis 不可用 ⇒ 500「操作失败，请重试」（不得静默成功）
        if (target == AdminStatusVO.DISABLED) {
            sessionVersion.increase(id);
        }
        return Result.ok(AdminStatusVO.of(id, target));
    }

    /**
     * CI-04：重置账号口令（路径/方法/入参/出参不变，§3.15 末尾）。
     * <p>
     * 新增：{@code 404}（账号不存在）与 {@code INCR auth:ver}（BR-ADM-05 重置后旧会话失效）。
     * 先失效旧会话、再改库：Redis 不可用时返回 500 且口令不变，避免「改成功了却告诉调用方失败」。
     * </p>
     */
    @PutMapping("/reset-password")
    public Result<Void> resetPwd(@Valid @RequestBody AdminResetPwdDTO body) {
        AdminAccount account = adminAccountMapper.selectById(body.getId());
        if (account == null || Boolean.TRUE.equals(account.getDeleted())) {
            throw new CommonException("账号不存在", 404, "RESOURCE_NOT_FOUND");
        }
        sessionVersion.increase(body.getId());
        AdminAccount a = new AdminAccount();
        a.setId(body.getId());
        a.setPassword(passwordEncoder.encode(body.getPassword()));
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        OperationLogContext.mark(null, "admin", body.getId(), "重置密码");
        return Result.ok();
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) {
        // §5.14：不能删除自己；D-35：不能删掉最后一个启用账号（与 CN-37 同口径）
        Long currentAdminId = UserContext.getUser();
        if (id.equals(currentAdminId)) {
            throw new CommonException("不能删除自己", 403, "ADMIN_SELF_FORBIDDEN");
        }
        AdminAccount account = adminAccountMapper.selectById(id);
        if (account != null && !Boolean.TRUE.equals(account.getDeleted())
                && Integer.valueOf(AdminStatusVO.ENABLED).equals(account.getStatus())) {
            assertNotLastEnabled(id);
        }
        OperationLogContext.mark(null, "admin", id, "删除账号");
        AdminAccount a = new AdminAccount();
        a.setId(id);
        a.setDeleted(true);
        a.setUpdateTime(LocalDateTime.now());
        adminAccountMapper.updateById(a);
        return Result.ok();
    }

    /**
     * D-35：目标账号是按「启用」口径统计的最后一个启用账号时拒绝操作。
     *
     * @param targetId 待禁用/删除的账号 ID（已确认当前为启用）
     */
    private void assertNotLastEnabled(Long targetId) {
        Long others = adminAccountMapper.selectCount(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getDeleted, false)
                .eq(AdminAccount::getStatus, AdminStatusVO.ENABLED)
                .ne(AdminAccount::getId, targetId));
        if (others == null || others == 0) {
            throw new CommonException("系统至少需保留一个启用账号", 409, "ADMIN_LAST_ENABLED");
        }
    }
}