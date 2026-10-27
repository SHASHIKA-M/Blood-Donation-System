package com.blooddonation.spring.controller;

import com.blooddonation.spring.model.*;
import com.blooddonation.spring.service.BloodDonationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private BloodDonationService service;

    @GetMapping("/login")
    public String loginForm() {
        return "redirect:/login";
    }

    @GetMapping("/logout")
    public String logout() {
        // Single invalidate lives in AuthController.logout — just delegate.
        return "redirect:/logout";
    }

    // Dashboard GET
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Donor> donors = service.searchDonors(null, null, null);
        List<Request> openReqs = service.getRequests("Pending", null, null);
        List<Request> fulfilledReqs = service.getRequests("Delivered", null, null);
        List<ContactMessage> messages = service.getAllMessages();

        model.addAttribute("totalDonors", donors.size());
        model.addAttribute("totalOpenRequests", openReqs.size());
        model.addAttribute("totalFulfilledRequests", fulfilledReqs.size());
        model.addAttribute("totalRequests", openReqs.size() + fulfilledReqs.size());
        model.addAttribute("totalMessages", messages.size());
        model.addAttribute("unreadMessages", messages.stream().filter(m -> "unread".equals(m.getStatus())).count());

        // Process stats for charts / visually appealing gauges
        Map<String, Long> bloodGroupStats = donors.stream()
                .collect(Collectors.groupingBy(Donor::getBloodGroup, Collectors.counting()));
        Map<String, Long> cityStats = donors.stream()
                .collect(Collectors.groupingBy(d -> d.getCity() != null ? d.getCity() : "Unknown", Collectors.counting()));

        // Also add some lists for quick preview
        model.addAttribute("bloodGroupStats", bloodGroupStats);
        model.addAttribute("cityStats", cityStats);
        
        // Show last 5 messages, requests, and donors
        model.addAttribute("recentDonors", donors.stream().limit(5).collect(Collectors.toList()));
        model.addAttribute("recentRequests", openReqs.stream().limit(5).collect(Collectors.toList()));
        model.addAttribute("recentMessages", messages.stream().limit(5).collect(Collectors.toList()));

        return "admin/dashboard";
    }

    // Donors Management GET
    @GetMapping("/inventory")
    public String inventory(@RequestParam(value = "bloodGroup", required = false) String bloodGroup, Model model) {
        model.addAttribute("units", service.getBloodUnits(bloodGroup)); model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("newUnit", new BloodUnit()); return "admin/inventory";
    }

    @PostMapping("/inventory")
    public String addBloodUnit(@ModelAttribute BloodUnit unit, RedirectAttributes redirects) {
        try { service.registerBloodUnit(unit); redirects.addFlashAttribute("success", "Blood unit added. It requires QA screening before release."); }
        catch (Exception e) { redirects.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/inventory";
    }

    @PostMapping("/inventory/{id}/screening")
    public String screening(@PathVariable String id, @RequestParam(required = false, defaultValue = "false") boolean hiv, @RequestParam(required = false, defaultValue = "false") boolean hepB, @RequestParam(required = false, defaultValue = "false") boolean hepC, @RequestParam(required = false, defaultValue = "false") boolean syphilis, @RequestParam(required = false, defaultValue = "false") boolean malaria, @RequestParam(required = false) String notes, RedirectAttributes redirects) {
        try { service.recordScreening(id, hiv, hepB, hepC, syphilis, malaria, notes); redirects.addFlashAttribute("success", "QA screening saved and availability updated."); }
        catch (Exception e) { redirects.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/inventory";
    }

    @PostMapping("/inventory/{id}/discard")
    public String discardUnitAdmin(@PathVariable String id, @RequestParam(required = false) String reason, RedirectAttributes redirects) {
        try {
            service.discardBloodUnit(id, reason);
            redirects.addFlashAttribute("success", "Blood unit decommissioned & marked as Discarded.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/inventory";
    }

    @GetMapping("/deliveries")
    public String deliveries(Model model) {
        model.addAttribute("deliveries", service.getDeliveryOrders());
        model.addAttribute("approvedRequests", service.getRequests("Approved", null, null));
        model.addAttribute("couriers", service.getAllCouriers());
        model.addAttribute("vehicles", service.getAllVehicles());
        return "admin/deliveries";
    }

    @PostMapping("/deliveries")
    public String scheduleDelivery(@RequestParam String requestId, @RequestParam String courierName, @RequestParam String vehicleNumber,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
                                   @RequestParam(required = false) String notes, RedirectAttributes redirects) {
        try { service.scheduleDelivery(requestId, courierName, vehicleNumber, scheduledAt, notes); redirects.addFlashAttribute("success", "Delivery scheduled and hospital request updated."); }
        catch (Exception e) { redirects.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/deliveries";
    }

    @PostMapping("/deliveries/{id}/status")
    public String updateDelivery(@PathVariable String id, @RequestParam String status, RedirectAttributes redirects) {
        try { service.updateDeliveryStatus(id, status); redirects.addFlashAttribute("success", "Delivery status updated."); }
        catch (Exception e) { redirects.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/deliveries";
    }

    @GetMapping("/donors")
    public String searchDonors(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "bloodGroup", required = false) String bloodGroup,
            @RequestParam(value = "city", required = false) String city,
            Model model) {
        
        List<Donor> donors = service.searchDonors(q, bloodGroup, city);
        model.addAttribute("donors", donors);
        model.addAttribute("q", q);
        model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("city", city);
        model.addAttribute("newDonor", new Donor());
        return "admin/donors";
    }

    // Create Donor POST
    @PostMapping("/donors/new")
    public String createDonorAdmin(
            @ModelAttribute Donor donor,
            @RequestParam(value = "slotDays", required = false) List<String> days,
            @RequestParam(value = "slotStarts", required = false) List<String> starts,
            @RequestParam(value = "slotEnds", required = false) List<String> ends,
            RedirectAttributes redirectAttributes) {

        try {
            List<AvailabilitySlot> slots = new ArrayList<>();
            if (days != null) {
                for (int i = 0; i < days.size(); i++) {
                    if (i < starts.size() && i < ends.size()) {
                        String day = days.get(i);
                        String sTime = starts.get(i);
                        String eTime = ends.get(i);
                        if (day != null && !day.isEmpty() && sTime != null && !sTime.isEmpty() && eTime != null && !eTime.isEmpty()) {
                            slots.add(new AvailabilitySlot(day, sTime, eTime));
                        }
                    }
                }
            }
            donor.setAvailabilitySlots(slots);
            Donor created = service.registerDonor(donor);
            String loginUser = service.getDonorLoginUsername(created.getId());
            redirectAttributes.addFlashAttribute("success", "Donor created successfully! ID: " + created.getId()
                    + (loginUser != null ? " | Login: " + loginUser + " / phone number" : ""));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating donor: " + e.getMessage());
        }
        return "redirect:/admin/donors";
    }

    // Edit Donor Details GET
    @GetMapping("/donors/{id}/edit")
    public String editDonorAdminForm(@PathVariable("id") String id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Donor> opt = service.getDonorById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Donor not found.");
            return "redirect:/admin/donors";
        }
        model.addAttribute("donor", opt.get());
        return "admin/donor-edit";
    }

    // Edit Donor Details POST
    @PostMapping("/donors/{id}/edit")
    public String editDonorAdmin(
            @PathVariable("id") String id,
            @ModelAttribute Donor donor,
            @RequestParam(value = "slotDays", required = false) List<String> days,
            @RequestParam(value = "slotStarts", required = false) List<String> starts,
            @RequestParam(value = "slotEnds", required = false) List<String> ends,
            RedirectAttributes redirectAttributes) {

        try {
            donor.setId(id);
            List<AvailabilitySlot> slots = new ArrayList<>();
            if (days != null) {
                for (int i = 0; i < days.size(); i++) {
                    String day = days.get(i);
                    String sTime = starts.get(i);
                    String eTime = ends.get(i);
                    if (day != null && !day.isEmpty() && sTime != null && !sTime.isEmpty() && eTime != null && !eTime.isEmpty()) {
                        slots.add(new AvailabilitySlot(day, sTime, eTime));
                    }
                }
            }
            donor.setAvailabilitySlots(slots);
            service.updateDonor(donor);
            redirectAttributes.addFlashAttribute("success", "Donor profile updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating donor: " + e.getMessage());
        }
        return "redirect:/admin/donors";
    }

    // Log Donor Donation History directly
    @PostMapping("/donors/{id}/donations")
    public String addDonationAdmin(
            @PathVariable("id") String id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date,
            @RequestParam("location") String location,
            @RequestParam("notes") String notes,
            RedirectAttributes redirectAttributes) {

        try {
            service.addDonation(id, date, location, notes);
            redirectAttributes.addFlashAttribute("success", "Donation recorded successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error recording donation: " + e.getMessage());
        }
        return "redirect:/admin/donors";
    }

    // Delete Donor POST
    @PostMapping("/donors/{id}/delete")
    public String deleteDonorAdmin(@PathVariable("id") String id, RedirectAttributes redirectAttributes) {
        boolean success = service.deleteDonor(id);
        if (success) {
            redirectAttributes.addFlashAttribute("success", "Donor deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete donor profile.");
        }
        return "redirect:/admin/donors";
    }

    // Requests Management GET
    @GetMapping("/requests")
    public String listRequestsAdmin(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "bloodGroup", required = false) String bloodGroup,
            @RequestParam(value = "city", required = false) String city,
            Model model) {

        List<Request> items = service.getRequests(status, bloodGroup, city);
        model.addAttribute("requests", items);
        model.addAttribute("newRequest", new Request());
        model.addAttribute("status", status);
        model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("city", city);
        return "admin/requests";
    }

    // Create Request POST
    @PostMapping("/requests/new")
    public String createRequestAdmin(
            @ModelAttribute Request request,
            @RequestParam("neededByDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime neededBy,
            RedirectAttributes redirectAttributes) {

        try {
            request.setNeededBy(neededBy);
            service.createRequest(request);
            redirectAttributes.addFlashAttribute("success", "Blood request posted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating request: " + e.getMessage());
        }
        return "redirect:/admin/requests";
    }

    // Modify Request Status
    @PostMapping("/requests/{id}/status")
    public String changeRequestStatusAdmin(
            @PathVariable("id") String id,
            @RequestParam("status") String status,
            RedirectAttributes redirectAttributes) {

        try {
            service.updateRequestStatus(id, status);
            redirectAttributes.addFlashAttribute("success", "Request status updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating status: " + e.getMessage());
        }
        return "redirect:/admin/requests";
    }

    // Delete Request POST
    @PostMapping("/requests/{id}/delete")
    public String deleteRequestAdmin(@PathVariable("id") String id, RedirectAttributes redirectAttributes) {
        boolean success = service.deleteRequest(id);
        if (success) {
            redirectAttributes.addFlashAttribute("success", "Blood request deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete blood request.");
        }
        return "redirect:/admin/requests";
    }

    // Messages Management GET
    @GetMapping("/messages")
    public String listMessagesAdmin(Model model) {
        List<ContactMessage> messages = service.getAllMessages();
        model.addAttribute("messages", messages);
        return "admin/messages";
    }

    // Mark Message as Read POST
    @PostMapping("/messages/{id}/read")
    public String markMessageReadAdmin(@PathVariable("id") String id, RedirectAttributes redirectAttributes) {
        try {
            service.markMessageAsRead(id);
            redirectAttributes.addFlashAttribute("success", "Message marked as read.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error marking message read: " + e.getMessage());
        }
        return "redirect:/admin/messages";
    }

    // Delete Message POST
    @PostMapping("/messages/{id}/delete")
    public String deleteMessageAdmin(@PathVariable("id") String id, RedirectAttributes redirectAttributes) {
        boolean success = service.deleteMessage(id);
        if (success) {
            redirectAttributes.addFlashAttribute("success", "Message deleted successfully.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete message.");
        }
        return "redirect:/admin/messages";
    }
}
