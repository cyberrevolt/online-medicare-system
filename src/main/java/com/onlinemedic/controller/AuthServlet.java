package com.onlinemedic.controller;
import com.onlinemedic.model.User;
import com.onlinemedic.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebServlet(urlPatterns={"/login","/login/*","/register","/register/*","/logout"})
public class AuthServlet extends HttpServlet {
    private final AuthService auth=new AuthService();
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        String path=requestPath(req);
        if("/logout".equals(path)){res.sendRedirect(req.getContextPath()+"/");return;}
        if(req.getSession(false)!=null&&req.getSession(false).getAttribute("user")!=null){redirectDashboard(req,res);return;}
        if("/login".equals(path)){req.getRequestDispatcher("/WEB-INF/views/account-access.jsp").forward(req,res);return;}
        if(path.startsWith("/login/")){setLoginRole(req,path.substring("/login/".length()));req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req,res);return;}
        if("/register".equals(path)||"/register/patient".equals(path)){req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req,res);return;}
        if(path.startsWith("/register/")){String role=path.substring("/register/".length()).toUpperCase(java.util.Locale.ROOT);if(!java.util.Set.of("DOCTOR","PHARMACIST","ADMIN","DELIVERY").contains(role)){res.sendError(404);return;}req.setAttribute("signupRole",role);req.setAttribute("signupLoginPath",req.getContextPath()+"/login/"+role.toLowerCase(java.util.Locale.ROOT));req.getRequestDispatcher("/WEB-INF/views/role-signup.jsp").forward(req,res);return;}
        res.sendError(404);
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse res)throws ServletException,IOException{
        req.setCharacterEncoding("UTF-8");String path=requestPath(req);
        try{
            if("/register".equals(path)||"/register/patient".equals(path)){User user=auth.registerPatient(req.getParameter("name"),req.getParameter("email"),req.getParameter("phone"),req.getParameter("password"));req.getSession(true).setAttribute("user",user);req.getSession().setAttribute("flashSuccess","Welcome to Online Medic. Your account is ready.");String next=safeNext(req.getParameter("next"));if(next!=null)res.sendRedirect(req.getContextPath()+next);else redirectDashboard(req,res);return;}
            if(path.startsWith("/login")){String role=roleForPath(path);var user=auth.login(req.getParameter("email"),req.getParameter("password"));if(user.isEmpty()){req.setAttribute("error","Email or password was incorrect.");forwardLogin(req,res,role);return;}if(role!=null&&!role.equals(user.get().role())){req.setAttribute("error","This account is registered as a "+role.toLowerCase(java.util.Locale.ROOT)+". Choose the matching sign-in page.");forwardLogin(req,res,role);return;}HttpSession session=req.getSession(true);req.changeSessionId();session.setAttribute("user",user.get());session.setAttribute("flashSuccess","Welcome back, "+user.get().fullName()+".");String next=safeNext(req.getParameter("next"));if(next!=null)res.sendRedirect(req.getContextPath()+next);else redirectDashboard(req,res);return;}
            if("/logout".equals(path)){HttpSession session=req.getSession(false);if(session!=null)session.invalidate();res.sendRedirect(req.getContextPath()+"/?signedOut=1");return;}
        }catch(IllegalArgumentException e){req.setAttribute("error",e.getMessage());if(path.startsWith("/login"))forwardLogin(req,res,roleForPath(path));else req.getRequestDispatcher("/WEB-INF/views/register.jsp").forward(req,res);}catch(Exception e){throw new ServletException("Request could not be completed.",e);}
    }
    private void forwardLogin(HttpServletRequest req,HttpServletResponse res,String role)throws ServletException,IOException{if(role==null){res.sendRedirect(req.getContextPath()+"/login");return;}setLoginRole(req,role.toLowerCase(java.util.Locale.ROOT));req.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(req,res);}
    private String requestPath(HttpServletRequest req){String path=req.getServletPath();return path+(req.getPathInfo()==null?"":req.getPathInfo());}
    private String safeNext(String next){return next!=null&&next.startsWith("/")&&!next.startsWith("//")&&!next.contains("\\")&&!next.contains("\r")&&!next.contains("\n")?next:null;}
    private void setLoginRole(HttpServletRequest req,String role){String normalized=role.toUpperCase(java.util.Locale.ROOT);if(!java.util.Set.of("PATIENT","DOCTOR","PHARMACIST","DELIVERY","ADMIN").contains(normalized))return;req.setAttribute("loginRole",normalized);req.setAttribute("loginPath",req.getContextPath()+"/login/"+normalized.toLowerCase(java.util.Locale.ROOT));}
    private String roleForPath(String path){if(!path.startsWith("/login/"))return null;String role=path.substring("/login/".length()).toUpperCase(java.util.Locale.ROOT);return java.util.Set.of("PATIENT","DOCTOR","PHARMACIST","DELIVERY","ADMIN").contains(role)?role:null;}
    private void redirectDashboard(HttpServletRequest req,HttpServletResponse res)throws IOException{res.sendRedirect(req.getContextPath()+"/dashboard");}
}
