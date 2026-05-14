package com.hairmony.warehouse.clientcare.web.controller;

import com.hairmony.warehouse.clientcare.service.ClientCareService;
import com.hairmony.warehouse.clientcare.service.FollowUpService;
import com.hairmony.warehouse.clientcare.web.dto.ClientFollowupDto;
import com.hairmony.warehouse.clientcare.web.dto.LatestFollowUpDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/clientcare/followups")
@RequiredArgsConstructor
public class ClientCareFollowupController {

    private final ClientCareService clientCareService;
    private final FollowUpService followUpService;

    @GetMapping
    public String followups(@RequestParam(required = false, defaultValue = "0") int minDays,
                            @RequestParam(required = false, defaultValue = "all") String visitFilter,
                            Model model) {
        List<ClientFollowupDto> queue = clientCareService.getFollowupQueue();

        var clientIds = queue.stream().map(ClientFollowupDto::clientId).collect(Collectors.toSet());
        Map<Long, LatestFollowUpDto> latestFollowUps = followUpService.getLatestPerClient(clientIds);

        // Active queue — exclude snoozed; apply minDays + visitFilter
        List<ClientFollowupDto> active = queue.stream()
                .filter(c -> {
                    LatestFollowUpDto fu = latestFollowUps.get(c.clientId());
                    if (fu != null && (fu.isActiveSnoozed() || fu.isRecentlyDone())) return false;
                    // Purchase filter: must have a purchase AND it must be old enough
                    if (minDays > 0 && (!c.hasPurchaseSignal() || c.daysSinceLastPurchase() < minDays)) return false;
                    // Visit filter: independent of purchase filter
                    return switch (visitFilter) {
                        case "overdue"   -> c.visitOverdue();
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

        // Build returnTo preserving both active filters
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (minDays > 0) parts.add("minDays=" + minDays);
        if (!"all".equals(visitFilter)) parts.add("visitFilter=" + visitFilter);
        String returnTo = "/clientcare/followups" + (parts.isEmpty() ? "" : "?" + String.join("&", parts));

        Map<Long, Integer> activityCounts = followUpService.getActivityCountsPerClient(clientIds);

        model.addAttribute("clients", active);
        model.addAttribute("snoozed", snoozed);
        model.addAttribute("done", done);
        model.addAttribute("latestFollowUps", latestFollowUps);
        model.addAttribute("activityCounts", activityCounts);
        model.addAttribute("activeMinDays", minDays);
        model.addAttribute("activeVisitFilter", visitFilter);
        model.addAttribute("returnTo", returnTo);
        return "clientcare/followups";
    }
}
