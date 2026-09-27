package com.litemall.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;

/**
 * <p>
 * storage 服务静态资源映射：/storage/** → 本地上传目录
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Configuration
public class StorageWebConfig implements WebMvcConfigurer {

    @Value("${hm.storage.dir:./storage}")
    private String storageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/storage/**")
                .addResourceLocations("file:" + Paths.get(storageDir).toAbsolutePath() + File.separator);
    }
}
