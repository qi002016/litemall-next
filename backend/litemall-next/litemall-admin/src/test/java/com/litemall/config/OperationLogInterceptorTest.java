package com.litemall.config;

import com.litemall.domain.AdminOperationLog;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.mapper.AdminOperationLogMapper;
import com.litemall.support.LogErrorCounter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2（CI-13 / D-36）：操作日志写入的字典与失败判定单测。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogInterceptorTest {

    @Mock
    private AdminOperationLogMapper logMapper;
    @Mock
    private AdminAccountMapper adminAccountMapper;
    @Mock
    private LogErrorCounter logErrorCounter;

    @Test
    @DisplayName("业务失败但 HTTP 200：status=1，result 带细分码（不能记成成功）")
    void businessFailureIsRecordedAsFailed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/admin/order/close");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getAttribute(LogFailureMarker.ATTR_FAILED)).thenReturn(Boolean.TRUE);
        when(request.getAttribute(LogFailureMarker.ATTR_ERROR_CODE)).thenReturn("ORDER_NOT_FOUND");

        interceptor().afterCompletion(request, response, null, null);

        AdminOperationLog entry = captureInsert();
        assertThat(entry.getStatus()).isEqualTo(1);
        assertThat(entry.getResult()).isEqualTo("失败(ORDER_NOT_FOUND)");
        assertThat(entry.getType()).isEqualTo(1);
        assertThat(entry.getAction()).isEqualTo("POST /admin/order/close");
        assertThat(entry.getTargetType()).isEqualTo("order");
        assertThat(entry.getAdmin()).isEqualTo("system");
        assertThat(entry.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("成功请求：status=0、result=成功（R1 旧实现是反的，D-36 已翻转）")
    void successIsRecordedAsSuccess() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/admin/notice/create");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);

        interceptor().afterCompletion(request, response, null, null);

        AdminOperationLog entry = captureInsert();
        assertThat(entry.getStatus()).isZero();
        assertThat(entry.getResult()).isEqualTo("成功");
        assertThat(entry.getTargetType()).isEqualTo("notice");
        assertThat(entry.getTargetId()).isZero();
    }

    @Test
    @DisplayName("路径前缀按段边界匹配：/admin/account 归 admin，不被 /admin/ad 吞掉")
    void pathPrefixRespectsSegmentBoundary() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/admin/account/create");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);

        interceptor().afterCompletion(request, response, null, null);

        assertThat(captureInsert().getTargetType()).isEqualTo("admin");
    }

    @Test
    @DisplayName("登录请求不写业务日志（type=2 由认证入口负责）")
    void loginIsSkipped() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/admin/auth/login");

        interceptor().afterCompletion(request, response, null, null);

        verify(logMapper, org.mockito.Mockito.never()).insert(any(AdminOperationLog.class));
    }

    @Test
    @DisplayName("写库失败：WARN + 计数，不抛异常（§5.11 / P-3）")
    void insertFailureCountsInsteadOfThrowing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/admin/notice/create");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getStatus()).thenReturn(200);
        when(logMapper.insert(any(AdminOperationLog.class))).thenThrow(new IllegalStateException("db down"));

        interceptor().afterCompletion(request, response, null, null);

        verify(logErrorCounter).increase();
    }

    private OperationLogInterceptor interceptor() {
        return new OperationLogInterceptor(logMapper, adminAccountMapper, logErrorCounter);
    }

    private AdminOperationLog captureInsert() {
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logMapper).insert(captor.capture());
        return captor.getValue();
    }
}