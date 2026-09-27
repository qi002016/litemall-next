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

    @Mock
    private StorageMapper storageMapper;

    private StorageServiceImpl storageService;

    @TempDir
    Path tempDir;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 为父类 protected 字段，Mockito @InjectMocks 无法注入，显式设置
        storageService = new StorageServiceImpl();
        ReflectionTestUtils.setField(storageService, "baseMapper", storageMapper);
        ReflectionTestUtils.setField(storageService, "storageDir", tempDir.toString());
    }

    /** 探针契约：空文件上传必须拒绝 */
    @Test
    @DisplayName("空文件上传拒绝")
    void upload_emptyFile_rejects() {
        ReflectionTestUtils.setField(storageService, "storageDir", tempDir.toString());
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThrows(BadRequestException.class, () -> storageService.upload(empty));
    }

    @Test
    @DisplayName("正常上传落盘并返回访问URL/名称/大小")
    void upload_ok_persistsFileAndRecord() throws IOException {
        ReflectionTestUtils.setField(storageService, "storageDir", tempDir.toString());
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
}
