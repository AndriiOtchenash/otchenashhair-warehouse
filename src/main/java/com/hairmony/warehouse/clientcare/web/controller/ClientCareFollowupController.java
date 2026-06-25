package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.ClientCareService;
import com.hairmony.warehouse.clientcare.service.FollowUpService;
import com.hairmony.warehouse.clientcare.service.VisitService;
import com.hairmony.warehouse.clientcare.web.dto.ClientFollowupDto;
import com.hairmony.warehouse.clientcare.web.dto.LatestFollowUpDto;
import com.hairmony.warehouse.clientcare.web.dto.UnresolvedVisitRowDto;
import com.hairmony.warehouse.clientcare.web.dto.UpcomingSnoozeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clientcare/followups")
@RequiredArgsConstructor
public class ClientCareFollowupController {

    private final ClientCareService clientCareService;
    private final FollowUpService followUpService;
    private final VisitService visitService;

    @GetMapping
    public String followups(@RequestParam(required = false, defaultValue = "0") int minDays,
                            @RequestParam(required = false, defaultValue = "0") int maxDays,
                            @RequestParam(required = false, defaultValue = "all") String visitFilter,
                            @RequestParam(required = false, defaultValue = "false") boolean vip,
                            Model model) {
        List<ClientFollowupDto> queue = clientCareService.getFollowupQueue();

        var clientIds = queue.stream().map(ClientFollowupDto::clientId).collect(Collectors.toSet());
        Map<Long, LatestFollowUpDto> latestFollowUps = followUpService.getLatestPerClient(clientIds);

        Set<Long> vipIds = vip ? clientCareService.getVipClientIds() : Set.of();
        // For overdue filter: use the same source as the KPI card — any past PLANNED/CONFIRMED appointment,
        // even if the client also has an upcoming booking (the queue DTO only tracks the primary signal).
        Map<Long, LocalDateTime> overdueStartByClient = "overdue".equals(visitFilter)
                ? clientCareService.getOverdueStartTimeByClient() : Map.of();
        Set<Long> overdueClientIds = overdueStartByClient.keySet();

        // Active queue — exclude snoozed; apply minDays + maxDays + visitFilter
        List<ClientFollowupDto> active = queue.stream()
                .filter(c -> {
                    if (vip && !vipIds.contains(c.clientId())) return false;
                    LatestFollowUpDto fu = latestFollowUps.get(c.clientId());
                    if (fu != null && (fu.isActiveSnoozed() || fu.isRecentlyDone())) return false;
                    // Purchase filter: must have a purchase AND it must be old enough
                    if (minDays > 0 && (!c.hasPurchaseSignal() || c.daysSinceLastPurchase() < minDays)) return false;
                    // Purchase upper bound: must have purchase AND it must be recent enough
                    if (maxDays > 0 && (!c.hasPurchaseSignal() || c.daysSinceLastPurchase() > maxDays)) return false;
                    // Visit filter: independent of purchase filter
                    return switch (visitFilter) {
                        case "overdue"   -> overdueClientIds.contains(c.clientId());
                        case "noshow"    -> (c.visitOverdue() || c.visitTodayOverdue()) && c.visitIsNoShow();
                        case "scheduled" -> c.visitToday() || c.visitUpcoming();
                        case "none"      -> !c.hasVisitSignal();
                        default          -> true;
                    };
                })
                .collect(Collectors.toList());

        // Snoozed clients — shown separately at the bottom
        List<ClientFollowupDto> snoozed = queue.stream()
                .filter(c -> {
                    LatestFollowUpDto fu = latestFollowUps.get(c.clientId());
                    return fu != null && fu.isActiveSnoozed();
                })
                .collect(Collectors.toList());

        // Recently-done clients — hidden for 7 days, shown in collapsed section
        List<ClientFollowupDto> done = queue.stream()
                .filter(c -> {
                    LatestFollowUpDto fu = latestFollowUps.get(c.clientId());
                    return fu != null && fu.isRecentlyDone();
                })
                .collect(Collectors.toList());

        // Build returnTo preserving all active filters
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (minDays > 0) parts.add("minDays=" + minDays);
        if (maxDays > 0) parts.add("maxDays=" + maxDays);
        if (!"all".equals(visitFilter)) parts.add("visitFilter=" + visitFilter);
        if (vip) parts.add("vip=true");
        String returnTo = "/clientcare/followups" + (parts.isEmpty() ? "" : "?" + String.join("&", parts));

        Map<Long, Integer> activityCounts = followUpService.getActivityCountsPerClient(clientIds);

        List<UnresolvedVisitRowDto> unresolvedVisits = visitService.getClientsWithUnresolvedNextVisit();
        List<UnresolvedVisitRowDto> skippedVisits = visitService.getClientsWithSkippedNextVisit();

        LocalDate today = LocalDate.now();
        List<UpcomingSnoozeDto> upcomingSnoozes = snoozed.stream()
                .map(c -> {
                    LatestFollowUpDto fu = latestFollowUps.get(c.clientId());
                    long daysUntil = (fu != null && fu.dueDate() != null)
                            ? ChronoUnit.DAYS.between(today, fu.dueDate())
                            : 0;
                    return new UpcomingSnoozeDto(
                            c.clientId(),
                            c.clientName(),
                            fu != null ? fu.note() : null,
                            fu != null ? fu.dueDate() : null,
                            daysUntil
                    );
                })
                .sorted(Comparator.comparingLong(UpcomingSnoozeDto::daysUntil))
                .collect(Collectors.toList());

        model.addAttribute("clients", active);
        model.addAttribute("snoozed", snoozed);
        model.addAttribute("done", done);
        model.addAttribute("upcomingSnoozes", upcomingSnoozes);
        model.addAttribute("latestFollowUps", latestFollowUps);
        model.addAttribute("activityCounts", activityCounts);
        model.addAttribute("overdueStartByClient", overdueStartByClient);
        model.addAttribute("activeMinDays", minDays);
        model.addAttribute("activeMaxDays", maxDays);
        model.addAttribute("activeVisitFilter", visitFilter);
        model.addAttribute("activeVip", vip);
        model.addAttribute("returnTo", returnTo);
        model.addAttribute("unresolvedVisits", unresolvedVisits);
        model.addAttribute("skippedVisits", skippedVisits);
        return "clientcare/followups";
    }
}
