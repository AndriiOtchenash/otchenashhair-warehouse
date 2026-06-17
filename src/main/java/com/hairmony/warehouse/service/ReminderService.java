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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderService {

    private static final List<AppointmentStatus> REMINDER_STATUSES =
            List.of(AppointmentStatus.PLANNED, AppointmentStatus.CONFIRMED);

    private static final String[] PL_DAYS = {
            "niedziela", "poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota"
    };
    private static final String[] PL_MONTHS = {
            "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
            "lipca", "sierpnia", "września", "października", "listopada", "grudnia"
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
                String text = "🌿 <b>Przypomnienie: jutro Twoja wizyta!</b>\n" +
                        "📅 " + dateStr + "\n" +
                        "🕐 " + timeStr + "\n" +
                        "✂️ " + serviceName + "\n" +
                        "📍 Jana Sebastiana Bacha 11, 50-305 Wrocław\n\n" +
                        "Prosimy o potwierdzenie wizyty:";

                List<List<Map<String, String>>> keyboard = List.of(
                        List.of(
                                Map.of("text", "✅ Potwierdzam", "callback_data", "confirm:" + a.getId()),
                                Map.of("text", "❌ Anuluj",      "callback_data", "cancel:"  + a.getId())
                        )
                );
                telegramService.sendMessageWithButtons(a.getClient().getTelegramChatId(), text, keyboard);
                sent++;
            } else {
                String masterText = "⚠️ Klient bez Telegram: <b>" + a.getClient().getName() + "</b>" +
                        (a.getClient().getPhone() != null ? ", " + a.getClient().getPhone() : "") + "\n" +
                        "Wizyta: " + dateStr + " o " + timeStr + " — " + serviceName + "\n" +
                        "Przypomnij ręcznie.";
                telegramService.sendMasterMessage(masterText);
                masterNotified++;
            }

            a.setReminder24hSentAt(LocalDateTime.now(ZoneId.of("Europe/Warsaw")));
            sleepBriefly();
        }

        String result = "24h: sent=" + sent + " master-notified=" + masterNotified + "\n";
        log.info(result.trim());
        return result;
    }

    private String resolveServiceName(Long serviceId) {
        if (serviceId == null) return "Usługa";
        return salonServiceRepository.findById(serviceId)
                .map(s -> s.getName())
                .orElse("Usługa");
    }

    private String formatDate(LocalDateTime dt) {
        String day = PL_DAYS[dt.getDayOfWeek().getValue() % 7];
        String month = PL_MONTHS[dt.getMonthValue() - 1];
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
