package com.freefood.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freefood.dao.UserDAO;
import com.freefood.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

/**
 * POST /api/auth/register  { username, password, displayName, phone }
 * POST /api/auth/login     { username, password }
 * POST /api/auth/logout
 * GET  /api/auth/me
 */
@WebServlet("/api/auth/*")
public class AuthServlet extends HttpServlet {
    private final UserDAO userDAO = new UserDAO();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getPathInfo();
        if ("/me".equals(path)) {
            HttpSession session = req.getSession(false);
            User user = session != null ? (User) session.getAttribute("user") : null;
            Map<String,Object> r = new HashMap<>();
            r.put("success", true);
            r.put("loggedIn", user != null);
            if (user != null) {
                r.put("userId", user.getId());
                r.put("username", user.getUsername());
                r.put("displayName", user.getDisplayName());
                r.put("isAdmin", user.isAdmin());
            }
            mapper.writeValue(resp.getWriter(), r);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getPathInfo();
        Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);

        try {
            if ("/register".equals(path)) {
                String username    = (String) body.get("username");
                String password    = (String) body.get("password");
                String displayName = (String) body.get("displayName");
                String phone       = (String) body.getOrDefault("phone", "");

                if (username == null || username.trim().length() < 3) { error(resp, "Username must be at least 3 characters"); return; }
                if (password == null || password.length() < 6)        { error(resp, "Password must be at least 6 characters"); return; }
                if (displayName == null || displayName.trim().isEmpty()){ error(resp, "Display name is required"); return; }
                if (userDAO.usernameExists(username))                  { error(resp, "Username already taken"); return; }

                long id = userDAO.register(username, password, displayName, phone);
                User user = userDAO.getById(id);
                HttpSession session = req.getSession(true);
                session.setAttribute("user", user);

                Map<String,Object> r = new HashMap<>();
                r.put("success", true);
                r.put("message", "Account created! Welcome, " + displayName);
                r.put("userId", id);
                r.put("displayName", displayName);
                mapper.writeValue(resp.getWriter(), r);

            } else if ("/login".equals(path)) {
                String username = (String) body.get("username");
                String password = (String) body.get("password");
                User user = userDAO.authenticate(username, password);
                if (user == null) { error(resp, "Invalid username or password"); return; }

                HttpSession session = req.getSession(true);
                session.setAttribute("user", user);

                Map<String,Object> r = new HashMap<>();
                r.put("success", true);
                r.put("message", "Welcome back, " + user.getDisplayName() + "!");
                r.put("userId", user.getId());
                r.put("displayName", user.getDisplayName());
                r.put("isAdmin", user.isAdmin());
                mapper.writeValue(resp.getWriter(), r);

            } else if ("/logout".equals(path)) {
                HttpSession session = req.getSession(false);
                if (session != null) session.invalidate();
                Map<String,Object> r = new HashMap<>();
                r.put("success", true);
                r.put("message", "Logged out");
                mapper.writeValue(resp.getWriter(), r);
            }
        } catch (Exception e) {
            resp.setStatus(500);
            error(resp, "Server error: " + e.getMessage());
        }
    }

    private void error(HttpServletResponse resp, String msg) throws IOException {
        Map<String,Object> r = new HashMap<>();
        r.put("success", false);
        r.put("error", msg);
        mapper.writeValue(resp.getWriter(), r);
    }
}
