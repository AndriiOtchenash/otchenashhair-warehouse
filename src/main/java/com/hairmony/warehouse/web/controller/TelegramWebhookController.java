package com.hairmony.warehouse.web.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hairmony.warehouse.config.TelegramConfig;
import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.ClientRepository;
import com.hairmony.warehouse.repository.SalonServiceRepository;
import com.hairmony.warehouse.service.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TelegramWebhookController {

    private final TelegramConfig telegramConfig;
    private final TelegramService telegramService;
    private final ClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;
    private final SalonServiceRepository salonServiceRepository;

    private static final String SERVICE_FIRST  = "Консультація первинна";
    private static final String SERVICE_REPEAT = "Консультація повторна";

    private static final String ADDRESS  = "Jana Sebastiana Bacha 11, 50-305 Wrocław";
    private static final String MAPS_URL = "https://www.google.com/maps/dir/?api=1&destination=Jana+Sebastiana+Bacha+11%2C+50-305+Wroc%C5%82aw%2C+Poland";

    @PostMapping("/telegram/webhook")
    @Transactional
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
            @RequestBody Update update) {

        if (!telegramConfig.getWebhook().getSecret().equals(secret)) {
            log.warn("Telegram webhook: invalid secret");
            return ResponseEntity.status(403).build();
        }

        try {
            if (update.message() != null && update.message().text() != null) {
                handleMessage(update.message());
            } else if (update.callbackQuery() != null) {
                handleCallbackQuery(update.callbackQuery());
            }
        } catch (Exception e) {
            log.error("Telegram webhook processing error: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    private void handleMessage(Message message) {
        String text = message.text().trim();
        Long chatId = message.chat().id();

        if (!text.startsWith("/start")) return;

        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            telegramService.sendMessage(chatId, "❌ Link jest nieprawidłowy lub już został użyty.");
            return;
        }

        String token = parts[1].trim();
        Optional<Client> clientOpt = clientRepository.findByTelegramLinkToken(token);

        if (clientOpt.isEmpty()) {
            telegramService.sendMessage(chatId, "❌ Link jest nieprawidłowy lub już został użyty.");
            return;
        }

        Client client = clientOpt.get();
        client.setTelegramChatId(chatId);
        client.setTelegramLinkToken(null);
        // dirty checking — no explicit save()

        telegramService.sendMessage(chatId,
                "✅ <b>Świetnie!</b> Od teraz będziesz otrzymywać przypomnienia o Twoich wizytach.");
        telegramService.sendMasterMessage(
                "🔗 <b>" + client.getName() + "</b> połączył(a) przypomnienia Telegram");

        log.info("Telegram: client {} linked chat_id={}", client.getId(), chatId);
    }

    private void handleCallbackQuery(CallbackQuery cq) {
        String data = cq.data();
        Long chatId = cq.message().chat().id();
        Integer messageId = cq.message().messageId();

        if (data == null || (!data.startsWith("confirm:") && !data.startsWith("cancel:"))) return;

        boolean isConfirm = data.startsWith("confirm:");
        long appointmentId;
        try {
            appointmentId = Long.parseLong(data.substring(data.indexOf(':') + 1));
        } catch (NumberFormatException e) {
            return;
        }

        Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
        if (appointmentOpt.isEmpty()) {
            telegramService.answerCallbackQuery(cq.id(), "Ta wizyta została już przetworzona.");
            return;
        }

        Appointment appointment = appointmentOpt.get();
        if (appointment.getStatus() != AppointmentStatus.PLANNED &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            telegramService.answerCallbackQuery(cq.id(), "Ta wizyta została już przetworzona.");
            telegramService.editMessageReplyMarkup(chatId, messageId);
            return;
        }

        String clientName = appointment.getClient() != null ? appointment.getClient().getName() : "Клієнт";
        String clientPhone = appointment.getClient() != null ? appointment.getClient().getPhone() : "";
        String dateStr = formatDate(appointment.getStartAt());
        String timeStr = formatTime(appointment.getStartAt());
        String serviceStr = resolveServiceName(appointment.getServiceId());

        if (isConfirm) {
            appointment.setStatus(AppointmentStatus.CONFIRMED);
            // status committed — then notify (best-effort)

            String resolvedServiceName = resolveServiceName(appointment.getServiceId());

            telegramService.answerCallbackQuery(cq.id(), "✅ Potwierdzono!");
            telegramService.editMessageReplyMarkup(chatId, messageId);
            telegramService.editMessageText(chatId, messageId,
                    "✅ <b>Wizyta potwierdzona!</b>\n\n" +
                    "📅 " + dateStr + "\n" +
                    "🕐 " + timeStr + "\n" +
                    "✂️ " + resolvedServiceName + "\n" +
                    "📍 " + ADDRESS);
            telegramService.sendMasterMessage(
                    "✅ <b>" + clientName + "</b> potwierdził(a) wizytę\n" +
                    "📅 " + dateStr + " o " + timeStr + " — " + resolvedServiceName);

            boolean filesSent = sendPostConfirmFiles(chatId, resolvedServiceName);
            if (!filesSent) {
                telegramService.sendMessageNoPreview(chatId,
                        "🗺 <a href=\"" + MAPS_URL + "\">Nawiguj →</a>");
            }

            log.info("Telegram: appointment {} confirmed by client via Telegram", appointmentId);
        } else {
            appointment.setStatus(AppointmentStatus.CANCELLED);

            telegramService.answerCallbackQuery(cq.id(), "❌ Anulowano");
            telegramService.editMessageReplyMarkup(chatId, messageId);
            telegramService.editMessageText(chatId, messageId,
                    "❌ <b>Wizyta anulowana.</b>\n📅 " + dateStr + "\n🕐 " + timeStr);
            telegramService.sendMasterMessage(
                    "❌ <b>" + clientName + "</b> anulował(a) wizytę\n" +
                    "📅 " + dateStr + " o " + timeStr + " — " + serviceStr + "\n" +
                    "📱 " + clientPhone);

            log.info("Telegram: appointment {} cancelled by client via Telegram", appointmentId);
        }
    }

    /** Returns true if at least one file was sent (maps link should follow after files). */
    private boolean sendPostConfirmFiles(Long chatId, String serviceName) {
        String videoId     = telegramConfig.getFiles().getHowToFindVideoId();
        String checklistId = telegramConfig.getFiles().getChecklistPhotoId();

        boolean isFirst  = SERVICE_FIRST.equalsIgnoreCase(serviceName);
        boolean isRepeat = SERVICE_REPEAT.equalsIgnoreCase(serviceName);

        if (!isFirst && !isRepeat) return false;

        boolean sent = false;
        if (isFirst && videoId != null && !videoId.isBlank()) {
            telegramService.sendVideoWithCaption(chatId, videoId,
                    "📹 Film: jak do nas dotrzeć");
            sent = true;
        }
        if (checklistId != null && !checklistId.isBlank()) {
            telegramService.sendPhotoWithCaption(chatId, checklistId,
                    "📋 Lista kontrolna: jak przygotować się do konsultacji");
            sent = true;
        }
        if (sent) {
            telegramService.sendMessageNoPreview(chatId,
                    "🗺 <a href=\"" + MAPS_URL + "\">Nawiguj →</a>");
        }
        return sent;
    }

    private static final String[] PL_MONTHS = {
        "stycznia","lutego","marca","kwietnia","maja","czerwca",
        "lipca","sierpnia","września","października","listopada","grudnia"
    };
    private static final String[] PL_DAYS = {
        "niedziela","poniedziałek","wtorek","środa","czwartek","piątek","sobota"
    };

    private String resolveServiceName(Long serviceId) {
        if (serviceId == null) return "Usługa";
        return salonServiceRepository.findById(serviceId)
                .map(s -> s.getName())
                .orElse("Usługa");
    }

    private String formatDate(LocalDateTime dt) {
        return dt.getDayOfMonth() + " " + PL_MONTHS[dt.getMonthValue() - 1] +
               ", " + PL_DAYS[dt.getDayOfWeek().getValue() % 7];
    }

    private String formatTime(LocalDateTime dt) {
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }

    // ── Telegram Update DTOs ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(
            @JsonProperty("update_id")  long updateId,
            @JsonProperty("message")    Message message,
            @JsonProperty("callback_query") CallbackQuery callbackQuery
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("message_id") Integer messageId,
            @JsonProperty("chat")       Chat chat,
            @JsonProperty("text")       String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(
            @JsonProperty("id") Long id
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(
            @JsonProperty("id")      String id,
            @JsonProperty("message") Message message,
            @JsonProperty("data")    String data
    ) {}
}
