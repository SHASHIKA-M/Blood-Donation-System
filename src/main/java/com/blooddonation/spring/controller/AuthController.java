package com.blooddonation.spring.controller;

import com.blooddonation.spring.model.UserAccount;
import com.blooddonation.spring.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private AuthService authService;

    @GetMapping("/login")
    public String loginForm(HttpSession session) {
        if (session.getAttribute(AuthService.SESSION_USER) != null) {
            UserAccount user = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
            return "redirect:" + authService.getHomePath(user);
        }
        return "login";
    }

    @PostMapping("/login")
    public String loginSubmit(@RequestParam String username, @RequestParam String password,
                              HttpSession session, RedirectAttributes redirects) {
        var userOpt = authService.authenticate(username, password);
        if (userOpt.isEmpty()) {
            redirects.addFlashAttribute("error", "Invalid username or password.");
            return "redirect:/login";
        }
        UserAccount user = userOpt.get();
        session.setAttribute(AuthService.SESSION_USER, user);
        session.setAttribute(AuthService.SESSION_ROLE, user.getRole());
        authService.log(user.getUsername(), "LOGIN", "User logged in as " + user.getRole().getDisplayName());
        redirects.addFlashAttribute("success", "Welcome, " + user.getFullName() + "!");
        return "redirect:" + authService.getHomePath(user);
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirects) {
        UserAccount user = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (user != null) authService.log(user.getUsername(), "LOGOUT", "User logged out");
        session.invalidate();
        redirects.addFlashAttribute("success", "Logged out successfully.");
        return "redirect:/login";
    }
}
