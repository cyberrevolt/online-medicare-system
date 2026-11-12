package com.onlinemedic.service;
import com.onlinemedic.dao.UserDao;
import com.onlinemedic.model.User;
import com.onlinemedic.util.PasswordUtil;
import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

public class AuthService {
    private static final Pattern EMAIL=Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private final UserDao users=new UserDao();
    public Optional<User> login(String email,String password)throws SQLException{
        if(email==null||password==null)return Optional.empty();return users.findForLogin(email.trim()).filter(record->PasswordUtil.verify(password,record.passwordHash())).map(UserDao.AuthRecord::user);
    }
    public User registerPatient(String name,String email,String phone,String password)throws SQLException{
        name=clean(name,2,120,"Enter your name (2–120 characters).");email=clean(email,5,190,"Enter a valid email address.");
        if(!EMAIL.matcher(email).matches())throw new IllegalArgumentException("Enter a valid email address.");
        if(password==null||password.length()<10||password.length()>128||!password.matches(".*[A-Za-z].*")||!password.matches(".*\\d.*"))throw new IllegalArgumentException("Use at least 10 characters, including a letter and a number.");
        phone=phone==null?"":phone.trim();if(phone.length()>30)throw new IllegalArgumentException("Phone number is too long.");
        try{return users.registerPatient(name,email,phone,PasswordUtil.hash(password));}catch(SQLException e){if(e.getSQLState()!=null&&e.getSQLState().startsWith("23"))throw new IllegalArgumentException("An account with that email already exists.");throw e;}
    }
    private String clean(String v,int min,int max,String error){if(v==null)throw new IllegalArgumentException(error);String s=v.trim();if(s.length()<min||s.length()>max)throw new IllegalArgumentException(error);return s;}
}
