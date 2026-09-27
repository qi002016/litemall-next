package com.litemall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PO.Storage;
import com.litemall.domain.VO.StorageVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.StorageMapper;
import com.litemall.service.IStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * 文件存储服务 实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
public class StorageServiceImpl extends ServiceImpl<StorageMapper, Storage> implements IStorageService {

    /** L7：`litemall_storage.size` 的 DB 列是 int，故任何配置上限都不得超过它 */
    private static final long SIZE_HARD_LIMIT = Integer.MAX_VALUE;

    /** L7：扩展名只允许纯字母数字，杜绝 "png/../../evil" 这类借扩展名做目录穿越 */
    private static final Pattern SAFE_EXT = Pattern.compile("^[a-z0-9]{1,10}$");

    private final String storageDir;
    private final long maxSize;
    private final Set<String> allowedExtensions;

    /**
     * L7：由字段注入（{@code @Value} 直接标注在字段上）改为构造注入——
     * 配置项成为不可变依赖，且单元测试无需再用反射塞私有字段。
     */
    public StorageServiceImpl(
            @Value("${hm.storage.dir:./storage}") String storageDir,
            @Value("${hm.storage.max-size:10485760}") long maxSize,
            @Value("${hm.storage.allowed-extensions:jpg,jpeg,png,gif,webp,bmp,pdf}") String allowedExtensions) {
        this.storageDir = storageDir;
        this.maxSize = (maxSize <= 0 || maxSize > SIZE_HARD_LIMIT) ? SIZE_HARD_LIMIT : maxSize;
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("上传文件不能为空");
        }

        long size = file.getSize();
        // L7：上限校验必须在落盘之前，否则磁盘先被写满再报错
        if (size > maxSize) {
            throw new BadRequestException("文件过大，单文件上限 " + maxSize + " 字节");
        }

        // 只取文件名部分：原实现直接用 getOriginalFilename()，其中的路径会被存进 name 列
        String originalName = basename(file.getOriginalFilename());
        String ext = extensionOf(originalName);
        if (ext.isEmpty()) {
            throw new BadRequestException("文件缺少扩展名，无法识别类型");
        }
        if (!SAFE_EXT.matcher(ext).matches() || !allowedExtensions.contains(ext)) {
            throw new BadRequestException("不支持的文件类型：" + ext + "，允许：" + allowedExtensions);
        }
        // MIME 二次校验：Content-Type 由客户端提供、可伪造，仅作纵深防御，不作为唯一判据
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (!ct.startsWith("image/") && !ct.equals("application/pdf")) {
                throw new BadRequestException("不支持的文件类型：" + contentType);
            }
        }

        String key = IdUtil.fastSimpleUUID() + "." + ext;
        try {
            Path dir = Paths.get(storageDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(key);
            file.transferTo(target.toAbsolutePath());
        } catch (IOException e) {
            throw new BadRequestException("文件保存失败");
        }

        LocalDateTime now = LocalDateTime.now();
        Storage storage = new Storage()
                .setKey(key)
                .setName(originalName)
                .setType(ext)
                .setSize(toIntSize(size))
                .setUrl("/storage/" + key)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(storage);

        StorageVO vo = new StorageVO();
        vo.setUrl(storage.getUrl());
        vo.setName(originalName);
        vo.setSize(size);
        return vo;
    }

    /** L7：把 long 转 int 显式化——溢出时抛业务异常，而不是像原 `(int)` 强转那样静默变成负数 */
    private static int toIntSize(long size) {
        try {
            return Math.toIntExact(size);
        } catch (ArithmeticException e) {
            throw new BadRequestException("文件过大，超出存储大小字段上限");
        }
    }

    private static String basename(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String normalized = name.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return base.isBlank() ? "unnamed" : base;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}