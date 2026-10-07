package ru.skripov.ai_assistant.dbeaver_proxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ComponentScan("ru.skripov.ai_assistant.dbeaver_proxy")
@PropertySource("classpath:application.properties")
public class DBeaverProxyConfig {
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
