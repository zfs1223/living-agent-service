package com.luohuo.flex.im.common.config;

import com.luohuo.flex.im.common.utils.sensitiveword.DFAFilter;
import com.luohuo.flex.im.common.utils.sensitiveword.SensitiveWordBs;
import com.luohuo.flex.im.sensitive.MyWordFactory;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * @author nyh
 */
@Configuration
@Slf4j
public class SensitiveWordConfig {

    @Resource
    private MyWordFactory myWordFactory;

    /**
     * 初始化引导类
     *
     * @return 初始化引导类
     */
    @Bean
    public SensitiveWordBs sensitiveWordBs() {
        int size = myWordFactory.getWordList().size();
        log.info("敏感词初始化加载数量: {}", size);
        return SensitiveWordBs.newInstance()
                .filterStrategy(DFAFilter.getInstance())
                .sensitiveWord(myWordFactory)
                .init();
    }

}
