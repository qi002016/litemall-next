package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminOperationLog;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdminOperationLogVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AdminOperationLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * R2（CI-13）：操作日志筛选/出参单测（05-R2 实施口径 §2.5）。
 */
@ExtendWith(MockitoExtension.class)
class LogControllerTest {

    @Mock
    private AdminOperationLogMapper logMapper;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<AdminOperationLog>> wrapperCaptor;

    @Test
    @DisplayName("全筛选：admin/targetType/targetId/action/status/时间范围全部落到 SQL 条件")
    void allFiltersApplied() {
        givenPage(row());

        LogController controller = new LogController(logMapper);
        Result<PageVO<AdminOperationLogVO>> result = controller.list(1, 20, "ad", "order", "1024",
                "发货", "0,1", "2026-10-01 00:00:00", "2026-11-01 23:59:59");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getTotal()).isEqualTo(1L);

        LambdaQueryWrapper<AdminOperationLog> wrapper = captureWrapper();
        // MyBatis-Plus 的 #{ew.paramNameValuePairs.xxx} 在生成 SQL 片段时才实例化，先取 sqlSegment
        String sqlSegment = wrapper.getSqlSegment();
        Collection<Object> params = wrapper.getParamNameValuePairs().values();
        assertThat(params).contains("order", 1024L, 0, 1);
        assertThat(params).anySatisfy(value ->
                assertThat(String.valueOf(value)).isEqualTo("2026-10-01T00:00"));
        assertThat(sqlSegment).contains("target_type", "target_id", "status IN");
        assertThat(sqlSegment).contains("add_time >=").contains("add_time <=");
        // §2.5.1：排序固定 add_time DESC, id DESC
        assertThat(sqlSegment).contains("ORDER BY add_time DESC,id DESC");
    }

    @Test
    @DisplayName("出参：typeText/statusText 按 D-36 字典，targetId/addTime 字符串化")
    void voMappingFollowsDictionary() {
        givenPage(row());
        LogController controller = new LogController(logMapper);

        AdminOperationLogVO vo = controller.list(null, null, null, null, null, null, null, null, null)
                .getData().getList().get(0);

        assertThat(vo.getId()).isEqualTo("451");
        assertThat(vo.getType()).isEqualTo(1);
        assertThat(vo.getTypeText()).isEqualTo("业务操作");
        assertThat(vo.getStatus()).isZero();
        assertThat(vo.getStatusText()).isEqualTo("成功");
        assertThat(vo.getTargetType()).isEqualTo("order");
        assertThat(vo.getTargetId()).isEqualTo("1024");
        assertThat(vo.getAddTime()).isEqualTo("2026-11-01 18:43:11");
    }

    @Test
    @DisplayName("字典边界：type=2 ⇒ 登录，status=1 ⇒ 失败")
    void dictionaryCoversLoginAndFailure() {
        AdminOperationLog login = row();
        login.setType(2);
        login.setStatus(1);
        givenPage(login);
        LogController controller = new LogController(logMapper);

        AdminOperationLogVO vo = controller.list(null, null, null, null, null, null, null, null, null)
                .getData().getList().get(0);

        assertThat(vo.getTypeText()).isEqualTo("登录");
        assertThat(vo.getStatusText()).isEqualTo("失败");
    }

    @Test
    @DisplayName("targetType 非白名单 ⇒ 400 PARAM_INVALID（不能静默忽略）")
    void invalidTargetTypeRejected() {
        LogController controller = new LogController(logMapper);
        assertThatThrownBy(() -> controller.list(null, null, null, "product", null, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("targetType")
                .extracting(e -> ((BadRequestException) e).getErrorCode())
                .isEqualTo("PARAM_INVALID");
    }

    @Test
    @DisplayName("targetId 非数字 ⇒ 400 PARAM_INVALID")
    void nonNumericTargetIdRejected() {
        LogController controller = new LogController(logMapper);
        assertThatThrownBy(() -> controller.list(null, null, null, null, "abc", null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("targetId");
    }

    @Test
    @DisplayName("status 非法 token ⇒ 400 PARAM_INVALID")
    void invalidStatusRejected() {
        LogController controller = new LogController(logMapper);
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, "2", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status");
    }

    @Test
    @DisplayName("时间格式错 ⇒ 400；dateFrom > dateTo ⇒ 400")
    void dateRangeValidated() {
        LogController controller = new LogController(logMapper);
        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null,
                "2026-11-01", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yyyy-MM-dd HH:mm:ss");

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null,
                "2026-11-02 00:00:00", "2026-11-01 00:00:00"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能晚于");
    }

    @Test
    @DisplayName("分页归一：pageSize>100 按 100，pageNum<1 按 1（§1.4，不报错）")
    void pagingNormalized() {
        givenPage(row());
        LogController controller = new LogController(logMapper);

        controller.list(0, 1000, null, null, null, null, null, null, null);

        Page<AdminOperationLog> page = capturePage();
        assertThat(page.getCurrent()).isEqualTo(1L);
        assertThat(page.getSize()).isEqualTo(100L);
    }

    @Test
    @DisplayName("空集：返回空列表而不是异常")
    void emptyResultIsEmptyList() {
        Page<AdminOperationLog> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(logMapper.selectPage(any(), any())).thenReturn(page);
        LogController controller = new LogController(logMapper);

        Result<PageVO<AdminOperationLogVO>> result =
                controller.list(null, null, null, null, null, null, null, null, null);

        assertThat(result.getData().getList()).isEmpty();
        assertThat(result.getData().getTotal()).isZero();
    }

    private void givenPage(AdminOperationLog... rows) {
        Page<AdminOperationLog> page = new Page<>(1, 20);
        page.setRecords(List.of(rows));
        page.setTotal(rows.length);
        when(logMapper.selectPage(any(), any())).thenReturn(page);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<AdminOperationLog> captureWrapper() {
        org.mockito.Mockito.verify(logMapper).selectPage(any(), wrapperCaptor.capture());
        return wrapperCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Page<AdminOperationLog> capturePage() {
        ArgumentCaptor<Page<AdminOperationLog>> captor =
                ArgumentCaptor.forClass((Class<Page<AdminOperationLog>>) (Class<?>) Page.class);
        org.mockito.Mockito.verify(logMapper).selectPage(captor.capture(), any());
        return captor.getValue();
    }

    private AdminOperationLog row() {
        AdminOperationLog log = new AdminOperationLog();
        log.setId(451L);
        log.setAdmin("admin123");
        log.setIp("127.0.0.1");
        log.setType(1);
        log.setAction("POST /admin/order/ship");
        log.setStatus(0);
        log.setResult("成功");
        log.setComment("发货");
        log.setTargetType("order");
        log.setTargetId(1024L);
        log.setAddTime(LocalDateTime.of(2026, 11, 1, 18, 43, 11));
        log.setDeleted(false);
        return log;
    }
}