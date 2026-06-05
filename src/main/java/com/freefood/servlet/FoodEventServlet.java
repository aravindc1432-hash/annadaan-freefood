package com.freefood.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.freefood.dao.FoodEventDAO;
import com.freefood.model.FoodEvent;
import com.freefood.model.User;
import com.freefood.util.DatabaseUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

/**
 * GET    /api/events               ?lat=&lon=&page=&pageSize=
 * GET    /api/events/{id}
 * POST   /api/events               multipart: fields + optional photo file
 * PUT    /api/events/{id}          multipart
 * DELETE /api/events/{id}
 * POST   /api/events/{id}/going    { sessionId, userName }
 * POST   /api/events/{id}/checkin  { sessionId, checkerName, lat, lon, note }
 * GET    /api/events/{id}/checkins
 * POST   /api/events/{id}/duplicate { newDate }
 */
@WebServlet("/api/events/*")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024) // 5 MB max photo
public class FoodEventServlet extends HttpServlet {

    private final FoodEventDAO dao    = new FoodEventDAO();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── GET ───────────────────────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getPathInfo();
        try {
            if (path == null || path.equals("/")) {
                // list with pagination
                double lat      = parseDouble(req.getParameter("lat"), 0);
                double lon      = parseDouble(req.getParameter("lon"), 0);
                int    page     = parseInt(req.getParameter("page"), 1);
                int    pageSize = parseInt(req.getParameter("pageSize"), 10);
                String sid      = req.getSession(true).getId();

                List<FoodEvent> events;
                if (lat != 0 || lon != 0)
                    events = dao.getActiveEventsSortedByDistance(lat, lon, sid, page, pageSize);
                else
                    events = dao.getAllActiveEvents(page, pageSize);

                int total = dao.getTotalActiveCount();
                Map<String,Object> r = new HashMap<>();
                r.put("success", true);
                r.put("events", events);
                r.put("total", total);
                r.put("page", page);
                r.put("pageSize", pageSize);
                r.put("totalPages", (int) Math.ceil((double) total / pageSize));
                mapper.writeValue(resp.getWriter(), r);

            } else {
                String[] parts = path.split("/");
                long id = Long.parseLong(parts[1]);

                if (parts.length == 3 && "checkins".equals(parts[2])) {
                    // GET checkins for an event
                    List<Map<String,Object>> checkins = dao.getCheckins(id);
                    Map<String,Object> r = new HashMap<>();
                    r.put("success", true);
                    r.put("checkins", checkins);
                    mapper.writeValue(resp.getWriter(), r);
                } else {
                    FoodEvent ev = dao.getById(id);
                    if (ev == null) { resp.setStatus(404); error(resp, "Not found"); return; }
                    Map<String,Object> r = new HashMap<>();
                    r.put("success", true);
                    r.put("event", ev);
                    mapper.writeValue(resp.getWriter(), r);
                }
            }
        } catch (Exception e) {
            resp.setStatus(500); error(resp, e.getMessage());
        }
    }

    // ── POST ──────────────────────────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getPathInfo();

        try {
            // Sub-action routes
            if (path != null && path.matches("/\\d+/going")) {
                handleGoing(req, resp, Long.parseLong(path.split("/")[1])); return;
            }
            if (path != null && path.matches("/\\d+/checkin")) {
                handleCheckin(req, resp, Long.parseLong(path.split("/")[1])); return;
            }
            if (path != null && path.matches("/\\d+/duplicate")) {
                handleDuplicate(req, resp, Long.parseLong(path.split("/")[1])); return;
            }

            // Create event
            FoodEvent ev = buildEventFromRequest(req);
            String validErr = validate(ev);
            if (validErr != null) { resp.setStatus(400); error(resp, validErr); return; }

            // Handle photo upload
            Part photo = getPhotoPart(req);
            if (photo != null && photo.getSize() > 0) {
                String filename = savePhoto(photo, null);
                ev.setPhotoPath(filename);
            }

            long newId = dao.save(ev);
            resp.setStatus(201);
            Map<String,Object> r = new HashMap<>();
            r.put("success", true);
            r.put("message", "Event posted! Thank you for sharing.");
            r.put("id", newId);
            mapper.writeValue(resp.getWriter(), r);

        } catch (Exception e) {
            resp.setStatus(500); error(resp, e.getMessage());
        }
    }

    // ── PUT ───────────────────────────────────────────────────────────────────
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) { resp.setStatus(400); error(resp, "ID required"); return; }
        try {
            long id = Long.parseLong(path.replaceAll("[^0-9]", ""));
            FoodEvent existing = dao.getById(id);
            if (existing == null) { resp.setStatus(404); error(resp, "Not found"); return; }

            FoodEvent ev = buildEventFromRequest(req);
            ev.setId(id);

            Part photo = getPhotoPart(req);
            if (photo != null && photo.getSize() > 0) {
                // delete old photo
                if (existing.getPhotoPath() != null)
                    new File(DatabaseUtil.UPLOAD_DIR, existing.getPhotoPath()).delete();
                ev.setPhotoPath(savePhoto(photo, null));
            }

            boolean ok = dao.update(ev);
            Map<String,Object> r = new HashMap<>();
            r.put("success", ok);
            r.put("message", ok ? "Event updated!" : "Update failed");
            mapper.writeValue(resp.getWriter(), r);
        } catch (Exception e) {
            resp.setStatus(500); error(resp, e.getMessage());
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        String path = req.getPathInfo();
        try {
            long id = Long.parseLong(path.replaceAll("[^0-9]", ""));
            FoodEvent ev = dao.getById(id);
            if (ev != null && ev.getPhotoPath() != null)
                new File(DatabaseUtil.UPLOAD_DIR, ev.getPhotoPath()).delete();
            boolean ok = dao.deleteById(id);
            Map<String,Object> r = new HashMap<>();
            r.put("success", ok);
            r.put("message", ok ? "Event deleted" : "Not found");
            mapper.writeValue(resp.getWriter(), r);
        } catch (Exception e) {
            resp.setStatus(500); error(resp, e.getMessage());
        }
    }

    // ── GOING ─────────────────────────────────────────────────────────────────
    private void handleGoing(HttpServletRequest req, HttpServletResponse resp, long eventId)
            throws IOException, SQLException {
        Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
        String sessionId = (String) body.getOrDefault("sessionId", req.getSession(true).getId());
        String userName  = (String) body.getOrDefault("userName", "Anonymous");
        String result    = dao.toggleGoing(eventId, sessionId, userName);
        FoodEvent ev     = dao.getById(eventId);
        Map<String,Object> r = new HashMap<>();
        r.put("success", true);
        r.put("action", result);
        r.put("goingCount", ev != null ? ev.getGoingCount() : 0);
        r.put("message", "added".equals(result) ? "You're going! 🙌" : "Removed from going list");
        mapper.writeValue(resp.getWriter(), r);
    }

    // ── CHECK-IN ──────────────────────────────────────────────────────────────
    private void handleCheckin(HttpServletRequest req, HttpServletResponse resp, long eventId)
            throws IOException, SQLException {
        Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
        String sessionId   = (String) body.getOrDefault("sessionId", req.getSession(true).getId());
        String checkerName = (String) body.getOrDefault("checkerName", "Someone");
        double lat  = parseDouble(String.valueOf(body.getOrDefault("lat", "0")), 0);
        double lon  = parseDouble(String.valueOf(body.getOrDefault("lon", "0")), 0);
        String note = (String) body.getOrDefault("note", "");

        String result = dao.checkIn(eventId, sessionId, checkerName, lat, lon, note);
        Map<String,Object> r = new HashMap<>();

        if (result.startsWith("already:")) {
            String existingName = result.substring(8);
            r.put("success", false);
            r.put("alreadyCheckedIn", true);
            r.put("checkerName", existingName);
            r.put("message", existingName + " already confirmed this location from your device.");
        } else {
            FoodEvent ev = dao.getById(eventId);
            r.put("success", true);
            r.put("message", "✅ Location confirmed! Thank you, " + checkerName);
            r.put("checkinCount", ev != null ? ev.getCheckinCount() : 0);
        }
        mapper.writeValue(resp.getWriter(), r);
    }

    // ── DUPLICATE ─────────────────────────────────────────────────────────────
    private void handleDuplicate(HttpServletRequest req, HttpServletResponse resp, long eventId)
            throws IOException, SQLException {
        Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
        String newDate = (String) body.get("newDate");
        if (newDate == null || newDate.trim().isEmpty()) { error(resp, "newDate is required"); return; }
        long newId = dao.duplicate(eventId, newDate);
        Map<String,Object> r = new HashMap<>();
        r.put("success", true);
        r.put("message", "Event duplicated for " + newDate);
        r.put("newId", newId);
        mapper.writeValue(resp.getWriter(), r);
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private FoodEvent buildEventFromRequest(HttpServletRequest req) throws IOException, ServletException {
        FoodEvent ev = new FoodEvent();
        // Support both multipart and JSON
        String ct = req.getContentType();
        if (ct != null && ct.startsWith("multipart/")) {
            ev.setProviderName(part(req, "providerName"));
            ev.setContactNumber(part(req, "contactNumber"));
            ev.setFoodDescription(part(req, "foodDescription"));
            ev.setAddress(part(req, "address"));
            ev.setCity(part(req, "city"));
            ev.setState(part(req, "state"));
            ev.setLatitude(parseDouble(part(req, "latitude"), 0));
            ev.setLongitude(parseDouble(part(req, "longitude"), 0));
            ev.setEventDate(part(req, "eventDate"));
            ev.setStartTime(part(req, "startTime"));
            ev.setEndTime(part(req, "endTime"));
            ev.setQuantity(parseInt(part(req, "quantity"), 0));
            ev.setNotes(part(req, "notes"));
            ev.setAddedByWitness("true".equalsIgnoreCase(part(req, "addedByWitness")));
            ev.setWitnessName(part(req, "witnessName"));
        } else {
            Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
            ev.setProviderName((String) body.get("providerName"));
            ev.setContactNumber((String) body.get("contactNumber"));
            ev.setFoodDescription((String) body.get("foodDescription"));
            ev.setAddress((String) body.get("address"));
            ev.setCity((String) body.get("city"));
            ev.setState((String) body.get("state"));
            ev.setLatitude(parseDouble(String.valueOf(body.getOrDefault("latitude","0")), 0));
            ev.setLongitude(parseDouble(String.valueOf(body.getOrDefault("longitude","0")), 0));
            ev.setEventDate((String) body.get("eventDate"));
            ev.setStartTime((String) body.get("startTime"));
            ev.setEndTime((String) body.get("endTime"));
            ev.setQuantity(parseInt(String.valueOf(body.getOrDefault("quantity","0")), 0));
            ev.setNotes((String) body.get("notes"));
            ev.setAddedByWitness(Boolean.TRUE.equals(body.get("addedByWitness")));
            ev.setWitnessName((String) body.get("witnessName"));
        }
        // Attach logged-in user
        HttpSession session = req.getSession(false);
        if (session != null) {
            User user = (User) session.getAttribute("user");
            if (user != null) ev.setUserId(user.getId());
        }
        return ev;
    }

    private String part(HttpServletRequest req, String name) throws IOException, ServletException {
        Part p = req.getPart(name);
        if (p == null) return null;
        try (InputStream is = p.getInputStream()) {
            return new String(is.readAllBytes()).trim();
        }
    }

    private Part getPhotoPart(HttpServletRequest req) throws IOException, ServletException {
        String ct = req.getContentType();
        if (ct == null || !ct.startsWith("multipart/")) return null;
        try { return req.getPart("photo"); } catch (Exception e) { return null; }
    }

    private String savePhoto(Part photo, String existingName) throws IOException {
        String orig = photo.getSubmittedFileName();
        String ext  = orig != null && orig.contains(".") ? orig.substring(orig.lastIndexOf('.')) : ".jpg";
        String name = UUID.randomUUID().toString() + ext;
        Path dest   = Paths.get(DatabaseUtil.UPLOAD_DIR, name);
        try (InputStream in = photo.getInputStream()) {
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
        return name;
    }

    private String validate(FoodEvent e) {
        if (e.getProviderName() == null || e.getProviderName().trim().isEmpty()) return "Provider name required";
        if (e.getFoodDescription() == null || e.getFoodDescription().trim().isEmpty()) return "Food description required";
        if (e.getAddress() == null || e.getAddress().trim().isEmpty()) return "Address required";
        if (e.getCity() == null || e.getCity().trim().isEmpty()) return "City required";
        if (e.getState() == null || e.getState().trim().isEmpty()) return "State required";
        if (e.getEventDate() == null || e.getEventDate().trim().isEmpty()) return "Event date required";
        if (e.getStartTime() == null || e.getStartTime().trim().isEmpty()) return "Start time required";
        if (e.getEndTime() == null || e.getEndTime().trim().isEmpty()) return "End time required";
        if (e.getLatitude() == 0.0 && e.getLongitude() == 0.0) return "GPS coordinates required";
        return null;
    }

    private void error(HttpServletResponse resp, String msg) throws IOException {
        Map<String,Object> r = new HashMap<>();
        r.put("success", false); r.put("error", msg);
        mapper.writeValue(resp.getWriter(), r);
    }
    private double parseDouble(String s, double def) { try { return s!=null ? Double.parseDouble(s) : def; } catch(Exception e){return def;} }
    private int parseInt(String s, int def) { try { return s!=null ? Integer.parseInt(s) : def; } catch(Exception e){return def;} }
}
