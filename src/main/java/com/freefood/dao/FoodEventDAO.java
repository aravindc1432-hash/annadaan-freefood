package com.freefood.dao;

import com.freefood.model.FoodEvent;
import com.freefood.util.DatabaseUtil;
import com.freefood.util.DistanceUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FoodEventDAO {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ── SAVE ──────────────────────────────────────────────────────────────────
    public long save(FoodEvent e) throws SQLException {
        String sql = """
            INSERT INTO food_events
              (user_id,provider_name,contact_number,food_description,address,city,state,
               latitude,longitude,event_date,start_time,end_time,quantity,notes,photo_path,
               added_by_witness,witness_name)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, e.getUserId());
            ps.setString(2, e.getProviderName());
            ps.setString(3, e.getContactNumber());
            ps.setString(4, e.getFoodDescription());
            ps.setString(5, e.getAddress());
            ps.setString(6, e.getCity());
            ps.setString(7, e.getState());
            ps.setDouble(8, e.getLatitude());
            ps.setDouble(9, e.getLongitude());
            ps.setString(10, e.getEventDate());
            ps.setString(11, e.getStartTime());
            ps.setString(12, e.getEndTime());
            ps.setInt(13, e.getQuantity());
            ps.setString(14, e.getNotes());
            ps.setString(15, e.getPhotoPath());
            ps.setInt(16, e.isAddedByWitness() ? 1 : 0);
            ps.setString(17, e.getWitnessName());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { if (k.next()) return k.getLong(1); }
        }
        return -1;
    }

    // ── GET ACTIVE SORTED BY DISTANCE ─────────────────────────────────────────
    public List<FoodEvent> getActiveEventsSortedByDistance(double lat, double lon, String sessionId,
                                                            int page, int pageSize) throws SQLException {
        deleteExpiredEvents();
        List<FoodEvent> list = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM food_events ORDER BY event_date ASC, start_time ASC LIMIT ? OFFSET ?";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FoodEvent ev = mapRow(rs);
                    ev.setDistanceKm(DistanceUtil.calculate(lat, lon, ev.getLatitude(), ev.getLongitude()));
                    ev.setGoingCount(countGoing(c, ev.getId()));
                    ev.setCheckinCount(countCheckins(c, ev.getId()));
                    if (sessionId != null) ev.setUserIsGoing(isGoing(c, ev.getId(), sessionId));
                    list.add(ev);
                }
            }
        }
        list.sort(Comparator.comparingDouble(FoodEvent::getDistanceKm));
        return list;
    }

    public List<FoodEvent> getAllActiveEvents(int page, int pageSize) throws SQLException {
        deleteExpiredEvents();
        List<FoodEvent> list = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM food_events ORDER BY event_date ASC, start_time ASC LIMIT ? OFFSET ?";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FoodEvent ev = mapRow(rs);
                    ev.setGoingCount(countGoing(c, ev.getId()));
                    ev.setCheckinCount(countCheckins(c, ev.getId()));
                    list.add(ev);
                }
            }
        }
        return list;
    }

    public int getTotalActiveCount() throws SQLException {
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM food_events");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public FoodEvent getById(long id) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM food_events WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FoodEvent ev = mapRow(rs);
                    ev.setGoingCount(countGoing(c, id));
                    ev.setCheckinCount(countCheckins(c, id));
                    return ev;
                }
            }
        }
        return null;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    public boolean update(FoodEvent e) throws SQLException {
        String sql = """
            UPDATE food_events SET
              provider_name=?,contact_number=?,food_description=?,address=?,city=?,state=?,
              latitude=?,longitude=?,event_date=?,start_time=?,end_time=?,quantity=?,notes=?,
              photo_path=COALESCE(?,photo_path),added_by_witness=?,witness_name=?
            WHERE id=?""";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, e.getProviderName());
            ps.setString(2, e.getContactNumber());
            ps.setString(3, e.getFoodDescription());
            ps.setString(4, e.getAddress());
            ps.setString(5, e.getCity());
            ps.setString(6, e.getState());
            ps.setDouble(7, e.getLatitude());
            ps.setDouble(8, e.getLongitude());
            ps.setString(9, e.getEventDate());
            ps.setString(10, e.getStartTime());
            ps.setString(11, e.getEndTime());
            ps.setInt(12, e.getQuantity());
            ps.setString(13, e.getNotes());
            ps.setString(14, e.getPhotoPath());
            ps.setInt(15, e.isAddedByWitness() ? 1 : 0);
            ps.setString(16, e.getWitnessName());
            ps.setLong(17, e.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    public boolean deleteById(long id) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM food_events WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public int deleteExpiredEvents() throws SQLException {
        String today   = LocalDate.now().format(DATE_FMT);
        String nowTime = LocalTime.now().format(TIME_FMT);
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "DELETE FROM food_events WHERE event_date<? OR (event_date=? AND end_time<?)")) {
            ps.setString(1, today); ps.setString(2, today); ps.setString(3, nowTime);
            int n = ps.executeUpdate();
            if (n > 0) System.out.println("[DAO] Cleaned " + n + " expired events");
            return n;
        }
    }

    // ── DUPLICATE ─────────────────────────────────────────────────────────────
    public long duplicate(long id, String newDate) throws SQLException {
        FoodEvent src = getById(id);
        if (src == null) return -1;
        src.setId(null);
        src.setEventDate(newDate);
        return save(src);
    }

    // ── GOING ─────────────────────────────────────────────────────────────────
    public String toggleGoing(long eventId, String sessionId, String userName) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection()) {
            if (isGoing(c, eventId, sessionId)) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM going WHERE event_id=? AND session_id=?")) {
                    ps.setLong(1, eventId); ps.setString(2, sessionId);
                    ps.executeUpdate();
                }
                return "removed";
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT OR IGNORE INTO going(event_id,session_id,user_name) VALUES(?,?,?)")) {
                    ps.setLong(1, eventId); ps.setString(2, sessionId); ps.setString(3, userName);
                    ps.executeUpdate();
                }
                return "added";
            }
        }
    }

    // ── CHECK-IN ──────────────────────────────────────────────────────────────
    /**
     * Returns "added" if first check-in from this session,
     * "already" if this session already checked in (with existing checker name),
     * or throws on error.
     */
    public String checkIn(long eventId, String sessionId, String checkerName,
                          double lat, double lon, String note) throws SQLException {
        try (Connection c = DatabaseUtil.getConnection()) {
            // Check if THIS session already checked in
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT checker_name FROM checkins WHERE event_id=? AND session_id=?")) {
                ps.setLong(1, eventId); ps.setString(2, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return "already:" + rs.getString("checker_name");
                }
            }
            // Insert new check-in
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO checkins(event_id,session_id,checker_name,latitude,longitude,note) VALUES(?,?,?,?,?,?)")) {
                ps.setLong(1, eventId); ps.setString(2, sessionId);
                ps.setString(3, checkerName);
                ps.setDouble(4, lat); ps.setDouble(5, lon);
                ps.setString(6, note);
                ps.executeUpdate();
            }
            // If this check-in has GPS and it's more accurate than original, optionally update event coords
            if (lat != 0 && lon != 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE food_events SET latitude=?, longitude=? WHERE id=? AND added_by_witness=1")) {
                    ps.setDouble(1, lat); ps.setDouble(2, lon); ps.setLong(3, eventId);
                    ps.executeUpdate();
                }
            }
            return "added";
        }
    }

    public List<Map<String,Object>> getCheckins(long eventId) throws SQLException {
        List<Map<String,Object>> list = new ArrayList<>();
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM checkins WHERE event_id=? ORDER BY created_at DESC")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> m = new LinkedHashMap<>();
                    m.put("checkerName", rs.getString("checker_name"));
                    m.put("note", rs.getString("note"));
                    m.put("createdAt", rs.getString("created_at"));
                    list.add(m);
                }
            }
        }
        return list;
    }

    // ── ADMIN: all events without pagination ──────────────────────────────────
    public List<FoodEvent> getAllForAdmin() throws SQLException {
        List<FoodEvent> list = new ArrayList<>();
        String sql = "SELECT * FROM food_events ORDER BY event_date DESC, start_time DESC";
        try (Connection c = DatabaseUtil.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                FoodEvent ev = mapRow(rs);
                ev.setGoingCount(countGoing(c, ev.getId()));
                ev.setCheckinCount(countCheckins(c, ev.getId()));
                list.add(ev);
            }
        }
        return list;
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────
    private int countGoing(Connection c, long eventId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM going WHERE event_id=?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }
    private int countCheckins(Connection c, long eventId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM checkins WHERE event_id=?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }
    private boolean isGoing(Connection c, long eventId, String sessionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM going WHERE event_id=? AND session_id=?")) {
            ps.setLong(1, eventId); ps.setString(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private FoodEvent mapRow(ResultSet rs) throws SQLException {
        FoodEvent e = new FoodEvent();
        e.setId(rs.getLong("id"));
        e.setUserId((Long) rs.getObject("user_id"));
        e.setProviderName(rs.getString("provider_name"));
        e.setContactNumber(rs.getString("contact_number"));
        e.setFoodDescription(rs.getString("food_description"));
        e.setAddress(rs.getString("address"));
        e.setCity(rs.getString("city"));
        e.setState(rs.getString("state"));
        e.setLatitude(rs.getDouble("latitude"));
        e.setLongitude(rs.getDouble("longitude"));
        e.setEventDate(rs.getString("event_date"));
        e.setStartTime(rs.getString("start_time"));
        e.setEndTime(rs.getString("end_time"));
        e.setQuantity(rs.getInt("quantity"));
        e.setNotes(rs.getString("notes"));
        e.setPhotoPath(rs.getString("photo_path"));
        e.setAddedByWitness(rs.getInt("added_by_witness") == 1);
        e.setWitnessName(rs.getString("witness_name"));
        e.setCreatedAt(rs.getString("created_at"));
        return e;
    }
}
