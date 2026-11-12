package com.onlinemedic.dao;
import com.onlinemedic.model.Medicine;
import com.onlinemedic.model.PharmacyProfile;
import com.onlinemedic.util.Database;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class MedicineDao {
    private static final String AVAILABLE_STOCK="(GREATEST(0,m.stock_quantity-COALESCE((SELECT SUM(b.quantity) FROM medicine_batches b WHERE b.medicine_id=m.id),0))+COALESCE((SELECT SUM(b.quantity) FROM medicine_batches b WHERE b.medicine_id=m.id AND b.expiry_date>=CURRENT_DATE),0))";
    private static final String SELECT="SELECT m.id,m.name,COALESCE(c.name,'General') category,COALESCE(f.name,'') manufacturer,COALESCE(m.description,'') description,m.price,m.prescription_required,"+AVAILABLE_STOCK+" stock_quantity,m.min_stock,m.image_url FROM medicines m LEFT JOIN categories c ON c.id=m.category_id LEFT JOIN manufacturers f ON f.id=m.manufacturer_id WHERE m.active=TRUE";
    public List<Medicine> search(String q,String category,String sort)throws SQLException{
        StringBuilder sql=new StringBuilder(SELECT);List<Object> args=new ArrayList<>();if(q!=null&&!q.isBlank()){sql.append(" AND (LOWER(m.name) LIKE ? OR LOWER(m.description) LIKE ?)");String v="%"+q.trim().toLowerCase(Locale.ROOT)+"%";args.add(v);args.add(v);}if(category!=null&&!category.isBlank()){sql.append(" AND c.name=?");args.add(category);}sql.append(" ORDER BY ");sql.append("price".equals(sort)?"m.price ASC":"m.name ASC");sql.append(" LIMIT 100");
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql.toString())){for(int i=0;i<args.size();i++)p.setObject(i+1,args.get(i));try(ResultSet r=p.executeQuery()){List<Medicine> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}}
    }
    public Optional<Medicine> find(long id)throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(SELECT+" AND m.id=?")){p.setLong(1,id);try(ResultSet r=p.executeQuery()){return r.next()?Optional.of(map(r)):Optional.empty();}}}
    public List<String> categories()throws SQLException{try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT name FROM categories ORDER BY name")){List<String> out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}}
    public List<String> manufacturers()throws SQLException{try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT name FROM manufacturers ORDER BY name")){List<String> out=new ArrayList<>();while(r.next())out.add(r.getString(1));return out;}}
    public Optional<PharmacyProfile> pharmacyProfile()throws SQLException{try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT pharmacy_name,profile_image FROM pharmacy_settings WHERE id=1")){if(r.next())return Optional.of(new PharmacyProfile(r.getString(1),r.getString(2)));}String sql="SELECT p.pharmacy_name,p.profile_image FROM pharmacist_profiles p JOIN users u ON u.id=p.user_id WHERE u.active=TRUE AND p.pharmacy_name IS NOT NULL ORDER BY p.user_id LIMIT 1";try(Connection c=Database.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){return r.next()?Optional.of(new PharmacyProfile(r.getString(1),r.getString(2))):Optional.empty();}}
    public void create(String name,String category,String manufacturer,String description,BigDecimal price,boolean rx,int stock,int minStock,long actor)throws SQLException{create(name,category,manufacturer,description,price,rx,stock,minStock,null,actor);}
    public void create(String name,String category,String manufacturer,String description,BigDecimal price,boolean rx,int stock,int minStock,String imageUrl,long actor)throws SQLException{
        name=bounded(name,3,180,"Medicine name must be 3–180 characters.");category=bounded(category,1,100,"Choose a category.");manufacturer=bounded(manufacturer,1,140,"Choose a manufacturer.");description=description==null?"":description.trim();if(description.length()>1200||price==null||price.signum()<0||stock<0||stock>100000||minStock<0||minStock>100000)throw new IllegalArgumentException("Check the price, description, and stock values.");
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long categoryId,manufacturerId;try(PreparedStatement p=c.prepareStatement("SELECT id FROM categories WHERE name=?")){p.setString(1,category);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose a category from the list.");categoryId=r.getLong(1);}}try(PreparedStatement p=c.prepareStatement("SELECT id FROM manufacturers WHERE name=?")){p.setString(1,manufacturer);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose a manufacturer from the list.");manufacturerId=r.getLong(1);}}
            long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO medicines(category_id,manufacturer_id,name,description,price,prescription_required,stock_quantity,min_stock,image_url) VALUES(?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,categoryId);p.setLong(2,manufacturerId);p.setString(3,name);p.setString(4,description);p.setBigDecimal(5,price);p.setBoolean(6,rx);p.setInt(7,stock);p.setInt(8,minStock);p.setString(9,imageUrl);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();id=k.getLong(1);}}
            if(stock>0)try(PreparedStatement p=c.prepareStatement("INSERT INTO inventory_transactions(medicine_id,quantity_change,reason) VALUES(?,?,'INITIAL STOCK')")){p.setLong(1,id);p.setInt(2,stock);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'MEDICINE CREATED',?)")){p.setLong(1,actor);p.setString(2,"Medicine "+name+" added to catalogue.");p.executeUpdate();}c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public void configureImported(long medicineId,BigDecimal price,String description,boolean rx,int stock,int minStock,long actor)throws SQLException{
        description=description==null?"":description.trim();
        if(price==null||price.signum()<=0||description.length()>1200||stock<0||stock>100000||minStock<0||minStock>100000)
            throw new IllegalArgumentException("Enter a verified price above zero, a valid stock quantity, and a description under 1,200 characters.");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                int changed;
                try(PreparedStatement p=c.prepareStatement("UPDATE medicines SET price=?,description=?,prescription_required=?,stock_quantity=?,min_stock=? WHERE id=? AND active=TRUE AND price<=0")){
                    p.setBigDecimal(1,price);p.setString(2,description);p.setBoolean(3,rx);p.setInt(4,stock);p.setInt(5,minStock);p.setLong(6,medicineId);changed=p.executeUpdate();
                }
                if(changed!=1)throw new IllegalArgumentException("This starter item is already configured or no longer available.");
                if(stock>0)try(PreparedStatement p=c.prepareStatement("INSERT INTO inventory_transactions(medicine_id,quantity_change,reason) VALUES(?,?,'PHARMACY CATALOG CONFIGURATION')")){p.setLong(1,medicineId);p.setInt(2,stock);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'MEDICINE CONFIGURED',?)")){p.setLong(1,actor);p.setString(2,"Starter medicine "+medicineId+" configured for sale.");p.executeUpdate();}
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    public void updateDetails(long medicineId,String name,String category,String manufacturer,String description,BigDecimal price,boolean rx,int minStock,String imageUrl,long actor)throws SQLException{
        name=bounded(name,3,180,"Medicine name must be 3–180 characters.");
        category=bounded(category,1,100,"Choose a category.");
        manufacturer=bounded(manufacturer,1,140,"Choose a manufacturer.");
        description=description==null?"":description.trim();
        if(description.length()>1200||price==null||price.signum()<=0||minStock<0||minStock>100000)
            throw new IllegalArgumentException("Enter a sale price above zero, a description under 1,200 characters, and a valid low-stock alert quantity.");
        try(Connection c=Database.getConnection()){
            c.setAutoCommit(false);
            try{
                long categoryId,manufacturerId;
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM categories WHERE name=?")){
                    p.setString(1,category);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose a category from the list.");categoryId=r.getLong(1);}
                }
                try(PreparedStatement p=c.prepareStatement("SELECT id FROM manufacturers WHERE name=?")){
                    p.setString(1,manufacturer);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose a manufacturer from the list.");manufacturerId=r.getLong(1);}
                }
                try(PreparedStatement p=c.prepareStatement("UPDATE medicines SET category_id=?,manufacturer_id=?,name=?,description=?,price=?,prescription_required=?,min_stock=?,image_url=COALESCE(?,image_url) WHERE id=? AND active=TRUE")){
                    p.setLong(1,categoryId);p.setLong(2,manufacturerId);p.setString(3,name);p.setString(4,description);p.setBigDecimal(5,price);p.setBoolean(6,rx);p.setInt(7,minStock);p.setString(8,imageUrl);p.setLong(9,medicineId);
                    if(p.executeUpdate()!=1)throw new IllegalArgumentException("This medicine is no longer available to edit.");
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'MEDICINE DETAILS UPDATED',?)")){
                    p.setLong(1,actor);p.setString(2,"Medicine "+medicineId+" details updated.");p.executeUpdate();
                }
                c.commit();
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }
    private String bounded(String value,int min,int max,String message){if(value==null||value.trim().length()<min||value.trim().length()>max)throw new IllegalArgumentException(message);return value.trim();}
    public List<Medicine> lowStock()throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(SELECT+" AND m.price>0 AND "+AVAILABLE_STOCK+"<=m.min_stock ORDER BY stock_quantity,m.name")){try(ResultSet r=p.executeQuery()){List<Medicine> out=new ArrayList<>();while(r.next())out.add(map(r));return out;}}}
    public List<Map<String,Object>> suppliers()throws SQLException{try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement("SELECT id,name,email,phone FROM suppliers WHERE active=TRUE ORDER BY name");ResultSet r=p.executeQuery()){List<Map<String,Object>> out=new ArrayList<>();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("id",r.getLong("id"));row.put("name",r.getString("name"));row.put("email",r.getString("email"));row.put("phone",r.getString("phone"));out.add(row);}return out;}}
    public long createSupplier(String name,String email,String phone,long actor)throws SQLException{
        name=bounded(name,2,160,"Supplier name must be 2–160 characters.");email=email==null?"":email.trim();phone=phone==null?"":phone.trim();
        if(email.length()>190||(!email.isBlank()&&!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))||phone.length()>30)throw new IllegalArgumentException("Enter a valid supplier email and phone number.");
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{long id;try(PreparedStatement p=c.prepareStatement("INSERT INTO suppliers(name,email,phone) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setString(1,name);p.setString(2,email.isBlank()?null:email);p.setString(3,phone.isBlank()?null:phone);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();id=k.getLong(1);}}
            audit(c,actor,"SUPPLIER CREATED","Supplier "+name+" added.");c.commit();return id;
        }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof SQLException x&&"23505".equals(x.getSQLState()))throw new IllegalArgumentException("A supplier with that name already exists.");throw e;}}
    }
    public void receiveBatch(long medicineId,long supplierId,String batchNumber,LocalDate manufacturingDate,LocalDate expiryDate,int quantity,BigDecimal unitCost,long actor)throws SQLException{
        batchNumber=bounded(batchNumber,2,90,"Enter a batch number (2–90 characters).");if(!batchNumber.matches("[A-Za-z0-9._/-]+"))throw new IllegalArgumentException("Batch numbers may contain letters, numbers, dots, underscores, slashes, and hyphens.");
        if(expiryDate==null||!expiryDate.isAfter(LocalDate.now()))throw new IllegalArgumentException("Only batches with a future expiry date can be received.");
        if(manufacturingDate!=null&&(manufacturingDate.isAfter(LocalDate.now())||manufacturingDate.isAfter(expiryDate)))throw new IllegalArgumentException("Check the manufacturing date.");
        if(quantity<1||quantity>50000||unitCost==null||unitCost.signum()<0||unitCost.compareTo(new BigDecimal("1000000"))>0)throw new IllegalArgumentException("Enter a quantity from 1 to 50,000 and a valid unit cost.");
        try(Connection c=Database.getConnection()){c.setAutoCommit(false);try{
            try(PreparedStatement p=c.prepareStatement("SELECT id FROM medicines WHERE id=? AND active=TRUE AND price>0 FOR UPDATE")){p.setLong(1,medicineId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose an active medicine with a configured sale price.");}}
            try(PreparedStatement p=c.prepareStatement("SELECT id FROM suppliers WHERE id=? AND active=TRUE")){p.setLong(1,supplierId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Choose an active supplier.");}}
            long batchId;try(PreparedStatement p=c.prepareStatement("INSERT INTO medicine_batches(medicine_id,batch_number,manufacturing_date,expiry_date,quantity,purchase_price) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){p.setLong(1,medicineId);p.setString(2,batchNumber);if(manufacturingDate==null)p.setNull(3,Types.DATE);else p.setObject(3,manufacturingDate);p.setObject(4,expiryDate);p.setInt(5,quantity);p.setBigDecimal(6,unitCost);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();batchId=k.getLong(1);}}
            try(PreparedStatement p=c.prepareStatement("UPDATE medicines SET stock_quantity=stock_quantity+? WHERE id=?")){p.setInt(1,quantity);p.setLong(2,medicineId);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO supplier_purchases(supplier_id,medicine_id,batch_id,quantity,unit_cost) VALUES(?,?,?,?,?)")){p.setLong(1,supplierId);p.setLong(2,medicineId);p.setLong(3,batchId);p.setInt(4,quantity);p.setBigDecimal(5,unitCost);p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO inventory_transactions(medicine_id,quantity_change,reason) VALUES(?,?,'SUPPLIER BATCH RECEIVED')")){p.setLong(1,medicineId);p.setInt(2,quantity);p.executeUpdate();}
            audit(c,actor,"SUPPLIER BATCH RECEIVED","Batch "+batchNumber+" received for medicine "+medicineId+", quantity "+quantity+", expiry "+expiryDate+".");c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();if(e instanceof SQLException x&&"23505".equals(x.getSQLState()))throw new IllegalArgumentException("That batch number already exists.");throw e;}}
    }
    public List<Map<String,Object>> expiryWatch(int days)throws SQLException{
        if(days<0||days>3650)throw new IllegalArgumentException("Expiry window is invalid.");String sql="SELECT b.id,b.batch_number,b.expiry_date,b.quantity,m.id medicine_id,m.name medicine_name,CASE WHEN b.expiry_date<CURRENT_DATE THEN TRUE ELSE FALSE END expired FROM medicine_batches b JOIN medicines m ON m.id=b.medicine_id WHERE b.quantity>0 AND b.expiry_date<=? ORDER BY b.expiry_date,m.name";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql)){p.setObject(1,LocalDate.now().plusDays(days));try(ResultSet r=p.executeQuery()){List<Map<String,Object>> out=new ArrayList<>();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("id",r.getLong("id"));row.put("batchNumber",r.getString("batch_number"));row.put("expiryDate",r.getDate("expiry_date").toLocalDate());row.put("quantity",r.getInt("quantity"));row.put("medicineId",r.getLong("medicine_id"));row.put("medicineName",r.getString("medicine_name"));row.put("expired",r.getBoolean("expired"));out.add(row);}return out;}}
    }
    public List<Map<String,Object>> batchInventory()throws SQLException{
        String sql="SELECT b.id,b.batch_number,b.manufacturing_date,b.expiry_date,b.quantity,b.purchase_price,m.name medicine_name,s.name supplier_name FROM medicine_batches b JOIN medicines m ON m.id=b.medicine_id LEFT JOIN supplier_purchases sp ON sp.batch_id=b.id LEFT JOIN suppliers s ON s.id=sp.supplier_id WHERE b.quantity>0 ORDER BY b.expiry_date,m.name";
        try(Connection c=Database.getConnection();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){List<Map<String,Object>> out=new ArrayList<>();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("id",r.getLong("id"));row.put("batchNumber",r.getString("batch_number"));row.put("manufacturingDate",r.getDate("manufacturing_date"));row.put("expiryDate",r.getDate("expiry_date"));row.put("quantity",r.getInt("quantity"));row.put("purchasePrice",r.getBigDecimal("purchase_price"));row.put("medicineName",r.getString("medicine_name"));row.put("supplierName",r.getString("supplier_name"));out.add(row);}return out;}
    }
    private void audit(Connection c,long actor,String action,String detail)throws SQLException{try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,?,?)")){p.setLong(1,actor);p.setString(2,action);p.setString(3,detail);p.executeUpdate();}}
    public void restock(long medicineId,int quantity,long actorId)throws SQLException{if(quantity<1||quantity>5000)throw new IllegalArgumentException("Choose a stock increase from 1 to 5,000.");try(Connection c=Database.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("UPDATE medicines SET stock_quantity=stock_quantity+? WHERE id=? AND price>0")){p.setInt(1,quantity);p.setLong(2,medicineId);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Set a verified sale price before restocking this medicine.");}try(PreparedStatement p=c.prepareStatement("INSERT INTO inventory_transactions(medicine_id,quantity_change,reason) VALUES(?,?,'PHARMACY STOCK ADJUSTMENT')")){p.setLong(1,medicineId);p.setInt(2,quantity);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT INTO audit_logs(user_id,action,detail) VALUES(?,'INVENTORY RESTOCK',?)")){p.setLong(1,actorId);p.setString(2,"Medicine "+medicineId+" stock increased by "+quantity);p.executeUpdate();}c.commit();}catch(SQLException|RuntimeException e){throw e;}}
    private Medicine map(ResultSet r)throws SQLException{return new Medicine(r.getLong("id"),r.getString("name"),r.getString("category"),r.getString("manufacturer"),r.getString("description"),r.getBigDecimal("price"),r.getBoolean("prescription_required"),r.getInt("stock_quantity"),r.getInt("min_stock"),r.getString("image_url"));}
}
