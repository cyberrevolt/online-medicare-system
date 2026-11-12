package com.onlinemedic.dao;
import com.onlinemedic.model.Doctor;
import com.onlinemedic.util.Database;
import java.sql.*;
import java.util.*;

public class DoctorDao {
    private static final String SELECT="SELECT u.id,u.full_name,d.specialization,d.qualification,d.experience_years,d.consultation_fee,d.about,d.clinic,d.rating,d.consultation_type,d.profile_image,CASE WHEN COALESCE(d.about,'') LIKE 'DEMO_ONLY:%' THEN TRUE ELSE FALSE END demo_profile FROM users u JOIN doctor_profiles d ON d.user_id=u.id WHERE u.active=TRUE";
    public List<Doctor> search(String query,String specialty,String sort)throws SQLException{
        StringBuilder sql=new StringBuilder(SELECT);List<Object> args=new ArrayList<>();
        if(query!=null&&!query.isBlank()){sql.append(" AND (LOWER(u.full_name) LIKE ? OR LOWER(d.specialization) LIKE ? OR LOWER(d.qualification) LIKE ?)");String q="%"+query.trim().toLowerCase(Locale.ROOT)+"%";args.add(q);args.add(q);args.add(q);}
        if(specialty!=null&&!specialty.isBlank()){sql.append(" AND LOWER(d.specialization)=LOWER(?)");args.add(specialty);}
        switch(sort==null?"":sort){case "fee"->sql.append(" ORDER BY d.consultation_fee ASC");case "experience"->sql.append(" ORDER BY d.experience_years DESC");default->sql.append(" ORDER BY d.rating DESC,u.full_name ASC");}
        sql.append(" LIMIT 60");try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql.toString())){for(int i=0;i<args.size();i++)p.setObject(i+1,args.get(i));try(ResultSet r=p.executeQuery()){List<Doctor> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}}
    }
    public List<Map<String,Object>> adminList()throws SQLException{
        String sql="SELECT u.id,u.full_name,u.email,COALESCE(u.phone,'') phone,u.active,d.specialization,d.qualification,COALESCE(d.registration_number,'') registration_number,d.experience_years,d.consultation_fee,d.about,d.clinic,d.rating,d.consultation_type,d.profile_image,CASE WHEN COALESCE(d.about,'') LIKE 'DEMO_ONLY:%' THEN TRUE ELSE FALSE END demo_profile FROM users u JOIN doctor_profiles d ON d.user_id=u.id WHERE u.role='DOCTOR' ORDER BY u.active DESC,u.full_name ASC";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            List<Map<String,Object>> out=new ArrayList<>();
            while(r.next()){
                boolean demoProfile=r.getBoolean("demo_profile");String about=r.getString("about");
                if(demoProfile&&about!=null&&about.startsWith("DEMO_ONLY:"))about=about.substring("DEMO_ONLY:".length());
                Map<String,Object> doctor=new LinkedHashMap<>();
                doctor.put("id",r.getLong("id"));doctor.put("name",r.getString("full_name"));doctor.put("email",r.getString("email"));doctor.put("phone",r.getString("phone"));doctor.put("active",r.getBoolean("active"));
                doctor.put("specialization",r.getString("specialization"));doctor.put("qualification",r.getString("qualification"));doctor.put("registration",r.getString("registration_number"));doctor.put("experience",r.getInt("experience_years"));
                doctor.put("fee",r.getBigDecimal("consultation_fee"));doctor.put("about",about);doctor.put("clinic",r.getString("clinic"));doctor.put("rating",r.getBigDecimal("rating"));doctor.put("consultationType",r.getString("consultation_type"));doctor.put("profileImage",r.getString("profile_image"));doctor.put("demoProfile",demoProfile);out.add(doctor);
            }
            return out;
        }
    }
    public Optional<Doctor> find(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(SELECT+" AND u.id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}}
    public List<String> specializations()throws SQLException{try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT DISTINCT specialization FROM doctor_profiles ORDER BY specialization")){List<String> out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}}
    public List<String> slots(long doctorId)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT CAST(a.available_date AS VARCHAR)||'|'||CAST(a.start_time AS VARCHAR) FROM doctor_availability a JOIN users u ON u.id=a.doctor_id AND u.role='DOCTOR' AND u.active=TRUE WHERE a.doctor_id=? AND (a.available_date>CURRENT_DATE OR (a.available_date=CURRENT_DATE AND a.start_time>CURRENT_TIME)) AND NOT EXISTS(SELECT 1 FROM appointments b WHERE b.doctor_id=a.doctor_id AND b.appointment_date=a.available_date AND b.start_time=a.start_time AND b.status NOT IN ('CANCELLED','REJECTED')) ORDER BY a.available_date,a.start_time")){p.setLong(1,doctorId);try(ResultSet r=p.executeQuery()){List<String> out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}}}
    private Doctor map(ResultSet r)throws SQLException{return new Doctor(r.getLong("id"),r.getString("full_name"),r.getString("specialization"),r.getString("qualification"),r.getInt("experience_years"),r.getBigDecimal("consultation_fee"),r.getString("about"),r.getString("clinic"),r.getBigDecimal("rating"),r.getString("consultation_type"),r.getString("profile_image"),r.getBoolean("demo_profile"));}
}
