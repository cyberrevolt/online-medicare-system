package com.onlinemedic.controller;
import com.onlinemedic.dao.AppointmentDao;import com.onlinemedic.model.User;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;
@WebServlet("/doctor/appointments")
public class DoctorAppointmentsServlet extends HttpServlet{
 private final AppointmentDao dao=new AppointmentDao();
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse s)throws ServletException,IOException{User u=(User)q.getSession().getAttribute("user");try{q.setAttribute("appointments",dao.forDoctor(u.id()));q.getRequestDispatcher("/WEB-INF/views/doctor-appointments.jsp").forward(q,s);}catch(Exception e){throw new ServletException("Schedule is unavailable.",e);}}
 @Override protected void doPost(HttpServletRequest q,HttpServletResponse s)throws ServletException,IOException{User u=(User)q.getSession().getAttribute("user");try{dao.updateStatus(Long.parseLong(q.getParameter("appointmentId")),u.id(),q.getParameter("status"));q.getSession().setAttribute("flashSuccess","Appointment status updated.");s.sendRedirect(q.getContextPath()+"/doctor/appointments");}catch(IllegalArgumentException e){q.getSession().setAttribute("flashError",e.getMessage());s.sendRedirect(q.getContextPath()+"/doctor/appointments");}catch(Exception e){throw new ServletException("Appointment could not be updated.",e);}}
}
