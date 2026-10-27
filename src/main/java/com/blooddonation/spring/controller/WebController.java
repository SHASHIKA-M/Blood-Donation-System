package com.blooddonation.spring.controller;

import com.blooddonation.spring.model.AvailabilitySlot;
import com.blooddonation.spring.model.Donor;
import com.blooddonation.spring.model.Request;
import com.blooddonation.spring.model.UserAccount;
import com.blooddonation.spring.service.AuthService;
import com.blooddonation.spring.service.BloodDonationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class WebController {

    @Autowired
    private BloodDonationService service;

    @Autowired
    private AuthService authService;

    // Home / Landing Page
    @GetMapping("/")
    public String home(Model model) {
        // Real-world: use COUNT queries, not full-table loads
        model.addAttribute("totalDonors", service.countDonors());
        model.addAttribute("totalOpenRequests", service.countRequestsByStatus("Pending"));
        model.addAttribute("totalFulfilledRequests", service.countRequestsByStatus("Delivered"));
        
        // Show up to 3 urgent open requests
        List<Request> urgentRequests = service.getRequests("Pending", null, null).stream()
            .limit(3)
            .toList();
        model.addAttribute("urgentRequests", urgentRequests);

        // Live stock per blood group for the public stock board
        model.addAttribute("stockByGroup", service.getStockByGroup());
        
        return "index";
    }

    // Register Donor GET & POST
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("donor", new Donor());
        return "register";
    }

    @PostMapping("/register")
    public String registerSubmit(
            @ModelAttribute Donor donor,
            @RequestParam(value = "slotDays", required = false) List<String> days,
            @RequestParam(value = "slotStarts", required = false) List<String> starts,
            @RequestParam(value = "slotEnds", required = false) List<String> ends,
            RedirectAttributes redirectAttributes) {
        try {
            // Build slots list
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

            Donor registered = service.registerDonor(donor);
            String loginUser = service.getDonorLoginUsername(registered.getId());
            if (loginUser != null) {
                redirectAttributes.addFlashAttribute("success", "Welcome aboard, " + registered.getName()
                        + "! You can now log in with username '" + loginUser + "' and your phone number as the password.");
            } else {
                redirectAttributes.addFlashAttribute("success", "Welcome aboard! You are registered as a donor.");
            }
            return "redirect:/donor/" + registered.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", toUserMessage(e.getMessage()));
            redirectAttributes.addFlashAttribute("donor", donor);
            return "redirect:/register";
        }
    }

    /** Owner of the profile or IT staff may mutate it — everyone else is read-only. */
    private boolean isOwnerOrStaff(HttpSession session, String donorId) {
        UserAccount u = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (u == null) return false;
        if (u.getRole() == com.blooddonation.spring.model.UserRole.IT_ADMIN) return true;
        return donorId != null && donorId.equals(u.getDonorId());
    }

    /** Never leak SQL/technical internals to the screen — log-worthy, not user-worthy. */
    private String toUserMessage(String raw) {
        if (raw == null || raw.isBlank()) return "Something went wrong. Please try again.";
        String low = raw.toLowerCase();
        if (low.contains("sql") || low.contains("constraint") || low.contains("jdbc")
                || low.contains("could not execute") || low.contains("null into column")
                || low.contains("exception") || low.length() > 220) {
            return "Something went wrong on our side. Please try again or contact support.";
        }
        return raw;
    }

    // Search Donors
    @GetMapping("/availability")
    public String availability(@RequestParam(value = "bloodGroup", required = false) String bloodGroup, Model model) {
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("units", service.getBloodUnits(bloodGroup).stream()
                .filter(u -> "Approved - Available".equals(u.getStatus()) && u.getExpiresAt() != null && u.getExpiresAt().isAfter(now))
                .toList());
        model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("stockByGroup", service.getStockByGroup());
        return "availability";
    }

    @GetMapping("/search")
    public String searchDonors(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "bloodGroup", required = false) String bloodGroup,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "province", required = false) String province,
            Model model) {
        
        List<Donor> donors = service.searchDonors(q, bloodGroup, city, province);
        model.addAttribute("donors", donors);
        model.addAttribute("q", q);
        model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("city", city);
        model.addAttribute("province", province);
        return "search";
    }

    // Single Donor Profile / Edit
    @GetMapping("/donor/{id}")
    public String donorProfile(@PathVariable("id") String id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<Donor> opt = service.getDonorById(id);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Donor profile not found.");
            return "redirect:/search";
        }
        model.addAttribute("donor", opt.get());
        // Owner dashboard widgets also work on the public URL when the owner views it
        UserAccount viewer = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (viewer != null && id.equals(viewer.getDonorId())) {
            model.addAttribute("eligibility", service.checkDonorEligibility(id));
            model.addAttribute("myAppointments", service.getDonorAppointments(id));
        }
        return "donor-profile";
    }

    // Update Donor Profile Details
    @PostMapping("/donor/{id}/edit")
    public String editDonor(
            @PathVariable("id") String id,
            @ModelAttribute Donor donor,
            @RequestParam(value = "slotDays", required = false) List<String> days,
            @RequestParam(value = "slotStarts", required = false) List<String> starts,
            @RequestParam(value = "slotEnds", required = false) List<String> ends,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isOwnerOrStaff(session, id)) {
            redirectAttributes.addFlashAttribute("error", "Please log in as the profile owner to edit.");
            return "redirect:/donor/" + id;
        }
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

            Donor updated = service.updateDonor(donor);
            // Keep donor login in sync (username = name, password = phone)
            try {
                authService.updateDonorCredentials(id, updated.getName(), updated.getPhone());
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", e.getMessage());
                return "redirect:/donor/" + id;
            }
            // Refresh own session if editing own profile
            UserAccount sessUser = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
            if (sessUser != null && id.equals(sessUser.getDonorId())) {
                authService.findByDonorId(id).ifPresent(a -> {
                    session.setAttribute(AuthService.SESSION_USER, a);
                    session.setAttribute(AuthService.SESSION_ROLE, a.getRole());
                });
            }
            redirectAttributes.addFlashAttribute("success", "Donor profile updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/donor/" + id;
    }

    // Add Donation Log entry
    @PostMapping("/donor/{id}/donations")
    public String addDonationLog(
            @PathVariable("id") String id,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date,
            @RequestParam("location") String location,
            @RequestParam("notes") String notes,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!isOwnerOrStaff(session, id)) {
            redirectAttributes.addFlashAttribute("error", "Please log in as the profile owner to log donations.");
            return "redirect:/donor/" + id;
        }
        try {
            service.addDonation(id, date, location, notes);
            redirectAttributes.addFlashAttribute("success", "Donation recorded successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/donor/" + id;
    }

    // Delete Donor
    @PostMapping("/donor/{id}/delete")
    public String deleteDonor(@PathVariable("id") String id, HttpSession session, RedirectAttributes redirectAttributes) {
        UserAccount actor = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (actor == null || actor.getRole() != com.blooddonation.spring.model.UserRole.IT_ADMIN) {
            redirectAttributes.addFlashAttribute("error", "You are not allowed to delete donor profiles.");
            return "redirect:/donor/" + id;
        }
        boolean success = service.deleteDonor(id);
        if (success) {
            redirectAttributes.addFlashAttribute("success", "Donor account deleted successfully.");
            return "redirect:/search";
        } else {
            redirectAttributes.addFlashAttribute("error", "Failed to delete donor profile.");
            return "redirect:/donor/" + id;
        }
    }

    // Request Board view & Create Request POST
    @GetMapping("/requests")
    public String requestBoard(
            @RequestParam(value = "status", defaultValue = "Pending") String status,
            @RequestParam(value = "bloodGroup", required = false) String bloodGroup,
            @RequestParam(value = "city", required = false) String city,
            Model model) {
        
        List<Request> items = service.getRequests(status, bloodGroup, city);
        model.addAttribute("requests", items);
        model.addAttribute("newRequest", new Request());
        model.addAttribute("status", status);
        model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("city", city);
        return "requests";
    }

    @PostMapping("/requests")
    public String createRequest(
            @ModelAttribute Request request,
            @RequestParam("neededByDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime neededBy,
            RedirectAttributes redirectAttributes) {
        try {
            request.setNeededBy(neededBy);
            service.createRequest(request);
            redirectAttributes.addFlashAttribute("success", "Blood request posted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/requests?status=Pending";
    }

    // Fulfill / Re-open Request
    @PostMapping("/requests/{id}/status")
    public String changeRequestStatus(
            @PathVariable("id") String id,
            @RequestParam("status") String status,
            RedirectAttributes redirectAttributes) {
        try {
            service.updateRequestStatus(id, status);
            redirectAttributes.addFlashAttribute("success", "Request status updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/requests";
    }

    // Contact Page
    @GetMapping("/contact")
    public String contactForm() {
        return "contact";
    }

    @PostMapping("/contact")
    public String contactSubmit(
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestParam("subject") String subject,
            @RequestParam("message") String message,
            RedirectAttributes redirectAttributes) {
        try {
            service.createMessage(name, email, subject, message);
            redirectAttributes.addFlashAttribute("success", "Thank you for contacting us, " + name + "! We will get back to you shortly.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/contact";
    }

    // About Page
    @GetMapping("/about")
    public String aboutPage() {
        return "about";
    }

    // Can You Donate Eligibility Page
    @GetMapping("/can-you-donate")
    public String canYouDonatePage() {
        return "canyoudonate";
    }

    // Hero Newsletter Subscribe Action
    @PostMapping("/subscribe")
    public String subscribeNewsletter(
            @RequestParam("email_address") String email,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("success", "Thank you for subscribing! We will notify " + email + " about urgent blood drives and alerts.");
        return "redirect:/";
    }
}
