package com.onlinemedic.service;

import com.onlinemedic.util.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stores short-lived WebRTC signaling only; audio and video remain peer-to-peer. */
public class VideoCallService {
    private static final Set<String> SIGNAL_TYPES = Set.of("OFFER", "ANSWER", "ICE");
    private static final int MAX_SIGNAL_LENGTH = 20_000;
    private static final int RING_TIMEOUT_MINUTES = 3;
    private static final int ACTIVE_TIMEOUT_HOURS = 6;

    public Map<String, Object> start(long appointmentId, long actorId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                AppointmentAccess appointment = lockAppointment(c, appointmentId, actorId);
                try (PreparedStatement p = c.prepareStatement("SELECT id,started_by,status,created_at FROM consultation_video_calls WHERE appointment_id=? AND status IN ('RINGING','ACTIVE') ORDER BY id DESC FOR UPDATE")) {
                    p.setLong(1, appointmentId);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next()) {
                            long id = r.getLong("id");
                            long existingStarter = r.getLong("started_by");
                            String status = r.getString("status");
                            Timestamp created = r.getTimestamp("created_at");
                            boolean expired = created != null && ("RINGING".equals(status) && created.toLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(RING_TIMEOUT_MINUTES)) || "ACTIVE".equals(status) && created.toLocalDateTime().isBefore(LocalDateTime.now().minusHours(ACTIVE_TIMEOUT_HOURS)));
                            if (expired) {
                                endLocked(c, id, actorId, "Unanswered call expired.");
                            } else {
                                c.commit();
                                return callMap(id, existingStarter, status, false);
                            }
                        }
                    }
                }
                long callId;
                try (PreparedStatement p = c.prepareStatement("INSERT INTO consultation_video_calls(appointment_id,started_by,status) VALUES(?,?,'RINGING')", Statement.RETURN_GENERATED_KEYS)) {
                    p.setLong(1, appointmentId); p.setLong(2, actorId); p.executeUpdate();
                    try (ResultSet keys = p.getGeneratedKeys()) { if (!keys.next()) throw new SQLException("Video call identifier was not returned."); callId = keys.getLong(1); }
                }
                audit(c, actorId, "VIDEO CALL STARTED", "Started video call for appointment #" + appointmentId + ".");
                c.commit();
                return callMap(callId, actorId, "RINGING", true);
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    public Map<String, Object> accept(long appointmentId, long callId, long actorId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockAppointment(c, appointmentId, actorId);
                CallAccess call = lockCall(c, appointmentId, callId);
                if (call.startedBy == actorId) throw new IllegalArgumentException("The person who started the call cannot accept their own call.");
                if ("RINGING".equals(call.status)) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE consultation_video_calls SET status='ACTIVE',accepted_at=CURRENT_TIMESTAMP WHERE id=? AND status='RINGING'")) { p.setLong(1, callId); if (p.executeUpdate() != 1) throw new IllegalArgumentException("This call is no longer waiting for an answer."); }
                    audit(c, actorId, "VIDEO CALL ACCEPTED", "Accepted video call for appointment #" + appointmentId + ".");
                    call = new CallAccess(call.id, call.appointmentId, call.startedBy, "ACTIVE", call.patientId, call.doctorId);
                } else if (!"ACTIVE".equals(call.status)) throw new IllegalArgumentException("This video call has ended.");
                c.commit();
                return callMap(call.id, call.startedBy, call.status);
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    public void sendSignal(long appointmentId, long callId, long actorId, String type, String payload) throws SQLException {
        type = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        payload = payload == null ? "" : payload.trim();
        if (!SIGNAL_TYPES.contains(type)) throw new IllegalArgumentException("Unsupported video-call signal.");
        if (payload.length() > MAX_SIGNAL_LENGTH || !payload.startsWith("{") || !payload.endsWith("}")) throw new IllegalArgumentException("The video-call signal is invalid or too large.");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockAppointment(c, appointmentId, actorId);
                CallAccess call = lockCall(c, appointmentId, callId);
                if (!"RINGING".equals(call.status) && !"ACTIVE".equals(call.status)) throw new IllegalArgumentException("This video call has ended.");
                if ("OFFER".equals(type) && (call.startedBy != actorId || !"RINGING".equals(call.status))) throw new SecurityException("Only the caller can send the initial offer.");
                if ("ANSWER".equals(type) && (call.startedBy == actorId || !"ACTIVE".equals(call.status))) throw new SecurityException("Only the other appointment participant can answer this call.");
                long recipient = actorId == call.patientId ? call.doctorId : call.patientId;
                try (PreparedStatement p = c.prepareStatement("INSERT INTO consultation_video_signals(call_id,sender_id,recipient_id,signal_type,payload) VALUES(?,?,?,?,?)")) {
                    p.setLong(1, callId); p.setLong(2, actorId); p.setLong(3, recipient); p.setString(4, type); p.setString(5, payload); p.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    public Map<String, Object> poll(long appointmentId, long actorId, long afterId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            AppointmentAccess appointment = appointment(c, appointmentId, actorId);
            long callId = 0, startedBy = 0;
            String status = null;
            Timestamp createdAt = null;
            try (PreparedStatement p = c.prepareStatement("SELECT id,started_by,status,created_at FROM consultation_video_calls WHERE appointment_id=? ORDER BY id DESC")) {
                p.setLong(1, appointmentId);
                try (ResultSet r = p.executeQuery()) { if (r.next()) { callId = r.getLong("id"); startedBy = r.getLong("started_by"); status = r.getString("status"); createdAt = r.getTimestamp("created_at"); } }
            }
            boolean expired = createdAt != null && ("RINGING".equals(status) && createdAt.toLocalDateTime().isBefore(LocalDateTime.now().minusMinutes(RING_TIMEOUT_MINUTES)) || "ACTIVE".equals(status) && createdAt.toLocalDateTime().isBefore(LocalDateTime.now().minusHours(ACTIVE_TIMEOUT_HOURS)));
            if (expired) {
                endExpired(c, callId);
                status = "ENDED";
            }
            if (!"CONFIRMED".equals(appointment.status) && callId > 0 && ("RINGING".equals(status) || "ACTIVE".equals(status))) {
                endExpired(c, callId);
                status = "ENDED";
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appointmentId", appointment.id);
            result.put("appointmentStatus", appointment.status);
            result.put("call", status == null ? null : callMap(callId, startedBy, status));
            List<Map<String, Object>> signals = new ArrayList<>();
            if (callId > 0 && afterId >= 0) {
                try (PreparedStatement p = c.prepareStatement("SELECT id,sender_id,signal_type,payload FROM consultation_video_signals WHERE call_id=? AND recipient_id=? AND id>? ORDER BY id")) {
                    p.setLong(1, callId); p.setLong(2, actorId); p.setLong(3, afterId);
                    try (ResultSet r = p.executeQuery()) { while (r.next()) { Map<String, Object> signal = new LinkedHashMap<>(); signal.put("id", r.getLong("id")); signal.put("senderId", r.getLong("sender_id")); signal.put("type", r.getString("signal_type")); signal.put("payload", r.getString("payload")); signals.add(signal); } }
                }
            }
            result.put("signals", signals);
            return result;
        }
    }

    public void end(long appointmentId, long callId, long actorId) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockAppointment(c, appointmentId, actorId);
                CallAccess call = lockCall(c, appointmentId, callId);
                if (!"ENDED".equals(call.status)) endLocked(c, callId, actorId, "Call ended by an appointment participant.");
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    private void endExpired(Connection c, long callId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE consultation_video_calls SET status='ENDED',ended_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('RINGING','ACTIVE')")) { p.setLong(1, callId); p.executeUpdate(); }
        try (PreparedStatement p = c.prepareStatement("DELETE FROM consultation_video_signals WHERE call_id=?")) { p.setLong(1, callId); p.executeUpdate(); }
    }

    private void endLocked(Connection c, long callId, long actorId, String detail) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE consultation_video_calls SET status='ENDED',ended_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('RINGING','ACTIVE')")) { p.setLong(1, callId); p.executeUpdate(); }
        // SDP and ICE can reveal network addresses; discard signaling as soon as a call ends.
        try (PreparedStatement p = c.prepareStatement("DELETE FROM consultation_video_signals WHERE call_id=?")) { p.setLong(1, callId); p.executeUpdate(); }
        audit(c, actorId, "VIDEO CALL ENDED", detail);
    }

    private AppointmentAccess lockAppointment(Connection c, long id, long actor) throws SQLException {
        AppointmentAccess access = readAppointment(c, id, actor, true, false);
        if (!"CONFIRMED".equals(access.status)) throw new IllegalArgumentException("This appointment is no longer active. Book a new appointment to start another video call.");
        return access;
    }

    private AppointmentAccess appointment(Connection c, long id, long actor) throws SQLException { return readAppointment(c, id, actor, false, true); }

    private AppointmentAccess readAppointment(Connection c, long id, long actor, boolean lock, boolean allowCompleted) throws SQLException {
        String sql = "SELECT a.id,a.patient_id,a.doctor_id,a.status,u.role actor_role FROM appointments a JOIN users u ON u.id=? AND u.active=TRUE WHERE a.id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, actor); p.setLong(2, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Appointment not found.");
                AppointmentAccess access = new AppointmentAccess(r.getLong("id"), r.getLong("patient_id"), r.getLong("doctor_id"), r.getString("status"));
                if (actor != access.patientId && actor != access.doctorId) throw new SecurityException("Only the patient and doctor on this appointment can join its call.");
                String expectedRole = actor == access.patientId ? "PATIENT" : "DOCTOR";
                if (!expectedRole.equals(r.getString("actor_role"))) throw new SecurityException("Only the patient and doctor on this appointment can join its call.");
                boolean finishedAppointment = Set.of("COMPLETED", "CANCELLED", "NO_SHOW", "REJECTED").contains(access.status);
                if (!"CONFIRMED".equals(access.status) && !(allowCompleted && finishedAppointment)) throw new IllegalArgumentException("Video calls are available only for confirmed appointments.");
                return access;
            }
        }
    }

    private CallAccess lockCall(Connection c, long appointmentId, long callId) throws SQLException {
        long id, foundAppointmentId, startedBy, patientId, doctorId;
        String status;
        try (PreparedStatement p = c.prepareStatement("SELECT id,appointment_id,started_by,status FROM consultation_video_calls WHERE id=? AND appointment_id=? FOR UPDATE")) {
            p.setLong(1, callId); p.setLong(2, appointmentId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Video call not found.");
                id = r.getLong("id"); foundAppointmentId = r.getLong("appointment_id"); startedBy = r.getLong("started_by"); status = r.getString("status");
            }
        }
        try (PreparedStatement a = c.prepareStatement("SELECT patient_id,doctor_id FROM appointments WHERE id=?")) {
            a.setLong(1, appointmentId); try (ResultSet appt = a.executeQuery()) { if (!appt.next()) throw new IllegalArgumentException("Appointment not found."); patientId = appt.getLong("patient_id"); doctorId = appt.getLong("doctor_id"); }
        }
        return new CallAccess(id, foundAppointmentId, startedBy, status, patientId, doctorId);
    }

    private Map<String, Object> callMap(long id, long startedBy, String status) {
        return callMap(id, startedBy, status, false);
    }

    private Map<String, Object> callMap(long id, long startedBy, String status, boolean created) {
        Map<String, Object> call = new LinkedHashMap<>(); call.put("id", id); call.put("startedBy", startedBy); call.put("status", status); call.put("created", created); return call;
    }

    private void audit(Connection c, long actor, String action, String detail) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,?,?)")) { p.setLong(1, actor); p.setString(2, action); p.setString(3, detail); p.executeUpdate(); }
    }

    private record AppointmentAccess(long id, long patientId, long doctorId, String status) { }
    private record CallAccess(long id, long appointmentId, long startedBy, String status, long patientId, long doctorId) { }
}
