package com.blooddonation.spring.config;

import com.blooddonation.spring.model.UserAccount;
import com.blooddonation.spring.model.UserRole;
import com.blooddonation.spring.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Autowired
    private AuthService authService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(AuthService.SESSION_USER) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        UserAccount user = (UserAccount) session.getAttribute(AuthService.SESSION_USER);
        if (user.getRole() == UserRole.IT_ADMIN) {
            return true;
        }

        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = (contextPath != null && !contextPath.isBlank()) ? uri.substring(contextPath.length()) : uri;

        String requiredPermission = getRequiredPermissionForPath(path);

        if (requiredPermission != null && !authService.hasPermission(user.getRole(), requiredPermission)) {
            session.setAttribute("error", "Access denied: You do not have permission to access this section of Admin Console.");
            if (authService.hasAnyAdminPermission(user.getRole())) {
                response.sendRedirect(request.getContextPath() + authService.getFirstAllowedAdminPath(user.getRole()));
            } else {
                response.sendRedirect(request.getContextPath() + authService.getHomePath(user));
            }
            return false;
        }

        if (!authService.hasAnyAdminPermission(user.getRole())) {
            session.setAttribute("error", "Access denied: Admin Console access restricted to authorized personnel.");
            response.sendRedirect(request.getContextPath() + authService.getHomePath(user));
            return false;
        }

        return true;
    }

    private String getRequiredPermissionForPath(String path) {
        if (path.startsWith("/admin/dashboard")) return "ADMIN_DASHBOARD";
        if (path.startsWith("/admin/donors")) return "ADMIN_DONORS";
        if (path.startsWith("/admin/requests")) return "ADMIN_REQUESTS";
        if (path.startsWith("/admin/inventory")) return "ADMIN_INVENTORY";
        if (path.startsWith("/admin/deliveries")) return "ADMIN_DELIVERIES";
        if (path.startsWith("/admin/messages")) return "ADMIN_MESSAGES";
        return "ACCESS_ADMIN_CONSOLE";
    }
}
