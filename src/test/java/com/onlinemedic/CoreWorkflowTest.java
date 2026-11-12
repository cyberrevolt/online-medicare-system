package com.onlinemedic;

import com.onlinemedic.dao.AppointmentDao;
import com.onlinemedic.dao.MedicineDao;
import com.onlinemedic.dao.OrderDao;
import com.onlinemedic.service.CartTotals;
import com.onlinemedic.service.AdminService;
import com.onlinemedic.service.AuthService;
import com.onlinemedic.service.OrderService;
import com.onlinemedic.service.PaymentService;
import com.onlinemedic.service.VideoCallService;
import com.onlinemedic.util.Database;
import com.onlinemedic.util.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoreWorkflowTest {
    private static long patientId,doctorId,medicineId,pharmacistId,deliveryId,adminId;
    private static LocalDate slotDate;

    @BeforeAll static void database() throws Exception {
        System.setProperty("DATABASE_URL","jdbc:h2:mem:online_medic_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        System.setProperty("DATABASE_USER","sa");System.setProperty("DATABASE_PASSWORD","");
        try(Connection c=Database.getConnection();InputStream in=CoreWorkflowTest.class.getResourceAsStream("/db/schema.sql")){
            String schema=new String(in.readAllBytes(),StandardCharsets.UTF_8);try(Statement s=c.createStatement()){for(String sql:schema.split(";"))if(!sql.isBlank())s.execute(sql.trim());}
            try(Statement s=c.createStatement()){
                s.executeUpdate("INSERT INTO roles(role_name,description) VALUES('PATIENT','Patient'),('DOCTOR','Doctor'),('PHARMACIST','Pharmacist'),('DELIVERY','Delivery'),('ADMIN','Admin')");
                s.executeUpdate("INSERT INTO categories(name) VALUES('Wellness')");s.executeUpdate("INSERT INTO manufacturers(name) VALUES('Test Maker')");
            }
            patientId=addUser(c,"Test Patient","test.patient@local.test","PATIENT");doctorId=addUser(c,"Test Doctor","test.doctor@local.test","DOCTOR");pharmacistId=addUser(c,"Test Pharmacist","test.pharmacy@local.test","PHARMACIST");deliveryId=addUser(c,"Test Delivery","test.delivery@local.test","DELIVERY");adminId=addUser(c,"Test Admin","test.admin@local.test","ADMIN");
            try(PreparedStatement p=c.prepareStatement("UPDATE users SET phone='+91 90000 12345' WHERE id=?")){p.setLong(1,deliveryId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO doctor_profiles(user_id,specialization,qualification,experience_years,consultation_fee) VALUES(?,'General Medicine','MBBS',4,500)")){p.setLong(1,doctorId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO patient_profiles(user_id) VALUES(?)")){p.setLong(1,patientId);p.executeUpdate();}
            slotDate=LocalDate.now().plusDays(2);try(PreparedStatement p=c.prepareStatement("INSERT INTO doctor_availability(doctor_id,available_date,start_time,end_time) VALUES(?,?,?,'11:00:00')")){p.setLong(1,doctorId);p.setObject(2,slotDate);p.setObject(3,LocalTime.of(10,30));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO doctor_availability(doctor_id,available_date,start_time,end_time) VALUES(?,?,?,'11:30:00')")){p.setLong(1,doctorId);p.setObject(2,slotDate);p.setObject(3,LocalTime.of(11,0));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO medicines(category_id,manufacturer_id,name,price,stock_quantity,min_stock) VALUES(1,1,'Vitamin C',25.00,12,2)",Statement.RETURN_GENERATED_KEYS)){p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();medicineId=k.getLong(1);}}
        }
    }

    @Test void passwordHashesAndAppointmentReservationAndOrderStockAreSafe() throws Exception {
        String hash=PasswordUtil.hash("Str0ng-test-pass");assertNotEquals("Str0ng-test-pass",hash);assertTrue(PasswordUtil.verify("Str0ng-test-pass",hash));assertFalse(PasswordUtil.verify("wrong-pass",hash));assertTrue(new PaymentService().processDemoPayment("success").success());assertFalse(new PaymentService().processDemoPayment("failure").success());
        AppointmentDao appointments=new AppointmentDao();long first=appointments.book(patientId,doctorId,slotDate,LocalTime.of(10,30),"Video");
        assertFalse(appointments.pay(first,patientId,false,"DEMO_CARD","TEST-FAIL-1"));
        long retry=appointments.book(patientId,doctorId,slotDate,LocalTime.of(10,30),"Video");
        assertTrue(appointments.pay(retry,patientId,true,"DEMO_CARD","TEST-SUCCESS-1"));
        assertThrows(IllegalArgumentException.class,()->appointments.book(patientId,doctorId,slotDate,LocalTime.of(10,30),"Video"));
        Map<Long,Integer> cart=Map.of(medicineId,2);Map<Long,BigDecimal> prices=Map.of(medicineId,new BigDecimal("25.00"));assertEquals(new BigDecimal("50.00"),CartTotals.calculate(cart,prices));
        OrderService service=new OrderService();var failed=service.checkout(patientId,cart,"DEMO_CARD","failure","12 Test Road","Bengaluru","Karnataka","560001");assertFalse(failed.paymentSuccess());assertEquals(12,stock());
        assertThrows(IllegalArgumentException.class,()->service.checkout(patientId,Map.of(medicineId,20),"COD","success","12 Test Road","Bengaluru","Karnataka","560001"));assertEquals(12,stock());
        var successful=service.checkout(patientId,cart,"COD","success","12 Test Road","Bengaluru","Karnataka","560001");assertTrue(successful.paymentSuccess());assertTrue(successful.orderId()>0);assertEquals(10,stock());
        OrderDao orderDao=new OrderDao();orderDao.advance(successful.orderId(),pharmacistId,"PHARMACIST","PROCESSING");orderDao.advance(successful.orderId(),pharmacistId,"PHARMACIST","PACKED");orderDao.assignDeliveryStaff(successful.orderId(),deliveryId,pharmacistId,"PHARMACIST");assertEquals("OUT_FOR_DELIVERY",orderStatus(successful.orderId()));orderDao.advance(successful.orderId(),deliveryId,"DELIVERY","DELIVERED");
        assertEquals("DELIVERED",orderStatus(successful.orderId()));assertEquals("SUCCESS",paymentStatus(successful.orderId()));
        orderDao.requestReturnByPatient(successful.orderId(),patientId,"Package arrived damaged");orderDao.approveReturnAndSchedulePickup(successful.orderId(),deliveryId,LocalDateTime.now().plusDays(1),pharmacistId,"Eligible for return");orderDao.markReturnPickedUp(successful.orderId(),deliveryId);orderDao.markReturnReceived(successful.orderId(),pharmacistId,"Product received and verified");orderDao.initiateRefundByPharmacist(successful.orderId(),pharmacistId,"Verified return");long returnRefund=orderRefundId(successful.orderId());orderDao.reviewRefund(returnRefund,adminId,"APPROVED","Return verified");orderDao.processRefund(returnRefund,adminId,"Demo refund recorded");assertEquals("REFUNDED",paymentStatus(successful.orderId()));assertEquals("RETURNED",orderStatus(successful.orderId()));assertEquals("PROCESSED",orderRefundStatus(returnRefund));
        var paidCancellable=service.checkout(patientId,cart,"DEMO_CARD","success","12 Test Road","Bengaluru","Karnataka","560001");assertTrue(paidCancellable.paymentSuccess());orderDao.cancelByPatient(paidCancellable.orderId(),patientId);long cancelledRefund=orderRefundId(paidCancellable.orderId());assertEquals("PENDING",orderRefundStatus(cancelledRefund));orderDao.reviewRefund(cancelledRefund,adminId,"REJECTED","Confirm the cancellation details");orderDao.requestRefundByPatient(paidCancellable.orderId(),patientId,"Cancellation details confirmed");long resubmittedRefund=orderRefundId(paidCancellable.orderId());assertNotEquals(cancelledRefund,resubmittedRefund);orderDao.reviewRefund(resubmittedRefund,adminId,"APPROVED","Eligible");orderDao.processRefund(resubmittedRefund,adminId,"");assertEquals("REFUNDED",paymentStatus(paidCancellable.orderId()));assertEquals("PROCESSED",orderRefundStatus(resubmittedRefund));
        appointments.updateStatus(retry,doctorId,"COMPLETED");try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO prescriptions(appointment_id,patient_id,doctor_id,clinical_notes,status) VALUES(?,?,?,'Test care notes','ISSUED')")){p.setLong(1,retry);p.setLong(2,patientId);p.setLong(3,doctorId);p.executeUpdate();}
        long cancellable=appointments.book(patientId,doctorId,slotDate,LocalTime.of(11,0),"Video");assertTrue(appointments.pay(cancellable,patientId,true,"DEMO_CARD","TEST-CANCEL-1"));appointments.cancel(cancellable,patientId);assertEquals("REFUNDED",appointmentPaymentStatus(cancellable));assertEquals(1,refundCount(cancellable));long released=appointments.book(patientId,doctorId,slotDate,LocalTime.of(11,0),"Video");assertFalse(appointments.pay(released,patientId,false,"DEMO_CARD","TEST-RETRY-1"));
    }

    @Test void administratorCreatedDoctorCanSignInWithItsConfiguredPassword() throws Exception {
        String email="doctor.login."+UUID.randomUUID()+"@local.test";
        String password="ClinicDoctor987!";
        long created=new AdminService().createDoctor(adminId,"Dr Login QA",email,"+91 90000 54321",password,"General Medicine","MBBS","QA-"+UUID.randomUUID(),4,new BigDecimal("450.00"),"Test profile","QA Clinic");
        var authenticated=new AuthService().login("  "+email.toUpperCase(Locale.ROOT)+"  ",password);
        assertTrue(authenticated.isPresent(),"The password entered by the administrator must authenticate the created doctor.");
        assertEquals(created,authenticated.orElseThrow().id());
        assertEquals("DOCTOR",authenticated.orElseThrow().role());
    }

    @Test void supplierBatchesHideExpiredStockAndRestoreTheirBatchAllocation() throws Exception {
        long batchMedicineId;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO medicines(category_id,manufacturer_id,name,description,price,stock_quantity,min_stock) VALUES(1,1,'Batch Test Medicine','Inventory test fixture',10.00,0,1)",Statement.RETURN_GENERATED_KEYS)){
            p.executeUpdate();try(ResultSet keys=p.getGeneratedKeys()){assertTrue(keys.next());batchMedicineId=keys.getLong(1);}
        }
        MedicineDao medicines=new MedicineDao();long supplierId=medicines.createSupplier("Test Supplier "+UUID.randomUUID(),"supplier@local.test","+91 90000 11111",pharmacistId);
        LocalDate expiry=LocalDate.now().plusDays(15);
        medicines.receiveBatch(batchMedicineId,supplierId,"LOT-TEST-"+UUID.randomUUID().toString().substring(0,8),LocalDate.now().minusMonths(1),expiry,5,new BigDecimal("3.50"),pharmacistId);
        try(Connection c=Database.getConnection()){
            try(PreparedStatement p=c.prepareStatement("INSERT INTO medicine_batches(medicine_id,batch_number,expiry_date,quantity,purchase_price) VALUES(?,?,?,4,2.00)")){p.setLong(1,batchMedicineId);p.setString(2,"EXPIRED-"+UUID.randomUUID());p.setObject(3,LocalDate.now().minusDays(1));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("UPDATE medicines SET stock_quantity=stock_quantity+4 WHERE id=?")){p.setLong(1,batchMedicineId);p.executeUpdate();}
        }
        assertEquals(5,medicines.find(batchMedicineId).orElseThrow().stock());
        assertEquals(2,medicines.expiryWatch(30).stream().filter(batch->((Number)batch.get("quantity")).intValue()>0).count());
        long savedAddressId;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO addresses(user_id,label,line1,city,district,state,postal_code,is_default) VALUES(?,'Home','12 Test Road','Bengaluru','Bengaluru Urban','Karnataka','560001',TRUE)",Statement.RETURN_GENERATED_KEYS)){
            p.setLong(1,patientId);p.executeUpdate();try(ResultSet keys=p.getGeneratedKeys()){assertTrue(keys.next());savedAddressId=keys.getLong(1);}
        }
        OrderService orders=new OrderService();
        assertThrows(IllegalArgumentException.class,()->orders.checkout(patientId,Map.of(batchMedicineId,6),"COD","success","12 Test Road","Bengaluru","Karnataka","560001"));
        assertThrows(IllegalArgumentException.class,()->orders.checkout(patientId,Map.of(batchMedicineId,1),"COD","success",null,null,null,null,null,null,null,Long.MAX_VALUE));
        var placed=orders.checkout(patientId,Map.of(batchMedicineId,4),"DEMO_CARD","success",null,null,null,null,null,null,null,savedAddressId);assertTrue(placed.paymentSuccess());
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT address_id FROM orders WHERE id=?")){p.setLong(1,placed.orderId());try(ResultSet r=p.executeQuery()){assertTrue(r.next());assertEquals(savedAddressId,r.getLong(1));}}
        assertEquals(1,batchQuantity(batchMedicineId,expiry));assertEquals(5,stockOf(batchMedicineId));
        new OrderDao().cancelByPatient(placed.orderId(),patientId);
        assertEquals(5,batchQuantity(batchMedicineId,expiry));assertEquals(9,stockOf(batchMedicineId));
        assertEquals(5,medicines.find(batchMedicineId).orElseThrow().stock());
        assertTrue(medicines.batchInventory().stream().anyMatch(batch->batch.get("medicineName").equals("Batch Test Medicine")&&((Number)batch.get("quantity")).intValue()==5));
    }

    @Test void appointmentVideoCallsOnlyExchangeSignalsBetweenTheirTwoParticipants() throws Exception {
        long appointmentId;
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("INSERT INTO appointments(patient_id,doctor_id,appointment_date,start_time,consultation_type,fee,status,payment_status) VALUES(?,?,?,'09:00:00','Video',500,'CONFIRMED','SUCCESS')",Statement.RETURN_GENERATED_KEYS)){
            p.setLong(1,patientId);p.setLong(2,doctorId);p.setObject(3,LocalDate.now().plusDays(10));p.executeUpdate();try(ResultSet keys=p.getGeneratedKeys()){assertTrue(keys.next());appointmentId=keys.getLong(1);}
        }
        VideoCallService calls=new VideoCallService();Map<String,Object> started=calls.start(appointmentId,patientId);long callId=((Number)started.get("id")).longValue();assertTrue((Boolean)started.get("created"));assertEquals("RINGING",started.get("status"));
        Map<String,Object> sameCall=calls.start(appointmentId,doctorId);assertEquals(callId,((Number)sameCall.get("id")).longValue());assertFalse((Boolean)sameCall.get("created"));
        assertThrows(SecurityException.class,()->calls.poll(appointmentId,pharmacistId,0));
        calls.sendSignal(appointmentId,callId,patientId,"OFFER","{\"type\":\"offer\",\"sdp\":\"test-offer\"}");
        Map<String,Object> incoming=calls.poll(appointmentId,doctorId,0);assertEquals("RINGING",((Map<?,?>)incoming.get("call")).get("status"));assertEquals("OFFER",((Map<?,?>)((List<?>)incoming.get("signals")).get(0)).get("type"));
        assertThrows(SecurityException.class,()->calls.sendSignal(appointmentId,callId,pharmacistId,"ICE","{\"candidate\":\"not-authorized\"}"));
        calls.accept(appointmentId,callId,doctorId);calls.sendSignal(appointmentId,callId,doctorId,"ANSWER","{\"type\":\"answer\",\"sdp\":\"test-answer\"}");
        Map<String,Object> answer=calls.poll(appointmentId,patientId,0);assertEquals("ANSWER",((Map<?,?>)((List<?>)answer.get("signals")).get(0)).get("type"));
        calls.end(appointmentId,callId,doctorId);Map<String,Object> ended=calls.poll(appointmentId,patientId,0);assertEquals("ENDED",((Map<?,?>)ended.get("call")).get("status"));assertTrue(((List<?>)ended.get("signals")).isEmpty());
        Map<String,Object> finalCall=calls.start(appointmentId,patientId);long finalCallId=((Number)finalCall.get("id")).longValue();calls.sendSignal(appointmentId,finalCallId,patientId,"OFFER","{\"type\":\"offer\",\"sdp\":\"final-offer\"}");calls.accept(appointmentId,finalCallId,doctorId);calls.sendSignal(appointmentId,finalCallId,doctorId,"ANSWER","{\"type\":\"answer\",\"sdp\":\"final-answer\"}");new AppointmentDao().updateStatus(appointmentId,doctorId,"COMPLETED");
        Map<String,Object> completed=calls.poll(appointmentId,patientId,0);assertEquals("COMPLETED",completed.get("appointmentStatus"));assertEquals("ENDED",((Map<?,?>)completed.get("call")).get("status"));assertTrue(((List<?>)completed.get("signals")).isEmpty());assertThrows(IllegalArgumentException.class,()->calls.start(appointmentId,patientId));assertThrows(IllegalArgumentException.class,()->calls.accept(appointmentId,finalCallId,doctorId));assertThrows(IllegalArgumentException.class,()->calls.sendSignal(appointmentId,finalCallId,patientId,"ICE","{\"candidate\":\"late\"}"));
    }

    private static int stock()throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT stock_quantity FROM medicines WHERE id=?")){p.setLong(1,medicineId);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}}
    private static int stockOf(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT stock_quantity FROM medicines WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getInt(1);}}}
    private static int batchQuantity(long id,LocalDate expiry)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT quantity FROM medicine_batches WHERE medicine_id=? AND expiry_date=? AND batch_number LIKE 'LOT-TEST-%'")){p.setLong(1,id);p.setObject(2,expiry);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getInt(1);}}}
    private static String orderStatus(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT status FROM orders WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getString(1);}}}
    private static String paymentStatus(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT payment_status FROM orders WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getString(1);}}}
    private static String appointmentPaymentStatus(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT payment_status FROM appointments WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getString(1);}}}
    private static int refundCount(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.appointment_id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}}}
    private static long orderRefundId(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT r.id FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.order_id=? ORDER BY r.id DESC LIMIT 1")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}}}
    private static String orderRefundStatus(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT status FROM refunds WHERE id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getString(1);}}}
    private static long addUser(Connection c,String name,String email,String role)throws Exception{try(PreparedStatement p=c.prepareStatement("INSERT INTO users(full_name,email,password_hash,role) VALUES(?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,email);p.setString(3,PasswordUtil.hash("Test123456!"));p.setString(4,role);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();return k.getLong(1);}}}
}
