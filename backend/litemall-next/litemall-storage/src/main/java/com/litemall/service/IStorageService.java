package com.litemall.service;

import com.litemall.domain.VO.StorageVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * 文件存储服务
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IStorageService {

    /**
     * 上传文件：落盘到本地目录并写入存储表
     *
     * @param file 上传文件
     * @return 访问URL/文件名/大小
     */
    StorageVO upload(MultipartFile file);
}
