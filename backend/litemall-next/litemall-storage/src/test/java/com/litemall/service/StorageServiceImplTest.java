package com.litemall.service;

import com.litemall.domain.PO.Storage;
import com.litemall.domain.VO.StorageVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.StorageMapper;
import com.litemall.service.impl.StorageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * <p>
 * 文件存储服务单元测试（纯 Mockito + 临时目录）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    private static final long MAX = 10L * 1024 * 1024;

    @Mock
    private StorageMapper storageMapper;

    private StorageServiceImpl storageService;

    @TempDir
    Path tempDir;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // L7：改为构造注入，不再用反射写私有字段；
        // ServiceImpl 的 baseMapper 仍是父类 protected 字段，只能显式设置。
        storageService = new StorageServiceImpl(tempDir.toString(), MAX, "jpg,jpeg,png,gif,webp,bmp,pdf");
        ReflectionTestUtils.setField(storageService, "baseMapper", storageMapper);
    }

    /** 探针契约：空文件上传必须拒绝 */
    @Test
    @DisplayName("空文件上传拒绝")
    void upload_emptyFile_rejects() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThrows(BadRequestException.class, () -> storageService.upload(empty));
    }

    @Test
    @DisplayName("正常上传落盘并返回访问URL/名称/大小")
    void upload_ok_persistsFileAndRecord() throws IOException {
        byte[] bytes = "hello-litemall".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", bytes);
        doAnswer(inv -> {
            Storage s = inv.getArgument(0);
            s.setId(1);
            return 1;
        }).when(storageMapper).insert(org.mockito.ArgumentMatchers.any(Storage.class));

        StorageVO vo = storageService.upload(file);

        // 落盘
        try (Stream<Path> paths = Files.list(tempDir)) {
            assertTrue(paths.findAny().isPresent(), "文件应写入上传目录");
        }
        assertEquals("avatar.png", vo.getName());
        assertEquals(bytes.length, vo.getSize());
        assertTrue(vo.getUrl().startsWith("/storage/"));
        // 记录
        ArgumentCaptor<Storage> captor = ArgumentCaptor.forClass(Storage.class);
        verify(storageMapper).insert(captor.capture());
        assertEquals("avatar.png", captor.getValue().getName());
        assertEquals("png", captor.getValue().getType());
        assertEquals(bytes.length, captor.getValue().getSize());
        assertEquals(vo.getUrl(), captor.getValue().getUrl());
        assertEquals(false, captor.getValue().getDeleted());
    }

    /** L7：扩展名白名单——不在名单内的类型必须拒绝，且不得落盘 */
    @Test
    @DisplayName("L7 扩展名不在白名单内则拒绝")
    void upload_disallowedExtension_rejects() throws IOException {
        MockMultipartFile exe = new MockMultipartFile("file", "payload.exe", "application/octet-stream", "x".getBytes());

        assertThrows(BadRequestException.class, () -> storageService.upload(exe));
        try (Stream<Path> paths = Files.list(tempDir)) {
            assertFalse(paths.findAny().isPresent(), "被拒绝的文件不应落盘");
        }
    }

    /** L7：无扩展名必须拒绝 */
    @Test
    @DisplayName("L7 无扩展名则拒绝")
    void upload_noExtension_rejects() {
        MockMultipartFile noExt = new MockMultipartFile("file", "noext", "image/png", "x".getBytes());

        assertThrows(BadRequestException.class, () -> storageService.upload(noExt));
    }

    /**
     * L7（安全回归）：扩展名里夹带路径分隔符不能再用于目录穿越。
     * 原实现 ext = substring(lastIndexOf('.')+1) 会把 "png/../../evil" 当成扩展名拼进 key，
     * 使落盘路径逃出 upload 目录；现在扩展名必须匹配 ^[a-z0-9]{1,10}$，且 key 恒为 UUID+"."+ext。
     */
    @Test
    @DisplayName("L7 借扩展名做目录穿越被拒绝")
    void upload_extensionPathTraversal_rejects() {
        MockMultipartFile evil = new MockMultipartFile("file", "evil.png/../../outside", "image/png", "x".getBytes());

        assertThrows(BadRequestException.class, () -> storageService.upload(evil));
    }

    /** L7：超过配置上限的文件必须在上限校验阶段被拒（不落盘） */
    @Test
    @DisplayName("L7 超过大小上限则拒绝且不落盘")
    void upload_oversize_rejects() throws IOException {
        StorageServiceImpl tiny = new StorageServiceImpl(tempDir.toString(), 4L, "png");
        ReflectionTestUtils.setField(tiny, "baseMapper", storageMapper);
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", new byte[10]);

        assertThrows(BadRequestException.class, () -> tiny.upload(big));
        try (Stream<Path> paths = Files.list(tempDir)) {
            assertFalse(paths.findAny().isPresent(), "超限文件不应落盘");
        }
    }

    /** L7：MIME 与扩展名不一致时按 Content-Type 拒绝（纵深防御，非唯一判据） */
    @Test
    @DisplayName("L7 Content-Type 非图片/PDF 则拒绝")
    void upload_badContentType_rejects() {
        MockMultipartFile fake = new MockMultipartFile("file", "a.png", "text/html", "x".getBytes());

        assertThrows(BadRequestException.class, () -> storageService.upload(fake));
    }
}