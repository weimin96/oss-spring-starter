package com.wiblog.oss.config;

import com.wiblog.oss.bean.OssProperties;
import com.wiblog.oss.controller.OssController;
import com.wiblog.oss.service.OssTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置类
 * @author panwm
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = OssProperties.PREFIX, name = "enable", matchIfMissing  = true)
@EnableConfigurationProperties({ OssProperties.class })
public class OssAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OssAutoConfiguration.class);

    /**
     * OSS操作模板
     * @return OSS操作模板
     */
    @Bean
    @ConditionalOnMissingBean(OssTemplate.class)
    public OssTemplate ossTemplate(OssProperties properties) {
        log.info("初始化oss");
        return new OssTemplate(properties);
    }

    /**
     * OSS端点信息
     * @param template oss操作模版
     * @return oss远程服务端点
     */
//    @Bean
//    @ConditionalOnWebApplication
//    @ConditionalOnProperty(prefix = OssProperties.PREFIX, name = "http.enable", matchIfMissing  = true)
//    public OssController ossController(OssTemplate template) {
//        return new OssController(template);
//    }



}
