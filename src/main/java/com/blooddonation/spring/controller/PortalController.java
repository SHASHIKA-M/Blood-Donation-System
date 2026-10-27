package com.blooddonation.spring.controller;

import com.blooddonation.spring.model.*;
import com.blooddonation.spring.service.AuthService;
import com.blooddonation.spring.service.BloodDonationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/portal")
public class PortalController {

    @Autowired private BloodDonationService service;
    @Autowired private AuthService authService;

    private UserAccount requireRole(HttpSession session, UserRole... roles) {
        UserAccount user = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (user == null) throw new RuntimeException("Not authenticated");
        if (user.getRole() == UserRole.IT_ADMIN) return user;
        for (UserRole r : roles) if (user.getRole() == r) return user;
        throw new RuntimeException("Access denied");
    }

    /** Self-registered donors may ONLY use their own profile page — nothing else. */
    private UserAccount requireDonor(HttpSession session) {
        UserAccount user = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (user == null) throw new RuntimeException("Not authenticated");
        if (user.getDonorId() == null || user.getDonorId().isBlank()) throw new RuntimeException("Access denied");
        return user;
    }

    private boolean isDonor(UserAccount user) {
        return user != null && user.getDonorId() != null && !user.getDonorId().isBlank();
    }

    // Donor self-service: own profile only — reuses the public donor-profile view
    @GetMapping("/donor")
    public String donorPortal(Model model, HttpSession session, RedirectAttributes redirects) {
        UserAccount user = requireDonor(session);
        var donorOpt = service.getDonorById(user.getDonorId());
        if (donorOpt.isEmpty()) {
            redirects.addFlashAttribute("error", "Your donor profile was not found. Please contact support.");
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("donor", donorOpt.get());
        model.addAttribute("eligibility", service.checkDonorEligibility(user.getDonorId()));
        model.addAttribute("myAppointments", service.getDonorAppointments(user.getDonorId()));
        return "donor-profile";
    }

    // Donor books a donation appointment
    @PostMapping("/donor/appointments")
    public String bookAppointment(@RequestParam String branch,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
                                  @RequestParam(required = false) String notes,
                                  HttpSession session, RedirectAttributes redirects) {
        UserAccount user = requireDonor(session);
        try {
            service.bookAppointment(user.getDonorId(), branch, scheduledAt, notes);
            authService.log(user.getUsername(), "BOOK_APPOINTMENT", "Appointment @ " + branch + " " + scheduledAt);
            redirects.addFlashAttribute("success", "Appointment booked! Please arrive 10 minutes early.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/donor";
    }

    // Donor cancels own appointment
    @PostMapping("/donor/appointments/{id}/cancel")
    public String cancelAppointment(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        UserAccount user = requireDonor(session);
        try {
            service.cancelAppointment(id, user.getDonorId());
            redirects.addFlashAttribute("success", "Appointment cancelled.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/donor";
    }

    // Recipient Portal (staff recipients only; donors use /portal/donor)
    @GetMapping("/recipient")
    public String recipientPortal(@RequestParam(required = false) String bloodGroup, Model model, HttpSession session) {
        UserAccount user = requireRole(session, UserRole.RECIPIENT);
        if (isDonor(user)) return "redirect:/portal/donor";
        model.addAttribute("user", user);
        model.addAttribute("units", service.getBloodUnits(bloodGroup).stream()
                .filter(u -> "Approved - Available".equals(u.getStatus()) && u.getExpiresAt() != null).toList());
        model.addAttribute("bloodGroup", bloodGroup);
        model.addAttribute("myRequests", service.getRequests(null, null, null));
        model.addAttribute("newRequest", new Request());
        return "portal/recipient";
    }

    @PostMapping("/recipient/request")
    public String submitBloodRequest(@ModelAttribute Request request,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime neededByDate,
                                     HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount u = requireRole(session, UserRole.RECIPIENT);
            if (isDonor(u)) {
                redirects.addFlashAttribute("error", "Donor accounts can only manage their own profile.");
                return "redirect:/portal/donor";
            }
            request.setNeededBy(neededByDate);
            request.setUrgency("Urgent");
            Request saved = service.createRequest(request);
            authService.log(((UserAccount) session.getAttribute(AuthService.SESSION_USER)).getUsername(),
                    "SUBMIT_REQUEST", "Blood request " + saved.getId() + " submitted");
            redirects.addFlashAttribute("success", "Request submitted! Request ID: " + saved.getId() + ". Status: " + saved.getStatus());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/recipient";
    }

    @PostMapping("/recipient/request/{id}/edit")
    public String editRecipientRequest(@PathVariable String id,
                                       @RequestParam Integer units,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime neededByDate,
                                       @RequestParam String contactPhone,
                                       @RequestParam(required = false) String notes,
                                       HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount u2 = requireRole(session, UserRole.RECIPIENT);
            if (isDonor(u2)) {
                redirects.addFlashAttribute("error", "Donor accounts can only manage their own profile.");
                return "redirect:/portal/donor";
            }
            var existing = service.getRequestById(id);
            if (existing.isPresent() && ("Delivered".equalsIgnoreCase(existing.get().getStatus())
                    || "Dispatch Scheduled".equalsIgnoreCase(existing.get().getStatus()))) {
                redirects.addFlashAttribute("error", "This request is already " + existing.get().getStatus() + " and can no longer be edited.");
                return "redirect:/portal/recipient";
            }
            Request updated = service.updateRequestDetails(id, units, neededByDate, contactPhone, notes, null);
            authService.log(((UserAccount) session.getAttribute(AuthService.SESSION_USER)).getUsername(),
                    "UPDATE_REQUEST", "Updated blood request " + updated.getId());
            redirects.addFlashAttribute("success", "Request updated successfully! (ID: " + updated.getId() + ")");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/recipient";
    }

    @PostMapping("/recipient/request/{id}/delete")
    public String deleteRecipientRequest(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount u3 = requireRole(session, UserRole.RECIPIENT);
            if (isDonor(u3)) {
                redirects.addFlashAttribute("error", "Donor accounts can only manage their own profile.");
                return "redirect:/portal/donor";
            }
            var target = service.getRequestById(id);
            if (target.isPresent() && ("Delivered".equalsIgnoreCase(target.get().getStatus())
                    || "Dispatch Scheduled".equalsIgnoreCase(target.get().getStatus()))) {
                redirects.addFlashAttribute("error", "This request is already " + target.get().getStatus() + " and can no longer be cancelled.");
                return "redirect:/portal/recipient";
            }
            boolean ok = service.deleteRequest(id);
            if (ok) {
                authService.log(((UserAccount) session.getAttribute(AuthService.SESSION_USER)).getUsername(),
                        "DELETE_REQUEST", "Cancelled/Deleted blood request " + id);
                redirects.addFlashAttribute("success", "Blood request cancelled and removed successfully.");
            } else {
                redirects.addFlashAttribute("error", "Request not found or could not be removed.");
            }
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/recipient";
    }

    // Hospital Coordinator Portal
    @GetMapping("/hospital")
    public String hospitalPortal(Model model, HttpSession session) {
        UserAccount user = requireRole(session, UserRole.HOSPITAL_COORDINATOR);
        model.addAttribute("user", user);
        model.addAttribute("requisitions", service.getRequests(null, null, null).stream()
                .filter(r -> "Hospital Requisition".equals(r.getRequestType())).toList());
        model.addAttribute("newRequisition", new Request());
        return "portal/hospital";
    }

    @PostMapping("/hospital/requisition")
    public String submitRequisition(@ModelAttribute Request req,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime neededByDate,
                                    HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.HOSPITAL_COORDINATOR);
            req.setNeededBy(neededByDate);
            req.setHospital(user.getHospital());
            long available = service.countAvailableUnits(req.getBloodGroup(), req.getComponent(), null);
            Request saved = service.createHospitalRequisition(req);
            authService.log(user.getUsername(), "SUBMIT_REQUISITION",
                    "Requisition " + saved.getId() + " — availability: " + available + " units");
            redirects.addFlashAttribute("success",
                    "Requisition ID: " + saved.getId() + " | Availability check: " + available + " units found | Status: " + saved.getStatus());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/hospital";
    }

    @PostMapping("/hospital/requisition/{id}/edit")
    public String editHospitalRequisition(@PathVariable String id,
                                          @RequestParam Integer units,
                                          @RequestParam String urgency,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime neededByDate,
                                          @RequestParam String contactPhone,
                                          @RequestParam(required = false) String notes,
                                          HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.HOSPITAL_COORDINATOR);
            Request updated = service.updateRequestDetails(id, units, neededByDate, contactPhone, notes, urgency);
            authService.log(user.getUsername(), "UPDATE_REQUISITION", "Updated requisition " + updated.getId());
            redirects.addFlashAttribute("success", "Requisition updated successfully! (ID: " + updated.getId() + ")");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/hospital";
    }

    @PostMapping("/hospital/requisition/{id}/delete")
    public String deleteHospitalRequisition(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.HOSPITAL_COORDINATOR);
            boolean ok = service.deleteRequest(id);
            if (ok) {
                authService.log(user.getUsername(), "DELETE_REQUISITION", "Cancelled/Deleted requisition " + id);
                redirects.addFlashAttribute("success", "Hospital requisition cancelled and deleted successfully.");
            } else {
                redirects.addFlashAttribute("error", "Requisition not found or could not be cancelled.");
            }
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/hospital";
    }

    @GetMapping("/hospital/check-availability")
    @ResponseBody
    public String checkAvailability(@RequestParam String bloodGroup, @RequestParam String component, HttpSession session) {
        requireRole(session, UserRole.HOSPITAL_COORDINATOR);
        String group = bloodGroup == null ? "" : bloodGroup.trim();
        String comp = component == null ? "" : component.trim();
        long count = service.countAvailableUnits(group, comp, null);
        return count + " units available for " + group + " (" + comp + ")";
    }

    // QA Officer Portal
    @GetMapping("/qa")
    public String qaPortal(@RequestParam(required = false) String unitId, Model model, HttpSession session) {
        UserAccount user = requireRole(session, UserRole.QA_OFFICER);
        model.addAttribute("user", user);
        model.addAttribute("units", service.getBloodUnits(null));
        model.addAttribute("pendingUnits", service.getBloodUnits(null).stream()
                .filter(u -> "Pending Screening".equals(u.getStatus())).toList());
        if (unitId != null) {
            service.getBloodUnits(null).stream().filter(u -> u.getId().equals(unitId)).findFirst()
                    .ifPresent(u -> model.addAttribute("selectedUnit", u));
        }
        return "portal/qa";
    }

    @PostMapping("/qa/screening")
    public String logScreening(@RequestParam String unitId,
                               @RequestParam(required = false, defaultValue = "false") boolean hiv,
                               @RequestParam(required = false, defaultValue = "false") boolean hepB,
                               @RequestParam(required = false, defaultValue = "false") boolean hepC,
                               @RequestParam(required = false, defaultValue = "false") boolean syphilis,
                               @RequestParam(required = false, defaultValue = "false") boolean malaria,
                               @RequestParam(required = false) String notes, @RequestParam(required = false) Boolean crossMatch,
                               HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.QA_OFFICER);
            BloodUnit unit = service.recordScreening(unitId, hiv, hepB, hepC, syphilis, malaria, notes);
            authService.log(user.getUsername(), "LOG_SCREENING", "Unit " + unitId + " → " + unit.getStatus());
            redirects.addFlashAttribute("success", "Screening recorded. Unit status: " + unit.getStatus());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/qa?unitId=" + unitId;
    }

    @PostMapping("/qa/unit/{id}/discard")
    public String discardBloodUnit(@PathVariable String id, @RequestParam(required = false) String reason,
                                   HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.QA_OFFICER);
            BloodUnit unit = service.discardBloodUnit(id, reason);
            authService.log(user.getUsername(), "LOG_DISCARD", "Unit " + id + " decommissioned. Reason: " + reason);
            redirects.addFlashAttribute("success", "Unit " + id + " has been decommissioned & marked as Discarded.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/qa";
    }

    @PostMapping("/qa/unit/{id}/notes")
    public String updateQaNotes(@PathVariable String id, @RequestParam String notes,
                                HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.QA_OFFICER);
            service.updateBloodUnitNotes(id, notes);
            authService.log(user.getUsername(), "UPDATE_QA_NOTES", "Unit " + id + " notes updated");
            redirects.addFlashAttribute("success", "Quality assurance notes updated.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/qa?unitId=" + id;
    }

    // Delivery Coordination Portal
    @GetMapping("/delivery")
    public String deliveryPortal(Model model, HttpSession session) {
        UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
        model.addAttribute("user", user);
        model.addAttribute("deliveries", service.getDeliveryOrders());
        model.addAttribute("approvedRequests", service.getRequests("Approved", null, null));
        model.addAttribute("vehicles", service.getAllVehicles());
        model.addAttribute("couriers", service.getAllCouriers());
        model.addAttribute("availableCouriers", service.getAvailableCouriers());
        model.addAttribute("availableVehicles", service.getAvailableVehicles());
        model.addAttribute("newVehicle", new DeliveryVehicle());
        model.addAttribute("newCourier", new DeliveryCourier());
        return "portal/delivery";
    }

    @PostMapping("/delivery/vehicles")
    public String registerVehicle(@ModelAttribute DeliveryVehicle vehicle, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            service.registerVehicle(vehicle);
            authService.log(user.getUsername(), "REGISTER_VEHICLE", "Vehicle " + vehicle.getVehicleNumber());
            redirects.addFlashAttribute("success", "Vehicle registered: " + vehicle.getVehicleNumber());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/vehicles/{id}/status")
    public String updateVehicleStatus(@PathVariable String id, @RequestParam String status,
                                      HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            service.updateVehicleStatus(id, status);
            authService.log(user.getUsername(), "UPDATE_VEHICLE", "Vehicle " + id + " status → " + status);
            redirects.addFlashAttribute("success", "Vehicle status updated to: " + status);
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/vehicles/{id}/delete")
    public String deleteVehicle(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            boolean ok = service.deleteVehicle(id);
            if (ok) {
                authService.log(user.getUsername(), "DELETE_VEHICLE", "Deleted vehicle " + id);
                redirects.addFlashAttribute("success", "Vehicle removed from fleet.");
            } else {
                redirects.addFlashAttribute("error", "Vehicle not found.");
            }
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/couriers")
    public String registerCourier(@ModelAttribute DeliveryCourier courier, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            service.registerCourier(courier);
            authService.log(user.getUsername(), "REGISTER_COURIER", "Courier " + courier.getName());
            redirects.addFlashAttribute("success", "Courier registered: " + courier.getName());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/couriers/{id}/status")
    public String updateCourierStatus(@PathVariable String id, @RequestParam String status,
                                      HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            service.updateCourierStatus(id, status);
            authService.log(user.getUsername(), "UPDATE_COURIER", "Courier " + id + " status → " + status);
            redirects.addFlashAttribute("success", "Courier status updated to: " + status);
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/couriers/{id}/delete")
    public String deleteCourier(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            boolean ok = service.deleteCourier(id);
            if (ok) {
                authService.log(user.getUsername(), "DELETE_COURIER", "Deleted courier " + id);
                redirects.addFlashAttribute("success", "Courier removed from active directory.");
            } else {
                redirects.addFlashAttribute("error", "Courier not found.");
            }
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/schedule")
    public String scheduleDelivery(@RequestParam String requestId, @RequestParam String courierName,
                                   @RequestParam String vehicleNumber,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt,
                                   @RequestParam(required = false) String notes,
                                   HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            DeliveryOrder order = service.scheduleDelivery(requestId, courierName, vehicleNumber, scheduledAt, notes);
            authService.log(user.getUsername(), "SCHEDULE_DELIVERY", "Delivery " + order.getId() + " scheduled");
            redirects.addFlashAttribute("success", "Delivery scheduled. Requisition status: Dispatch Scheduled");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/{id}/status")
    public String updateDeliveryStatus(@PathVariable String id, @RequestParam String status,
                                       HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            service.updateDeliveryStatus(id, status);
            authService.log(user.getUsername(), "UPDATE_DELIVERY", "Delivery " + id + " → " + status);
            redirects.addFlashAttribute("success", "Delivery status updated to: " + status);
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    @PostMapping("/delivery/{id}/cancel")
    public String cancelDelivery(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.DELIVERY_OFFICER);
            service.cancelDeliveryOrder(id);
            authService.log(user.getUsername(), "CANCEL_DELIVERY", "Cancelled delivery order " + id);
            redirects.addFlashAttribute("success", "Delivery order cancelled. Request returned to approved pool.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/delivery";
    }

    // Blood Bank Manager Portal
    @GetMapping("/manager")
    public String managerPortal(@RequestParam(required = false) String branch, Model model, HttpSession session) {
        UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
        List<String> activeBranches = authService.getActiveBranches();
        model.addAttribute("allBranches", authService.getAllBranches());
        model.addAttribute("activeBranches", activeBranches);

        if (branch != null && !branch.isBlank() && !authService.isBranchActive(branch)) {
            branch = null;
        }
        if (branch == null || branch.isBlank()) {
            branch = activeBranches.isEmpty() ? "Colombo Central" : activeBranches.get(0);
        }
        model.addAttribute("user", user);
        model.addAttribute("stockSummary", service.getStockDashboard(branch));
        model.addAttribute("lowStockAlerts", service.getLowStockAlerts());
        model.addAttribute("pendingRequests", service.getRequests("Pending", null, null));
        model.addAttribute("submittedRequisitions", service.getRequests("Submitted", null, null));
        model.addAttribute("branchFilter", branch);
        model.addAttribute("upcomingAppointments", service.getUpcomingAppointments());
        // Collection dropdown: only eligible + Available donors (collected ones
        // drop out automatically until the 120-day gap passes)
        var eligibleDonors = service.searchDonors(null, null, null).stream()
                .filter(d -> "Available".equalsIgnoreCase(d.getAvailability()))
                .filter(d -> "ELIGIBLE".equals(service.checkDonorEligibility(d.getId())))
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("donors", eligibleDonors);
        return "portal/manager";
    }

    @PostMapping("/manager/stock/add")
    public String addStockBatch(@RequestParam String bloodGroup, @RequestParam String component,
                                @RequestParam String branch, @RequestParam int units,
                                @RequestParam(defaultValue = "35") int expiryDays,
                                HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            service.registerStockBatch(bloodGroup, component, branch, units, expiryDays);
            authService.log(user.getUsername(), "ADD_STOCK_BATCH", units + " units of " + bloodGroup + " added to " + branch);
            redirects.addFlashAttribute("success", units + " units of " + bloodGroup + " (" + component + ") registered for " + branch + ".");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    @PostMapping("/manager/stock/adjust")
    public String adjustStock(@RequestParam String bloodGroup, @RequestParam String component,
                              @RequestParam String branch, @RequestParam int delta,
                              @RequestParam(required = false) String reason,
                              HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            long newCount = service.adjustStockQuantity(bloodGroup, component, branch, delta, reason);
            authService.log(user.getUsername(), "ADJUST_STOCK",
                    (delta > 0 ? "+" + delta : "" + delta) + " " + bloodGroup + " " + component + " @ " + branch
                    + (reason != null && !reason.isBlank() ? " (" + reason.trim() + ")" : "") + " → now " + newCount);
            redirects.addFlashAttribute("success", "Stock updated: " + bloodGroup + " " + component + " @ " + branch
                    + " (" + (delta > 0 ? "+" + delta : delta) + ") → " + newCount + " available.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    @PostMapping("/manager/collect")
    public String collectFromDonor(@RequestParam String donorPhone, @RequestParam String component,
                                   @RequestParam String branch, @RequestParam(required = false) String location,
                                   HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            var donorOpt = service.getDonorByPhone(donorPhone);
            if (donorOpt.isEmpty()) throw new Exception("No donor found with phone " + donorPhone + ". Register them first.");
            BloodUnit unit = service.collectDonation(donorOpt.get().getId(), component, branch, location);
            authService.log(user.getUsername(), "COLLECT_DONATION",
                    "Collected " + unit.getBloodGroup() + " " + component + " from " + donorOpt.get().getName() + " → unit " + unit.getId());
            redirects.addFlashAttribute("success", "Collection recorded: " + unit.getBloodGroup() + " " + component
                    + " from " + donorOpt.get().getName() + " → Pending Screening. Donor hidden from list until eligible again.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    @PostMapping("/manager/appointments/{id}/status")
    public String updateAppointmentStatus(@PathVariable String id, @RequestParam String status,
                                          HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            if ("Completed".equalsIgnoreCase(status)) service.completeAppointment(id);
            else if ("Cancelled".equalsIgnoreCase(status)) service.cancelAppointment(id, null);
            else throw new Exception("Invalid appointment status.");
            authService.log(user.getUsername(), "APPOINTMENT_" + status.toUpperCase(), "Appointment " + id + " → " + status);
            redirects.addFlashAttribute("success", "Appointment marked as " + status + ".");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    @PostMapping("/manager/approve/{id}")    public String approveRequest(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            Request req = service.approveRequest(id);
            authService.log(user.getUsername(), "APPROVE_REQUEST", "Request " + id + " approved");
            redirects.addFlashAttribute("success", "Request " + req.getId() + " approved.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    @PostMapping("/manager/reject/{id}")
    public String rejectRequest(@PathVariable String id, @RequestParam(required = false) String reason,
                                HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            Request req = service.rejectRequest(id, reason);
            authService.log(user.getUsername(), "REJECT_REQUEST", "Request " + id + " rejected. Reason: " + reason);
            redirects.addFlashAttribute("success", "Request " + req.getId() + " rejected.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    @PostMapping("/manager/request/{id}/delete")
    public String deleteManagerRequest(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.BLOOD_BANK_MANAGER);
            boolean ok = service.deleteRequest(id);
            if (ok) {
                authService.log(user.getUsername(), "DELETE_REQUEST", "Purged request " + id);
                redirects.addFlashAttribute("success", "Request purged successfully.");
            } else {
                redirects.addFlashAttribute("error", "Request not found.");
            }
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/manager";
    }

    // IT Operations Portal
    @GetMapping("/it")
    public String itPortal(Model model, HttpSession session) {
        UserAccount user = requireRole(session, UserRole.IT_ADMIN);
        model.addAttribute("user", user);
        model.addAttribute("permissions", authService.getAllPermissions());
        model.addAttribute("allScopes", authService.getAllPermissionScopes());
        model.addAttribute("operationalScopes", authService.getOperationalScopes());
        model.addAttribute("adminScopes", AuthService.ADMIN_CONSOLE_SCOPES);
        var allUsers = authService.getAllUsers();
        model.addAttribute("users", allUsers);
        model.addAttribute("staffUsers", allUsers.stream()
                .filter(u -> u.getDonorId() == null || u.getDonorId().isBlank())
                .filter(u -> !"admin".equalsIgnoreCase(u.getUsername())).toList());
        model.addAttribute("donorUsers", allUsers.stream()
                .filter(u -> u.getDonorId() != null && !u.getDonorId().isBlank()).toList());
        model.addAttribute("auditLogs", authService.getRecentAuditLogs());
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("allBranches", authService.getAllBranches());
        model.addAttribute("activeBranches", authService.getActiveBranches());
        return "portal/it";
    }

    @PostMapping("/it/branches/add")
    public String addBranch(@RequestParam String branchName, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            authService.addBranch(branchName, actor.getUsername());
            redirects.addFlashAttribute("success", "Storage facility branch '" + branchName.trim() + "' added successfully and activated.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/branches/delete")
    public String deleteBranch(@RequestParam String branchName, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            authService.deleteBranch(branchName, actor.getUsername());
            redirects.addFlashAttribute("success", "Storage facility branch '" + branchName.trim() + "' deleted successfully.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/branches/rename")
    public String renameBranch(@RequestParam String oldName, @RequestParam String newName,
                               HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            authService.renameBranch(oldName, newName, actor.getUsername());
            redirects.addFlashAttribute("success", "Storage facility branch '" + oldName.trim()
                    + "' renamed to '" + newName.trim() + "' successfully.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/branch-access")
    public String updateBranchAccess(@RequestParam(name = "branches", required = false) List<String> branches,
                                     HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            authService.updateBranchAccess(branches, actor.getUsername());
            redirects.addFlashAttribute("success", "Storage facility branch access configuration updated successfully.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/permissions")
    public String updatePermissions(@RequestParam UserRole role, @RequestParam String permissions,
                                    HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount user = requireRole(session, UserRole.IT_ADMIN);
            authService.updatePermissions(role, permissions, user.getUsername());
            redirects.addFlashAttribute("success", "Permissions updated for " + role.getDisplayName());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/users")
    public String createUser(@RequestParam String username, @RequestParam String password,
                             @RequestParam String fullName, @RequestParam UserRole role,
                             @RequestParam(required = false) String hospital,
                             HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            authService.createUser(username, password, fullName, role, hospital);
            authService.log(actor.getUsername(), "CREATE_USER", "Created user " + username + " (" + role + ")");
            redirects.addFlashAttribute("success", "User account created: " + username);
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            UserAccount u = authService.toggleUserStatus(id);
            authService.log(actor.getUsername(), "TOGGLE_USER_STATUS", "User " + u.getUsername() + " status → " + (u.isActive() ? "Active" : "Inactive"));
            redirects.addFlashAttribute("success", "Account status updated for " + u.getUsername() + " (" + (u.isActive() ? "Active" : "Deactivated") + ")");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/users/{id}/edit")
    public String editUser(@PathVariable String id, @RequestParam String fullName,
                           @RequestParam UserRole role, @RequestParam(required = false) String hospital,
                           @RequestParam(required = false) String newPassword,
                           HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            UserAccount u = authService.updateUser(id, fullName, role, hospital, newPassword);
            authService.log(actor.getUsername(), "UPDATE_USER", "Updated user " + u.getUsername());
            redirects.addFlashAttribute("success", "User profile updated for " + u.getUsername());
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }

    @PostMapping("/it/users/{id}/delete")
    public String deleteUser(@PathVariable String id, HttpSession session, RedirectAttributes redirects) {
        try {
            UserAccount actor = requireRole(session, UserRole.IT_ADMIN);
            authService.deleteUser(id, actor.getUsername());
            authService.log(actor.getUsername(), "DELETE_USER", "Deleted user ID " + id);
            redirects.addFlashAttribute("success", "User account removed permanently.");
        } catch (Exception e) {
            redirects.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/portal/it";
    }
}
