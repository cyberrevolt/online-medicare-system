package com.onlinemedic.dao;
import com.onlinemedic.model.Appointment;
import com.onlinemedic.util.Database;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

public class AppointmentDao {
    public long book(long patientId,long doctorId,LocalDate date,LocalTime time,String type)throws SQLException{
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try(PreparedStatement slot=c.prepareStatement("SELECT d.consultation_fee FROM doctor_availability a JOIN doctor_profiles d ON d.user_id=a.doctor_id JOIN users u ON u.id=d.user_id AND u.role='DOCTOR' AND u.active=TRUE WHERE a.doctor_id=? AND a.available_date=? AND a.start_time=? AND (a.available_date>CURRENT_DATE OR (a.available_date=CURRENT_DATE AND a.start_time>CURRENT_TIME)) AND NOT EXISTS(SELECT 1 FROM appointments b WHERE b.doctor_id=a.doctor_id AND b.appointment_date=a.available_date AND b.start_time=a.start_time AND b.status NOT IN ('CANCELLED','REJECTED')) FOR UPDATE")){
                slot.setLong(1,doctorId);slot.setObject(2,date);slot.setObject(3,time);try(ResultSet r=slot.executeQuery()){if(!r.next())throw new IllegalArgumentException("That appointment time is no longer available. Choose another slot.");var fee=r.getBigDecimal(1);
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO appointments(patient_id,doctor_id,appointment_date,start_time,consultation_type,fee,status,payment_status) VALUES(?,?,?,?,?,?,'PENDING','PENDING')",Statement.RETURN_GENERATED_KEYS)){
                        p.setLong(1,patientId);p.setLong(2,doctorId);p.setObject(3,date);p.setObject(4,time);p.setString(5,type);p.setBigDecimal(6,fee);p.executeUpdate();try(ResultSet key=p.getGeneratedKeys()){key.next();long id=key.getLong(1);try(PreparedStatement audit=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'APPOINTMENT BOOKED',?)")){audit.setLong(1,patientId);audit.setString(2,"Appointment #"+id+" created pending local demo payment.");audit.executeUpdate();}c.commit();return id;}
                    }
                }
            }catch(Exception e){c.rollback();if(e instanceof SQLException sql){if(sql.getSQLState()!=null&&sql.getSQLState().startsWith("23"))throw new IllegalArgumentException("That appointment time is no longer available. Choose another slot.");throw sql;}if(e instanceof IllegalArgumentException bad)throw bad;throw new SQLException("Could not book the appointment",e);}
        }
    }
    public boolean pay(long appointmentId,long userId,boolean success,String method,String transactionId)throws SQLException{
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{
            String select="SELECT fee,patient_id,status FROM appointments WHERE id=? FOR UPDATE";java.math.BigDecimal amount;long patient;
            try(PreparedStatement p=c.prepareStatement(select)){p.setLong(1,appointmentId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Appointment not found.");amount=r.getBigDecimal(1);patient=r.getLong(2);if(patient!=userId)throw new SecurityException("Not allowed.");if(!"PENDING".equals(r.getString(3)))throw new IllegalArgumentException("This appointment has already been processed.");}}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO payments(user_id,appointment_id,amount,payment_method,transaction_id,status) VALUES(?,?,?,?,?,?)")){p.setLong(1,userId);p.setLong(2,appointmentId);p.setBigDecimal(3,amount);p.setString(4,method);p.setString(5,transactionId);p.setString(6,success?"SUCCESS":"FAILED");p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("UPDATE appointments SET status=?,payment_status=? WHERE id=?")){p.setString(1,success?"CONFIRMED":"CANCELLED");p.setString(2,success?"SUCCESS":"FAILED");p.setLong(3,appointmentId);p.executeUpdate();}
            if(success){long doctor;try(PreparedStatement p=c.prepareStatement("SELECT doctor_id FROM appointments WHERE id=?")){p.setLong(1,appointmentId);try(ResultSet r=p.executeQuery()){r.next();doctor=r.getLong(1);}}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO conversations(appointment_id,patient_id,doctor_id) VALUES(?,?,?)")){p.setLong(1,appointmentId);p.setLong(2,userId);p.setLong(3,doctor);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO consultations(appointment_id,status) VALUES(?,'SCHEDULED')")){p.setLong(1,appointmentId);p.executeUpdate();}
                notify(c,doctor,"Appointment confirmed","A patient booked appointment #"+appointmentId+".");notify(c,userId,"Appointment confirmed","Your appointment #"+appointmentId+" has been confirmed.");
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'APPOINTMENT PAYMENT',?)")){p.setLong(1,userId);p.setString(2,"Appointment #"+appointmentId+" payment outcome: "+(success?"SUCCESS":"FAILED"));p.executeUpdate();}
            c.commit();return success;
        }catch(Exception e){c.rollback();if(e instanceof SQLException x)throw x;if(e instanceof RuntimeException x)throw x;throw new SQLException(e);}}
    }
    public List<Appointment> forPatient(long id)throws SQLException{return list("a.patient_id=?",id);}
    public List<Appointment> forDoctor(long id)throws SQLException{return list("a.doctor_id=?",id);}
    public boolean isPendingPaymentForPatient(long appointmentId,long patientId)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT 1 FROM appointments WHERE id=? AND patient_id=? AND status='PENDING' AND payment_status='PENDING'")){p.setLong(1,appointmentId);p.setLong(2,patientId);try(ResultSet r=p.executeQuery()){return r.next();}}}
    private List<Appointment> list(String where,long id)throws SQLException{String sql="SELECT a.id,u.full_name,a.appointment_date,a.start_time,a.consultation_type,a.fee,a.status,a.payment_status,(a.appointment_date>CURRENT_DATE) can_cancel,EXISTS(SELECT 1 FROM reviews rv WHERE rv.appointment_id=a.id) reviewed FROM appointments a JOIN users u ON u.id="+(where.contains("patient")?"a.doctor_id":"a.patient_id")+" WHERE "+where+" ORDER BY a.appointment_date DESC,a.start_time DESC";try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setLong(1,id);try(ResultSet r=p.executeQuery()){List<Appointment> out=new ArrayList<>();while(r.next())out.add(new Appointment(r.getLong(1),r.getString(2),r.getObject(3,LocalDate.class),r.getObject(4,LocalTime.class),r.getString(5),r.getBigDecimal(6),r.getString(7),r.getString(8),r.getBoolean(9),r.getBoolean(10)));return out;}}}
    public void cancel(long appointmentId,long patientId)throws SQLException{
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long doctor;BigDecimal fee;LocalDate date;String status;
            try(PreparedStatement p=c.prepareStatement("SELECT doctor_id,fee,appointment_date,status FROM appointments WHERE id=? AND patient_id=? FOR UPDATE")){p.setLong(1,appointmentId);p.setLong(2,patientId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Appointment not found.");doctor=r.getLong(1);fee=r.getBigDecimal(2);date=r.getObject(3,LocalDate.class);status=r.getString(4);}}
            if(!"CONFIRMED".equals(status)||!date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Only future confirmed appointments can be cancelled.");
            try(PreparedStatement p=c.prepareStatement("UPDATE appointments SET status='CANCELLED',payment_status='REFUNDED' WHERE id=?")){p.setLong(1,appointmentId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("UPDATE consultations SET status='CANCELLED',ended_at=CURRENT_TIMESTAMP WHERE appointment_id=?")){p.setLong(1,appointmentId);p.executeUpdate();}
            closeVideoCalls(c,appointmentId);
            try(PreparedStatement p=c.prepareStatement("UPDATE payments SET status='REFUNDED',updated_at=CURRENT_TIMESTAMP WHERE appointment_id=? AND status='SUCCESS'")){p.setLong(1,appointmentId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO refunds(payment_id,amount,status,reason) SELECT id,?,'PROCESSED','Local demo cancellation; simulated refund' FROM payments WHERE appointment_id=? AND status='REFUNDED'")){p.setBigDecimal(1,fee);p.setLong(2,appointmentId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")){p.setLong(1,doctor);p.setString(2,"Appointment cancelled");p.setString(3,"Appointment #"+appointmentId+" was cancelled.");p.executeUpdate();p.setLong(1,patientId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'APPOINTMENT CANCELLED',?)")){p.setLong(1,patientId);p.setString(2,"Appointment #"+appointmentId+" cancelled; local demo refund recorded.");p.executeUpdate();}
            c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void updateStatus(long appointmentId,long doctorId,String status)throws SQLException{
        if(!Set.of("COMPLETED","NO_SHOW").contains(status))throw new IllegalArgumentException("Only completed or no-show status can be selected for a confirmed visit.");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement("UPDATE appointments SET status=? WHERE id=? AND doctor_id=? AND status='CONFIRMED'")){p.setString(1,status);p.setLong(2,appointmentId);p.setLong(3,doctorId);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Appointment could not be updated.");}
                try(PreparedStatement p=c.prepareStatement("UPDATE consultations SET status=?,ended_at=CURRENT_TIMESTAMP WHERE appointment_id=?")){p.setString(1,"COMPLETED".equals(status)?"COMPLETED":"NO_SHOW");p.setLong(2,appointmentId);p.executeUpdate();}
                closeVideoCalls(c,appointmentId);
                try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'APPOINTMENT STATUS',?)")){p.setLong(1,doctorId);p.setString(2,"Appointment #"+appointmentId+" marked "+status);p.executeUpdate();}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    private void closeVideoCalls(Connection c,long appointmentId)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("UPDATE consultation_video_calls SET status='ENDED',ended_at=CURRENT_TIMESTAMP WHERE appointment_id=? AND status IN ('RINGING','ACTIVE')")){p.setLong(1,appointmentId);p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("DELETE FROM consultation_video_signals WHERE call_id IN (SELECT id FROM consultation_video_calls WHERE appointment_id=?)")){p.setLong(1,appointmentId);p.executeUpdate();}
    }
    private void notify(Connection c,long id,String title,String body)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")){p.setLong(1,id);p.setString(2,title);p.setString(3,body);p.executeUpdate();}}
}
