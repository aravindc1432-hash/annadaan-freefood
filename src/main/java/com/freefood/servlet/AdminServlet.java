package com.freefood.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freefood.dao.FoodEventDAO;
import com.freefood.dao.UserDAO;
import com.freefood.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/**
 * Admin-only REST API.
 * All endpoints require an admin session.
 *
 * GET  /api/admin/events        - all events (no expiry filter)
 * GET  /api/admin/users         - all users
 * DELETE /api/admin/events/{id} - force delete any event
 * DELETE /api/admin/users/{id}  - delete a user
 * GET  /api/admin/stats         - summary stats
 */
@WebServlet("/api/admin/*")
public class AdminServlet extends HttpServlet {
    private final FoodEventDAO eventDAO = new FoodEventDAO();
    private final UserDAO      userDAO  = new UserDAO();
    private final ObjectMapper mapper   = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        if (!isAdmin(req)) { resp.setStatus(403); error(resp, "Admin access required"); return; }
        String path = req.getPathInfo();
        try {
            if ("/events".equals(path)) {
                var events = eventDAO.getAllForAdmin();
                Map<String,Object> r = new HashMap<>();
                r.put("success", true); r.put("events", events); r.put("count", events.size());
                mapper.writeValue(resp.getWriter(), r);
            } else if ("/users".equals(path)) {
                var users = userDAO.getAllUsers();
                // Don't expose password hashes
                List<Map<String,Object>> safe = new ArrayList<>();
                for (User u : users) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("id", u.getId()); m.put("username", u.getUsername());
                    m.put("displayName", u.getDisplayName()); m.put("phone", u.getPhone());
                    m.put("isAdmin", u.isAdmin()); m.put("createdAt", u.getCreatedAt());
                    safe.add(m);
                }
                Map<String,Object> r = new HashMap<>();
                r.put("success", true); r.put("users", safe);
                mapper.writeValue(resp.getWriter(), r);
            } else if ("/stats".equals(path)) {
                var events = eventDAO.getAllForAdmin();
                var users  = userDAO.getAllUsers();
                int totalGoing = events.stream().mapToInt(e -> e.getGoingCount()).sum();
                int totalCheckin = events.stream().mapToInt(e -> e.getCheckinCount()).sum();
                long witnessed = events.stream().filter(e -> e.isAddedByWitness()).count();
                Map<String,Object> r = new HashMap<>();
                r.put("success", true);
                r.put("totalEvents", events.size());
                r.put("totalUsers", users.size());
                r.put("totalGoing", totalGoing);
                r.put("totalCheckins", totalCheckin);
                r.put("witnessReports", witnessed);
                mapper.writeValue(resp.getWriter(), r);
            }
        } catch (Exception e) { resp.setStatus(500); error(resp, e.getMessage()); }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        if (!isAdmin(req)) { resp.setStatus(403); error(resp, "Admin access required"); return; }
        String path = req.getPathInfo();
        try {
            if (path != null && path.startsWith("/events/")) {
                long id = Long.parseLong(path.split("/")[2]);
                boolean ok = eventDAO.deleteById(id);
                ok(resp, ok ? "Event deleted" : "Not found");
            } else if (path != null && path.startsWith("/users/")) {
                long id = Long.parseLong(path.split("/")[2]);
                boolean ok = userDAO.deleteUser(id);
                ok(resp, ok ? "User deleted" : "Cannot delete admin or user not found");
            }
        } catch (Exception e) { resp.setStatus(500); error(resp, e.getMessage()); }
    }

    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return false;
        User u = (User) s.getAttribute("user");
        return u != null && u.isAdmin();
    }
    private void error(HttpServletResponse resp, String msg) throws IOException {
        mapper.writeValue(resp.getWriter(), Map.of("success", false, "error", msg));
    }
    private void ok(HttpServletResponse resp, String msg) throws IOException {
        mapper.writeValue(resp.getWriter(), Map.of("success", true, "message", msg));
    }
}
