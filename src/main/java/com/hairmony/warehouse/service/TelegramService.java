package com.hairmony.warehouse.service;

import com.hairmony.warehouse.config.TelegramConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private final RestClient telegramRestClient;
    private final TelegramConfig telegramConfig;
    private final Environment environment;

    public void sendMessage(Long chatId, String text) {
        try {
            telegramRestClient.post()
                    .uri("/sendMessage")
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", text,
                            "parse_mode", "HTML"
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram: sent message to chatId={}", chatId);
        } catch (Exception e) {
            log.error("Telegram: failed to send message to chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessageNoPreview(Long chatId, String text) {
        try {
            telegramRestClient.post()
                    .uri("/sendMessage")
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", text,
                            "parse_mode", "HTML",
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram: sent message (no preview) to chatId={}", chatId);
        } catch (Exception e) {
            log.error("Telegram: failed to send message to chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void sendMessageWithButtons(Long chatId, String text, List<List<Map<String, String>>> inlineKeyboard) {
        try {
            telegramRestClient.post()
                    .uri("/sendMessage")
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", text,
                            "parse_mode", "HTML",
                            "reply_markup", Map.of("inline_keyboard", inlineKeyboard)
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram: sent message with buttons to chatId={}", chatId);
        } catch (Exception e) {
            log.error("Telegram: failed to send message with buttons to chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void editMessageText(Long chatId, Integer messageId, String newText) {
        try {
            telegramRestClient.post()
                    .uri("/editMessageText")
                    .body(Map.of(
                            "chat_id", chatId,
                            "message_id", messageId,
                            "text", newText,
                            "parse_mode", "HTML",
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Telegram: failed to edit message text chatId={} msgId={}: {}", chatId, messageId, e.getMessage());
        }
    }

    public void editMessageReplyMarkup(Long chatId, Integer messageId) {
        try {
            telegramRestClient.post()
                    .uri("/editMessageReplyMarkup")
                    .body(Map.of(
                            "chat_id", chatId,
                            "message_id", messageId,
                            "reply_markup", Map.of("inline_keyboard", List.of())
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Telegram: failed to remove buttons chatId={} msgId={}: {}", chatId, messageId, e.getMessage());
        }
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        try {
            telegramRestClient.post()
                    .uri("/answerCallbackQuery")
                    .body(Map.of(
                            "callback_query_id", callbackQueryId,
                            "text", text
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Telegram: failed to answer callback {}: {}", callbackQueryId, e.getMessage());
        }
    }

    public void sendVideoWithCaption(Long chatId, String fileId, String caption) {
        try {
            telegramRestClient.post()
                    .uri("/sendVideo")
                    .body(Map.of("chat_id", chatId, "video", fileId,
                                 "caption", caption, "parse_mode", "HTML"))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram: sent video to chatId={}", chatId);
        } catch (Exception e) {
            log.error("Telegram: failed to send video to chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void sendPhotoWithCaption(Long chatId, String fileId, String caption) {
        try {
            telegramRestClient.post()
                    .uri("/sendPhoto")
                    .body(Map.of("chat_id", chatId, "photo", fileId,
                                 "caption", caption, "parse_mode", "HTML"))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram: sent photo to chatId={}", chatId);
        } catch (Exception e) {
            log.error("Telegram: failed to send photo to chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void sendMasterMessage(String text) {
        Long masterChatId = telegramConfig.getMaster().getChatId();
        if (masterChatId == null) {
            log.warn("Telegram: TELEGRAM_MASTER_CHAT_ID not configured, skipping master notification");
            return;
        }
        sendMessage(masterChatId, text);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWebhook() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!isProd) {
            log.warn("Telegram: skipping webhook registration (not prod profile)");
            return;
        }
        String webhookUrl = "https://otchenashhair-warehouse.fly.dev/telegram/webhook";
        try {
            telegramRestClient.post()
                    .uri("/setWebhook")
                    .body(Map.of(
                            "url", webhookUrl,
                            "secret_token", telegramConfig.getWebhook().getSecret(),
                            "allowed_updates", List.of("message", "callback_query")
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram: webhook registered at {}", webhookUrl);
        } catch (Exception e) {
            log.error("Telegram: failed to register webhook: {}", e.getMessage());
        }
    }
}
