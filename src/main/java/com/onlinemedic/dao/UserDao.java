package com.onlinemedic.dao;
import com.onlinemedic.model.User;
import com.onlinemedic.util.Database;
import java.sql.*;
import java.util.*;
import java.util.Optional;

public class UserDao {
    public record AuthRecord(User user, String passwordHash) { }
    public Optional<AuthRecord> findForLogin(String email) throws SQLException {
        String sql="SELECT id,full_name,email,phone,role,password_hash FROM users WHERE LOWER(email)=LOWER(?) AND active=TRUE";
        try(Connection c=Database.getConnection(); PreparedStatement p=c.prepareStatement(sql)){p.setString(1,email);try(ResultSet r=p.executeQuery()){if(!r.next())return Optional.empty();return Optional.of(new AuthRecord(new User(r.getLong("id"),r.getString("full_name"),r.getString("email"),r.getString("phone"),r.getString("role")),r.getString("password_hash")));}}
    }
    public User registerPatient(String name,String email,String phone,String hash)throws SQLException{
        String sql="INSERT INTO users(full_name,email,phone,password_hash,role) VALUES(?,?,?,?,'PATIENT')";
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long id;try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,email);p.setString(3,phone);p.setString(4,hash);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}}try(PreparedStatement p=c.prepareStatement("INSERT INTO patient_profiles(user_id) VALUES(?)")){p.setLong(1,id);p.executeUpdate();}c.commit();return new User(id,name,email,phone,"PATIENT");}catch(SQLException e){c.rollback();throw e;}}
    }
    public long registerDoctor(long actorId,String name,String email,String phone,String hash,String specialization,String qualification,String registration,int experience,java.math.BigDecimal fee,String about,String clinic,String profileImage)throws SQLException{
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO users(full_name,email,phone,password_hash,role) VALUES(?,?,?,?,'DOCTOR')",Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,email);p.setString(3,phone);p.setString(4,hash);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO doctor_profiles(user_id,specialization,qualification,registration_number,experience_years,consultation_fee,about,clinic,profile_image) VALUES(?,?,?,?,?,?,?,?,?)")){p.setLong(1,id);p.setString(2,specialization);p.setString(3,qualification);p.setString(4,registration);p.setInt(5,experience);p.setBigDecimal(6,fee);p.setString(7,about);p.setString(8,clinic);p.setString(9,profileImage);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO doctor_specializations(doctor_id,specialization) VALUES(?,?)")){p.setLong(1,id);p.setString(2,specialization);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'DOCTOR CREATED',?)")){p.setLong(1,actorId);p.setString(2,"Doctor account "+email+" created.");p.executeUpdate();}
            c.commit();return id;
        }catch(SQLException e){c.rollback();throw e;}}
    }
    public String updateDoctorByAdmin(long actorId,long doctorId,String name,String email,String phone,boolean active,String newPasswordHash,String specialization,String qualification,String registration,int experience,java.math.BigDecimal fee,String about,String clinic,String newProfileImage)throws SQLException{
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                String oldImage;String oldAbout;
                try(PreparedStatement p=c.prepareStatement("SELECT dp.profile_image,dp.about FROM doctor_profiles dp JOIN users u ON u.id=dp.user_id WHERE dp.user_id=? AND u.role='DOCTOR' FOR UPDATE")){
                    p.setLong(1,doctorId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Doctor profile not found.");oldImage=r.getString("profile_image");oldAbout=r.getString("about");}
                }
                if(oldAbout!=null&&oldAbout.startsWith("DEMO_ONLY:")&&(about==null||!about.startsWith("DEMO_ONLY:")))about="DEMO_ONLY:"+(about==null?"":about);
                try(PreparedStatement p=c.prepareStatement("UPDATE users SET full_name=?,email=?,phone=?,active=?,password_hash=COALESCE(?,password_hash) WHERE id=? AND role='DOCTOR'")){
                    p.setString(1,name);p.setString(2,email);p.setString(3,phone);p.setBoolean(4,active);p.setString(5,newPasswordHash);p.setLong(6,doctorId);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE doctor_profiles SET specialization=?,qualification=?,registration_number=?,experience_years=?,consultation_fee=?,about=?,clinic=?,profile_image=COALESCE(?,profile_image) WHERE user_id=?")){
                    p.setString(1,specialization);p.setString(2,qualification);p.setString(3,registration);p.setInt(4,experience);p.setBigDecimal(5,fee);p.setString(6,about);p.setString(7,clinic);p.setString(8,newProfileImage);p.setLong(9,doctorId);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM doctor_specializations WHERE doctor_id=?")){p.setLong(1,doctorId);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO doctor_specializations(doctor_id,specialization) VALUES(?,?)")){
                    p.setLong(1,doctorId);p.setString(2,specialization);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'DOCTOR PROFILE UPDATED',?)")){
                    p.setLong(1,actorId);p.setString(2,"Administrator updated doctor profile #"+doctorId+".");p.executeUpdate();
                }
                c.commit();return oldImage;
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public long registerStaff(long actorId,String name,String email,String phone,String hash,String role,String credential,String location,String pharmacyImage)throws SQLException{
        if(!"PHARMACIST".equals(role)&&!"DELIVERY".equals(role))throw new IllegalArgumentException("Choose a supported staff role.");
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO users(full_name,email,phone,password_hash,role) VALUES(?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,email);p.setString(3,phone);p.setString(4,hash);p.setString(5,role);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();id=r.getLong(1);}}
            if("PHARMACIST".equals(role)){try(PreparedStatement p=c.prepareStatement("INSERT INTO pharmacist_profiles(user_id,license_number,pharmacy_name,profile_image) VALUES(?,?,?,?)")){p.setLong(1,id);p.setString(2,credential);p.setString(3,location);p.setString(4,pharmacyImage);p.executeUpdate();}}
            else try(PreparedStatement p=c.prepareStatement("INSERT INTO delivery_staff_profiles(user_id,vehicle_number) VALUES(?,?)")){p.setLong(1,id);p.setString(2,credential);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'STAFF CREATED',?)")){p.setLong(1,actorId);p.setString(2,role+" account "+email+" created.");p.executeUpdate();}c.commit();return id;
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public List<Map<String,Object>> adminStaffList()throws SQLException{
        String sql="SELECT u.id,u.full_name,u.email,u.phone,u.role,u.active,pp.license_number,pp.pharmacy_name,pp.profile_image,dp.vehicle_number FROM users u LEFT JOIN pharmacist_profiles pp ON pp.user_id=u.id LEFT JOIN delivery_staff_profiles dp ON dp.user_id=u.id WHERE u.role IN ('PHARMACIST','DELIVERY') ORDER BY u.role,u.active DESC,u.full_name";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            List<Map<String,Object>> staff=new ArrayList<>();while(r.next()){Map<String,Object> item=new LinkedHashMap<>();item.put("id",r.getLong("id"));item.put("name",r.getString("full_name"));item.put("email",r.getString("email"));item.put("phone",r.getString("phone"));item.put("role",r.getString("role"));item.put("active",r.getBoolean("active"));item.put("license",r.getString("license_number"));item.put("pharmacy",r.getString("pharmacy_name"));item.put("image",r.getString("profile_image"));item.put("vehicle",r.getString("vehicle_number"));item.put("credential","PHARMACIST".equals(r.getString("role"))?r.getString("license_number"):r.getString("vehicle_number"));staff.add(item);}return staff;
        }
    }
    public void updateStaffByAdmin(long actorId,long staffId,String role,String name,String email,String phone,boolean active,String newPasswordHash,String credential,String location)throws SQLException{
        if(!Set.of("PHARMACIST","DELIVERY").contains(role))throw new IllegalArgumentException("Unsupported staff role.");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM users WHERE id=? AND role=? FOR UPDATE")){p.setLong(1,staffId);p.setString(2,role);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Staff account not found.");}}
                if("DELIVERY".equals(role)&&!active)try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM deliveries WHERE delivery_staff_id=? AND status IN ('SHIPPED','OUT_FOR_DELIVERY')")){p.setLong(1,staffId);try(ResultSet r=p.executeQuery()){r.next();if(r.getInt(1)>0)throw new IllegalArgumentException("This delivery person has active orders. Reassign those orders before pausing the account.");}}
                try(PreparedStatement p=c.prepareStatement("UPDATE users SET full_name=?,email=?,phone=?,active=?,password_hash=COALESCE(?,password_hash) WHERE id=? AND role=?")){p.setString(1,name);p.setString(2,email);p.setString(3,phone);p.setBoolean(4,active);p.setString(5,newPasswordHash);p.setLong(6,staffId);p.setString(7,role);p.executeUpdate();}
                if("PHARMACIST".equals(role)){
                    int changed;try(PreparedStatement p=c.prepareStatement("UPDATE pharmacist_profiles SET license_number=?,pharmacy_name=? WHERE user_id=?")){p.setString(1,credential);p.setString(2,location);p.setLong(3,staffId);changed=p.executeUpdate();}
                    if(changed==0)try(PreparedStatement p=c.prepareStatement("INSERT INTO pharmacist_profiles(user_id,license_number,pharmacy_name) VALUES(?,?,?)")){p.setLong(1,staffId);p.setString(2,credential);p.setString(3,location);p.executeUpdate();}
                }else{
                    int changed;try(PreparedStatement p=c.prepareStatement("UPDATE delivery_staff_profiles SET vehicle_number=?,active=? WHERE user_id=?")){p.setString(1,credential);p.setBoolean(2,active);p.setLong(3,staffId);changed=p.executeUpdate();}
                    if(changed==0)try(PreparedStatement p=c.prepareStatement("INSERT INTO delivery_staff_profiles(user_id,vehicle_number,active) VALUES(?,?,?)")){p.setLong(1,staffId);p.setString(2,credential);p.setBoolean(3,active);p.executeUpdate();}
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'STAFF ACCOUNT UPDATED',?)")){p.setLong(1,actorId);p.setString(2,"Administrator updated "+role.toLowerCase(Locale.ROOT)+" account #"+staffId+" (active="+active+").");p.executeUpdate();}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public int countRole(String role)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM users WHERE role=?")){p.setString(1,role);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}}
    public int countAll()throws SQLException{try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM users")){r.next();return r.getInt(1);}}
}
