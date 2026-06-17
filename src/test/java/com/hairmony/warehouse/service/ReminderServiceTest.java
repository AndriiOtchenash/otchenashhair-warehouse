package com.hairmony.warehouse.service;

import com.hairmony.warehouse.domain.appointment.Appointment;
import com.hairmony.warehouse.domain.appointment.AppointmentStatus;
import com.hairmony.warehouse.domain.client.Client;
import com.hairmony.warehouse.repository.AppointmentRepository;
import com.hairmony.warehouse.repository.SalonServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock SalonServiceRepository salonServiceRepository;
    @Mock TelegramService telegramService;

    @InjectMocks ReminderService reminderService;

    private static final long CHAT_ID = 111222333L;

    // ── helpers ──────────────────────────────────────────────────────────────

    private Appointment apptWithTelegram(LocalDateTime startAt) {
        Client client = Client.builder()
                .id(1L).name("Jan Kowalski").phone("+48123456789")
                .telegramChatId(CHAT_ID)
                .build();
        return Appointment.builder()
                .id(10L).client(client).startAt(startAt)
                .status(AppointmentStatus.PLANNED)
                .build();
    }

    private Appointment apptWithoutTelegram(LocalDateTime startAt) {
        Client client = Client.builder()
                .id(2L).name("Anna Nowak").phone("+48987654321")
                .build(); // telegramChatId = null
        return Appointment.builder()
                .id(11L).client(client).startAt(startAt)
                .status(AppointmentStatus.PLANNED)
                .build();
    }

    private void stubRepo(Appointment... appointments) {
        when(appointmentRepository.findAppointmentsForDayReminder(any(), any(), any()))
                .thenReturn(List.of(appointments));
        // serviceId is null in all test appointments → resolveServiceName() returns "Usługa"
        // without hitting salonServiceRepository, so no stub needed
    }

    // ── 1. Polish reminder text ───────────────────────────────────────────────

    @Test
    void clientWithTelegram_reminderTextIsPolish() {
        stubRepo(apptWithTelegram(LocalDateTime.of(2025, 5, 15, 11, 0)));

        reminderService.sendReminders();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessageWithButtons(eq(CHAT_ID), text.capture(), any());

        assertThat(text.getValue())
                .contains("Przypomnienie")
                .contains("Prosimy o potwierdzenie wizyty")
                .doesNotContain("Нагадуємо")
                .doesNotContain("Будь ласка");
    }

    // ── 2. Polish button labels ───────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void clientWithTelegram_inlineButtonsArePolish() {
        stubRepo(apptWithTelegram(LocalDateTime.of(2025, 5, 15, 11, 0)));

        reminderService.sendReminders();

        ArgumentCaptor<List<List<Map<String, String>>>> keyboard =
                ArgumentCaptor.forClass(List.class);
        verify(telegramService).sendMessageWithButtons(anyLong(), anyString(), keyboard.capture());

        List<Map<String, String>> row = keyboard.getValue().get(0);
        assertThat(row).extracting(m -> m.get("text"))
                .containsExactlyInAnyOrder("✅ Potwierdzam", "❌ Anuluj");
    }

    // ── 3. Master notification when client has no Telegram ───────────────────

    @Test
    void clientWithoutTelegram_masterNotifiedInPolish() {
        stubRepo(apptWithoutTelegram(LocalDateTime.of(2025, 8, 20, 9, 30)));

        reminderService.sendReminders();

        verify(telegramService, never()).sendMessageWithButtons(anyLong(), anyString(), any());

        ArgumentCaptor<String> master = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMasterMessage(master.capture());

        assertThat(master.getValue())
                .contains("Klient bez Telegram")
                .contains("Przypomnij ręcznie")
                .doesNotContain("Клієнт без Telegram")
                .doesNotContain("Нагадайте вручну");
    }

    // ── 4. Polish month names (all 12) ───────────────────────────────────────

    @ParameterizedTest(name = "month {0} → {1}")
    @CsvSource({
        "1,  stycznia",
        "2,  lutego",
        "3,  marca",
        "4,  kwietnia",
        "5,  maja",
        "6,  czerwca",
        "7,  lipca",
        "8,  sierpnia",
        "9,  września",
        "10, października",
        "11, listopada",
        "12, grudnia"
    })
    void dateFormat_containsPolishMonthName(int month, String expectedMonth) {
        LocalDateTime dt = LocalDateTime.of(2025, month, 15, 10, 0);
        stubRepo(apptWithTelegram(dt));

        reminderService.sendReminders();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessageWithButtons(anyLong(), text.capture(), any());
        assertThat(text.getValue()).contains(expectedMonth);
    }

    // ── 5. Polish day-of-week names (all 7) ──────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
        "2025-01-06, poniedziałek",
        "2025-01-07, wtorek",
        "2025-01-08, środa",
        "2025-01-09, czwartek",
        "2025-01-10, piątek",
        "2025-01-11, sobota",
        "2025-01-12, niedziela"
    })
    void dateFormat_containsPolishDayName(String date, String expectedDay) {
        LocalDateTime dt = LocalDateTime.parse(date + "T10:00:00");
        stubRepo(apptWithTelegram(dt));

        reminderService.sendReminders();

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(telegramService).sendMessageWithButtons(anyLong(), text.capture(), any());
        assertThat(text.getValue()).contains(expectedDay);
    }
}
