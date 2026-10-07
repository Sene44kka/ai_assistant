package ru.skripov.ai_assistant.core.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@ComponentScan("ru.skripov.ai_assistant.core")
@PropertySource("classpath:application.yml")
@EnableJpaAuditing
public class CoreConfig {
}
