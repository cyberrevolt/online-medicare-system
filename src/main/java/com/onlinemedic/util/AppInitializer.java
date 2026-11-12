package com.onlinemedic.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Enumeration;

@WebListener
public class AppInitializer implements ServletContextListener {
    private static final String DEMO_DOCTOR_PASSWORD = "DoctorDemo123!";

    @Override public void contextInitialized(ServletContextEvent event) {
        try (Connection connection = Database.getConnection()) {
            runSchema(connection);
            seedIfEmpty(connection);
            runSqlResource(connection, "/db/catalog_seed.sql");
            seedDemoDoctors(connection);
            activateAppointmentRooms(connection);
            event.getServletContext().setAttribute("databaseReady", Boolean.TRUE);
            event.getServletContext().log("Online Medic database is ready (" + (Database.isPostgres() ? "PostgreSQL" : "local H2 demo mode") + ").");
        } catch (Exception e) {
            event.getServletContext().setAttribute("databaseReady", Boolean.FALSE);
            event.getServletContext().setAttribute("startupMessage", "Database setup failed. Check the database configuration and server log.");
            event.getServletContext().log("Online Medic database initialization failed", e);
        }
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        ClassLoader webAppLoader = getClass().getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == webAppLoader) {
                try { DriverManager.deregisterDriver(driver); }
                catch (java.sql.SQLException e) { event.getServletContext().log("Could not clean up the application JDBC driver.", e); }
            }
        }
    }

    private void runSchema(Connection connection) throws Exception {
        runSqlResource(connection, "/db/schema.sql");
    }

    /** Adds image-backed directory samples once; bookings and conversations stay clearly in demo mode. */
    private void seedDemoDoctors(Connection c) throws Exception {
        String[][] profiles = {
            {"demo-aarav", "Dr. Aarav Malhotra", "showcase.aarav@onlinemedic.local", "Cardiology", "/assets/doctors/demo-doctor-01.jpg"},
            {"demo-kavya", "Dr. Kavya Iyer", "showcase.kavya@onlinemedic.local", "Pediatrics", "/assets/doctors/demo-doctor-02.jpg"},
            {"demo-anika", "Dr. Anika Sen", "showcase.anika@onlinemedic.local", "Dermatology", "/assets/doctors/demo-doctor-03.jpg"},
            {"demo-meera-kapoor", "Dr. Meera Kapoor", "showcase.meera@onlinemedic.local", "Ophthalmology", "/assets/doctors/demo-doctor-04.jpg"},
            {"demo-nisha", "Dr. Nisha Rao", "showcase.nisha@onlinemedic.local", "General Medicine", "/assets/doctors/demo-doctor-05.jpg"}
        };
        String about = "DEMO_ONLY:Fictional sample profile for a software walkthrough only. This is not a real licensed healthcare provider; bookings and chat are simulated and do not provide medical care.";
        for (String[] profile : profiles) {
            long id = findDoctorByDemoKey(c, profile[0]);
            if (id == 0) {
                try (PreparedStatement p = c.prepareStatement("SELECT id,role FROM users WHERE LOWER(email)=LOWER(?)")) {
                    p.setString(1, profile[2]);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next()) { if (!"DOCTOR".equals(r.getString("role"))) continue; id = r.getLong("id"); }
                    }
                }
            }
            boolean hasProfile = false; String currentKey = null;
            if (id != 0) try (PreparedStatement p = c.prepareStatement("SELECT demo_profile_key FROM doctor_profiles WHERE user_id=?")) {
                p.setLong(1, id); try (ResultSet r = p.executeQuery()) { if (r.next()) { hasProfile = true; currentKey = r.getString(1); } }
            }
            if (id == 0) {
                try (PreparedStatement insert = c.prepareStatement("INSERT INTO users(full_name,email,password_hash,role) VALUES(?,?,?,'DOCTOR')", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, profile[1]); insert.setString(2, profile[2]); insert.setString(3, PasswordUtil.hash(DEMO_DOCTOR_PASSWORD)); insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) { if (!keys.next()) throw new IllegalStateException("No demo doctor identifier returned."); id = keys.getLong(1); }
                }
            }
            if (!hasProfile) {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO doctor_profiles(user_id,specialization,qualification,registration_number,experience_years,consultation_fee,about,clinic,rating,consultation_type,profile_image,demo_profile_key) VALUES(?,?,?,NULL,0,499,?, 'Online Medic demo clinic',0,'Secure chat',?,?)")) {
                    p.setLong(1, id); p.setString(2, profile[3]); p.setString(3, "Fictional directory sample"); p.setString(4, about); p.setString(5, profile[4]); p.setString(6, profile[0]); p.executeUpdate();
                }
            } else if (currentKey == null) {
                try (PreparedStatement p = c.prepareStatement("UPDATE doctor_profiles SET demo_profile_key=?,profile_image=COALESCE(profile_image,?) WHERE user_id=? AND demo_profile_key IS NULL")) {
                    p.setString(1, profile[0]); p.setString(2, profile[4]); p.setLong(3, id); p.executeUpdate();
                }
                // One-time upgrade of demo accounts created by the prior directory seeder.
                try (PreparedStatement p = c.prepareStatement("UPDATE users SET password_hash=? WHERE id=? AND role='DOCTOR'")) {
                    p.setString(1, PasswordUtil.hash(DEMO_DOCTOR_PASSWORD)); p.setLong(2, id); p.executeUpdate();
                }
            } else {
                try (PreparedStatement p = c.prepareStatement("UPDATE doctor_profiles SET profile_image=? WHERE user_id=? AND (profile_image IS NULL OR profile_image='')")) {
                    p.setString(1, profile[4]); p.setLong(2, id); p.executeUpdate();
                }
            }
            // Upgrade existing preview accounts from the old, disabled-booking copy once.
            try (PreparedStatement p = c.prepareStatement("UPDATE doctor_profiles SET about=?,consultation_fee=CASE WHEN consultation_fee=0 THEN 499 ELSE consultation_fee END,consultation_type=CASE WHEN consultation_type='Demo only' THEN 'Secure chat' ELSE consultation_type END,clinic=CASE WHEN clinic='Online Medic directory preview' THEN 'Online Medic demo clinic' ELSE clinic END WHERE user_id=? AND about LIKE 'DEMO_ONLY:%appointments are disabled.'")) {
                p.setString(1, about); p.setLong(2, id); p.executeUpdate();
            }
            if (!hasProfile || currentKey == null) try (PreparedStatement p = c.prepareStatement("INSERT INTO doctor_specializations(doctor_id,specialization) SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM doctor_specializations WHERE doctor_id=? AND specialization=?)")) {
                p.setLong(1, id); p.setString(2, profile[3]); p.setLong(3, id); p.setString(4, profile[3]); p.executeUpdate();
            }
            upgradeLegacyDemoDoctorPassword(c, id);
            seedDemoAvailability(c, id);
        }
        ensureBuiltInDemoDoctorCredentials(c, "meera@onlinemedic.local", "demo-seed-meera-iyer");
        ensureBuiltInDemoDoctorCredentials(c, "kabir@onlinemedic.local", "demo-seed-kabir-shah");
        ensureBuiltInDemoDoctorCredentials(c, "ananya@onlinemedic.local", "demo-seed-ananya-rao");
    }

    /** Keeps two future chat appointment times per day available for the demo profiles. */
    private void seedDemoAvailability(Connection c, long doctorId) throws Exception {
        LocalTime[][] times = {{LocalTime.of(10, 0), LocalTime.of(10, 30)}, {LocalTime.of(14, 0), LocalTime.of(14, 30)}};
        for (int day = 1; day <= 14; day++) {
            LocalDate date = LocalDate.now().plusDays(day);
            for (LocalTime[] time : times) {
                try (PreparedStatement exists = c.prepareStatement("SELECT 1 FROM doctor_availability WHERE doctor_id=? AND available_date=? AND start_time=?")) {
                    exists.setLong(1, doctorId); exists.setObject(2, date); exists.setObject(3, time[0]);
                    try (ResultSet r = exists.executeQuery()) { if (r.next()) continue; }
                }
                try (PreparedStatement insert = c.prepareStatement("INSERT INTO doctor_availability(doctor_id,available_date,start_time,end_time) VALUES(?,?,?,?)")) {
                    insert.setLong(1, doctorId); insert.setObject(2, date); insert.setObject(3, time[0]); insert.setObject(4, time[1]); insert.executeUpdate();
                }
            }
        }
    }

    /** Backfills private chat rooms for older paid appointments created before the chat UI was active. */
    private void activateAppointmentRooms(Connection c) throws Exception {
        try (Statement statement = c.createStatement()) {
            statement.executeUpdate("INSERT INTO conversations(appointment_id,patient_id,doctor_id) SELECT a.id,a.patient_id,a.doctor_id FROM appointments a WHERE a.status IN ('CONFIRMED','COMPLETED') AND NOT EXISTS(SELECT 1 FROM conversations x WHERE x.appointment_id=a.id)");
            statement.executeUpdate("INSERT INTO consultations(appointment_id,status) SELECT a.id,'SCHEDULED' FROM appointments a WHERE a.status IN ('CONFIRMED','COMPLETED') AND NOT EXISTS(SELECT 1 FROM consultations x WHERE x.appointment_id=a.id)");
        }
    }

    private long findDoctorByDemoKey(Connection c, String key) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT user_id FROM doctor_profiles WHERE demo_profile_key=?")) {
            p.setString(1, key); try (ResultSet r = p.executeQuery()) { return r.next() ? r.getLong(1) : 0; }
        }
    }

    /** Initializes the legacy demo doctor password once; later administrator resets are preserved. */
    private void ensureBuiltInDemoDoctorCredentials(Connection c, String email, String key) throws Exception {
        long id = findDoctorByDemoKey(c, key);
        if (id == 0) {
            try (PreparedStatement p = c.prepareStatement("SELECT id FROM users WHERE email=? AND role='DOCTOR'")) {
                p.setString(1, email); try (ResultSet r = p.executeQuery()) { if (!r.next()) return; id = r.getLong(1); }
            }
        }
        String profileKey;
        try (PreparedStatement p = c.prepareStatement("SELECT demo_profile_key FROM doctor_profiles WHERE user_id=?")) {
            p.setLong(1, id); try (ResultSet r = p.executeQuery()) { if (!r.next()) return; profileKey = r.getString(1); }
        }
        if (profileKey == null) {
            try (PreparedStatement p = c.prepareStatement("UPDATE doctor_profiles SET demo_profile_key=? WHERE user_id=? AND demo_profile_key IS NULL")) { p.setString(1, key); p.setLong(2, id); p.executeUpdate(); }
        }
        upgradeLegacyDemoDoctorPassword(c, id);
    }

    /** Migrates only the old shared demo password, preserving passwords reset by an administrator. */
    private void upgradeLegacyDemoDoctorPassword(Connection c, long id) throws Exception {
        String hash;
        try (PreparedStatement p = c.prepareStatement("SELECT password_hash FROM users WHERE id=? AND role='DOCTOR'")) {
            p.setLong(1, id); try (ResultSet r = p.executeQuery()) { if (!r.next()) return; hash = r.getString(1); }
        }
        if ("Demo123!".equals(hash) || PasswordUtil.verify("Demo123!", hash)) {
            try (PreparedStatement p = c.prepareStatement("UPDATE users SET password_hash=? WHERE id=? AND role='DOCTOR'")) {
                p.setString(1, PasswordUtil.hash(DEMO_DOCTOR_PASSWORD)); p.setLong(2, id); p.executeUpdate();
            }
        }
    }

    private void runSqlResource(Connection connection, String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException(resource + " is missing");
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement statement = connection.createStatement()) {
                for (String part : sql.split(";")) if (!part.isBlank()) statement.execute(part.trim());
            }
        }
    }

    private void seedIfEmpty(Connection c) throws Exception {
        String[][] roles={{"PATIENT","Patient account"},{"DOCTOR","Licensed healthcare professional"},{"PHARMACIST","Pharmacy team member"},{"DELIVERY","Delivery staff member"},{"ADMIN","System administrator"}};
        for(String[] role:roles){boolean exists;try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM roles WHERE role_name=?")){p.setString(1,role[0]);try(ResultSet r=p.executeQuery()){exists=r.next();}}if(!exists)try(PreparedStatement p=c.prepareStatement("INSERT INTO roles(role_name,description) VALUES(?,?)")){p.setString(1,role[0]);p.setString(2,role[1]);p.executeUpdate();}}
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) { rs.next(); if (rs.getLong(1) > 0) return; }
        if(Database.isPostgres()){bootstrapAdminIfConfigured(c);return;}
        long admin = insertUser(c, "Online Medic Admin", "admin@onlinemedic.local", "+91 90000 00001", "ADMIN");
        long patient = insertUser(c, "Aarav Demo", "patient@onlinemedic.local", "+91 90000 00002", "PATIENT");
        long pharmacist = insertUser(c, "Pharmacy Demo", "pharmacy@onlinemedic.local", "+91 90000 00003", "PHARMACIST");
        long delivery = insertUser(c, "Delivery Demo", "delivery@onlinemedic.local", "+91 90000 00004", "DELIVERY");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO patient_profiles(user_id) VALUES(?)")){p.setLong(1,patient);p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO pharmacist_profiles(user_id,pharmacy_name) VALUES(?,?)")){p.setLong(1,pharmacist);p.setString(2,"Online Medic Pharmacy");p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO delivery_staff_profiles(user_id) VALUES(?)")){p.setLong(1,delivery);p.executeUpdate();}
        String[][] doctors = {
            {"Dr. Meera Iyer", "meera@onlinemedic.local", "Cardiology", "MBBS, MD (Cardiology)", "12", "800", "Video"},
            {"Dr. Kabir Shah", "kabir@onlinemedic.local", "Dermatology", "MBBS, DDVL", "9", "650", "Video"},
            {"Dr. Ananya Rao", "ananya@onlinemedic.local", "General Medicine", "MBBS, MD", "15", "500", "Video / In person"}
        };
        for (String[] d : doctors) {
            long id = insertUser(c, d[0], d[1], "+91 90000 00010", "DOCTOR");
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO doctor_profiles(user_id,specialization,qualification,experience_years,consultation_fee,about,clinic,rating,consultation_type) VALUES(?,?,?,?,?,?,?,?,?)")) {
                ps.setLong(1,id); ps.setString(2,d[2]); ps.setString(3,d[3]); ps.setInt(4,Integer.parseInt(d[4])); ps.setBigDecimal(5,new java.math.BigDecimal(d[5]));
                ps.setString(6,"Patient-focused care with clear explanations and practical next steps."); ps.setString(7,"Online Medic Care Centre"); ps.setBigDecimal(8,new java.math.BigDecimal("4.8")); ps.setString(9,d[6]); ps.executeUpdate();
            }
            try(PreparedStatement ps=c.prepareStatement("INSERT INTO doctor_specializations(doctor_id,specialization) VALUES(?,?)")){ps.setLong(1,id);ps.setString(2,d[2]);ps.executeUpdate();}
            for (int day=1; day<=7; day++) {
                LocalDate date = LocalDate.now().plusDays(day);
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO doctor_availability(doctor_id,available_date,start_time,end_time) VALUES(?,?,?,?)")) {
                    ps.setLong(1,id); ps.setObject(2,date); ps.setObject(3,LocalTime.of(10,0)); ps.setObject(4,LocalTime.of(10,30)); ps.executeUpdate();
                }
            }
        }
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO categories(name) VALUES(?),(?),(?)")) { ps.setString(1,"Wellness"); ps.setString(2,"Pain relief"); ps.setString(3,"First aid"); ps.executeUpdate(); }
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO manufacturers(name) VALUES(?),(?)")) { ps.setString(1,"CareWell Labs"); ps.setString(2,"Northstar Health"); ps.executeUpdate(); }
        addMedicine(c, 1, 1, "Vitamin C 500 mg", "Daily wellness supplement. Follow label directions.", "189.00", false, 80);
        addMedicine(c, 2, 2, "Paracetamol 500 mg", "For use as directed by a healthcare professional.", "45.00", false, 120);
        addMedicine(c, 3, 1, "Oral Rehydration Salts", "Electrolyte replacement sachets.", "32.00", false, 60);
        addMedicine(c, 1, 2, "Cetirizine 10 mg", "Antihistamine. Check the package leaflet before use.", "68.00", true, 35);
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO addresses(user_id,label,line1,city,state,postal_code,is_default) VALUES(?,?,?,?,?,?,TRUE)")) {
            ps.setLong(1,patient); ps.setString(2,"Home"); ps.setString(3,"12 Lake View Road"); ps.setString(4,"Bengaluru"); ps.setString(5,"Karnataka"); ps.setString(6,"560001"); ps.executeUpdate();
        }
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)")) {
            ps.setLong(1,admin); ps.setString(2,"Welcome to Online Medic"); ps.setString(3,"Your local demo workspace is ready."); ps.executeUpdate();
        }
    }

    private void bootstrapAdminIfConfigured(Connection c)throws Exception{
        String email=System.getenv("BOOTSTRAP_ADMIN_EMAIL"),password=System.getenv("BOOTSTRAP_ADMIN_PASSWORD");
        if(email==null||email.isBlank()||password==null||password.isBlank())return;
        if(password.length()<14||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$"))throw new IllegalArgumentException("The bootstrap admin email is invalid or the password is shorter than 14 characters.");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO users(full_name,email,password_hash,role) VALUES('System Administrator',?,?, 'ADMIN')")){p.setString(1,email.trim());p.setString(2,PasswordUtil.hash(password));p.executeUpdate();}
    }

    private long insertUser(Connection c, String name, String email, String phone, String role) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(full_name,email,phone,password_hash,role) VALUES(?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,name); ps.setString(2,email); ps.setString(3,phone); ps.setString(4,PasswordUtil.hash("Demo123!")); ps.setString(5,role); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) return keys.getLong(1); }
            throw new IllegalStateException("No user identifier returned");
        }
    }
    private void addMedicine(Connection c, int category, int manufacturer, String name, String description, String price, boolean rx, int stock) throws Exception {
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,stock_quantity,min_stock) VALUES(?,?,?,?,?,?,?,5)")) {
            ps.setInt(1,category); ps.setInt(2,manufacturer); ps.setString(3,name); ps.setString(4,description); ps.setBigDecimal(5,new java.math.BigDecimal(price)); ps.setBoolean(6,rx); ps.setInt(7,stock); ps.executeUpdate();
        }
    }
}
