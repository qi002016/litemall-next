package com.litemall.controller;

import com.litemall.domain.Result;
import com.litemall.domain.VO.StorageVO;
import com.litemall.service.IStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 文件存储接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@RestController
@RequestMapping("/app/storage")
@RequiredArgsConstructor
@Tag(name = "storage", description = "文件存储相关接口")
public class StorageController {

    private final IStorageService storageService;

    @Operation(summary = "文件上传")
    @PostMapping("/upload")
    public Result<StorageVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(storageService.upload(file));
    }
}
