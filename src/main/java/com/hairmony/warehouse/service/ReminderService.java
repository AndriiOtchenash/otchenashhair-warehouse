package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.SalonServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private static final List<AppointmentStatus> REMINDER_STATUSES =
            List.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED);

    private static final String[] UA_DAYS = {
            "неділя", "понеділок", "вівторок", "середа", "четвер", "п'ятниця", "субота"
    };
    private static final String[] UA_MONTHS = {
            "січня", "лютого", "березня", "квітня", "травня", "червня",
            "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"
    };

    private final AppointmentRepository appointmentRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final TelegramService telegramService;

    @Transactional
    public String sendReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime dayStart = tomorrow.atStartOfDay();
        LocalDateTime dayEnd   = tomorrow.plusDays(1).atStartOfDay();

        List<Appointment> appointments = appointmentRepository.findAppointmentsForDayReminder(
                dayStart, dayEnd, REMINDER_STATUSES);

        int sent = 0, masterNotified = 0;
        for (Appointment a : appointments) {
            String serviceName = resolveServiceName(a.getServiceId());
            String dateStr = formatDate(a.getStartAt());
            String timeStr = formatTime(a.getStartAt());

            if (a.getClient().getTelegramChatId() != null) {
                String text = "🌿 <b>Нагадуємо: завтра ваш візит!</b>\n" +
                        "📅 " + dateStr + "\n" +
                        "🕐 " + timeStr + "\n" +
                        "✂️ " + serviceName + "\n" +
                        "📍 Jana Sebastiana Bacha 11, 50-305 Wrocław\n\n" +
                        "Будь ласка, підтвердіть візит:";

                List<List<Map<String, String>>> keyboard = List.of(
                        List.of(
                                Map.of("text", "✅ Підтверджую", "callback_data", "confirm:" + a.getId()),
                                Map.of("text", "❌ Скасувати",   "callback_data", "cancel:"  + a.getId())
                        )
                );
                telegramService.sendMessageWithButtons(a.getClient().getTelegramChatId(), text, keyboard);
                sent++;
            } else {
                String masterText = "⚠️ Клієнт без Telegram: <b>" + a.getClient().getName() + "</b>" +
                        (a.getClient().getPhone() != null ? ", " + a.getClient().getPhone() : "") + "\n" +
                        "Візит: " + dateStr + " о " + timeStr + " — " + serviceName + "\n" +
                        "Нагадайте вручну.";
                telegramService.sendMasterMessage(masterText);
                masterNotified++;
            }

            a.setReminder24hSentAt(LocalDateTime.now());
            sleepBriefly();
        }

        String result = "24h: sent=" + sent + " master-notified=" + masterNotified + "\n";
        log.info(result.trim());
        return result;
    }

    private String resolveServiceName(Long serviceId) {
        if (serviceId == null) return "Послуга";
        return salonServiceRepository.findById(serviceId)
                .map(s -> s.getName())
                .orElse("Послуга");
    }

    private String formatDate(LocalDateTime dt) {
        String day = UA_DAYS[dt.getDayOfWeek().getValue() % 7];
        String month = UA_MONTHS[dt.getMonthValue() - 1];
        return dt.getDayOfMonth() + " " + month + ", " + day;
    }

    private String formatTime(LocalDateTime dt) {
        return String.format("%02d:%02d", dt.getHour(), dt.getMinute());
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
