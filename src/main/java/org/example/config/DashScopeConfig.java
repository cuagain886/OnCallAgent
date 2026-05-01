package org.example.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * OpenAI API 配置（NVIDIA 兼容接口）
 * 用于配置超时时间等参数
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.openai.chat.options.timeout:180000}")
    private long timeout;

    /**
     * 配置 RestClient.Builder，设置超时时间
     * Spring AI OpenAI 会自动使用这个 Bean
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeout))
                .readTimeout(Duration.ofMillis(timeout))
                .writeTimeout(Duration.ofMillis(timeout))
                .callTimeout(Duration.ofMillis(timeout))
                .build();

        return RestClient.builder()
                .requestFactory(new OkHttp3ClientHttpRequestFactory(okHttpClient));
    }
}
