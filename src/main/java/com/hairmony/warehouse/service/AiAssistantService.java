package com.hairmony.warehouse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hairmony.warehouse.domain.stock.StockMovement;
import com.hairmony.warehouse.web.dto.StockDashboardRowDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final StockService stockService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    public String askAssistant(String userQuestion, Locale locale) {
        log.info("Gemini API key present: {}", geminiApiKey != null && !geminiApiKey.isBlank());
        try {
            String context = buildContext();
            String language = resolveLanguage(locale);
            String fullPrompt = buildPrompt(context, userQuestion, language);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", fullPrompt))
                    ))
            );

            String responseJson = restClient.post()
                    .uri(GEMINI_URL + "?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseJson);
            return root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText("Відповідь не отримана.");

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return "Вибачте, виникла помилка при зверненні до AI. Спробуйте пізніше.";
        }
    }

    private String buildContext() {
        List<StockDashboardRowDto> stock = stockService.getDashboard();

        List<StockMovement> recentMovements = stockService.findAllMovements().stream()
                .filter(m -> m.getCreatedAt().isAfter(LocalDateTime.now(ZoneId.of("Europe/Warsaw")).minusDays(30)))
                .limit(20)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("=== ПОТОЧНІ ЗАЛИШКИ ===\n");
        for (StockDashboardRowDto row : stock) {
            sb.append(String.format("- %s (%s): %s %s, мін: %s, статус: %s%n",
                    row.getProductName(),
                    row.getCategoryName(),
                    row.getCurrentQuantity().stripTrailingZeros().toPlainString(),
                    row.getUnit(),
                    row.getMinStockLevel().stripTrailingZeros().toPlainString(),
                    translateStatus(row.getStatus())
            ));
        }

        sb.append("\n=== ОСТАННІ РУХИ ТОВАРІВ (30 ДНІВ, ДО 20 ЗАПИСІВ) ===\n");
        for (StockMovement m : recentMovements) {
            String counterparty = "";
            if (m.getSupplier() != null) counterparty = ", від: " + m.getSupplier().getName();
            else if (m.getClient() != null) counterparty = ", клієнт: " + m.getClient().getName();
            sb.append(String.format("- %s | %s | %s | %s %s%s%n",
                    m.getCreatedAt().toLocalDate(),
                    translateMovementType(m.getMovementType().name()),
                    m.getProduct().getName(),
                    m.getQuantity().stripTrailingZeros().toPlainString(),
                    m.getProduct().getUnit().name(),
                    counterparty
            ));
        }

        return sb.toString();
    }

    private String resolveLanguage(Locale locale) {
        return switch (locale.getLanguage()) {
            case "uk" -> "Ukrainian";
            case "pl" -> "Polish";
            default  -> "English";
        };
    }

    private String buildPrompt(String context, String question, String language) {
        return "You are a smart warehouse assistant for OtchenashHair, a trichology salon.\n"
                + "Your task is to help the warehouse manager make decisions about purchasing,\n"
                + "stock control, and inventory management.\n\n"
                + "Always respond in " + language + ". Be specific and helpful.\n"
                + "Use the provided warehouse data to answer the question.\n"
                + "If data is insufficient, say so.\n\n"
                + context
                + "\n=== MANAGER'S QUESTION ===\n"
                + question;
    }

    private String translateStatus(StockDashboardRowDto.StockStatus status) {
        return switch (status) {
            case OK -> "норма";
            case LOW -> "мало";
            case OUT -> "немає";
        };
    }

    private String translateMovementType(String type) {
        return switch (type) {
            case "PURCHASE" -> "Прихід";
            case "SALE" -> "Продаж";
            case "WRITE_OFF" -> "Списання";
            case "ADJUSTMENT" -> "Коригування";
            default -> type;
        };
    }
}
