package com.litemall.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PO.Storage;
import com.litemall.domain.VO.StorageVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.StorageMapper;
import com.litemall.service.IStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * <p>
 * 文件存储服务 实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class StorageServiceImpl extends ServiceImpl<StorageMapper, Storage> implements IStorageService {

    /** 文件保存目录，默认工作目录下 storage */
    @Value("${hm.storage.dir:./storage}")
    private String storageDir;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) {
            ext = originalName.substring(dot + 1).toLowerCase();
        }
        String key = IdUtil.fastSimpleUUID() + (ext.isEmpty() ? "" : "." + ext);
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
                .setSize((int) file.getSize())
                .setUrl("/storage/" + key)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(storage);

        StorageVO vo = new StorageVO();
        vo.setUrl(storage.getUrl());
        vo.setName(originalName);
        vo.setSize(file.getSize());
        return vo;
    }
}
