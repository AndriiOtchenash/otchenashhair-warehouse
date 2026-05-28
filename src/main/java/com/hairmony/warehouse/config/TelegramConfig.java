package com.hairmony.warehouse.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "telegram")
@Getter
@Setter
public class TelegramConfig {

    private Bot bot = new Bot();
    private Master master = new Master();
    private Webhook webhook = new Webhook();

    @Getter
    @Setter
    public static class Bot {
        private String token;
        private String username;
    }

    @Getter
    @Setter
    public static class Master {
        private Long chatId;
    }

    @Getter
    @Setter
    public static class Webhook {
        private String secret;
    }

    private Files files = new Files();

    @Getter
    @Setter
    public static class Files {
        private String howToFindVideoId;
        private String checklistPhotoId;
    }

    @Bean
    public RestClient telegramRestClient() {
        return RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + bot.getToken())
                .build();
    }
}
