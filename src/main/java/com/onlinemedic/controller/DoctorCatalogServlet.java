package com.onlinemedic.controller;
import com.onlinemedic.dao.DoctorDao;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;
@WebServlet("/doctors")
public class DoctorCatalogServlet extends HttpServlet {
 private final DoctorDao dao=new DoctorDao();
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse s)throws ServletException,IOException{try{String id=q.getParameter("id");if(id!=null){var doctor=dao.find(Long.parseLong(id));if(doctor.isEmpty()){s.sendError(404);return;}q.setAttribute("doctor",doctor.get());q.setAttribute("slots",dao.slots(doctor.get().id()));q.getRequestDispatcher("/WEB-INF/views/doctor-detail.jsp").forward(q,s);return;}q.setAttribute("doctors",dao.search(q.getParameter("q"),q.getParameter("specialty"),q.getParameter("sort")));q.setAttribute("specializations",dao.specializations());q.getRequestDispatcher("/WEB-INF/views/doctors.jsp").forward(q,s);}catch(NumberFormatException e){s.sendError(400);}catch(Exception e){throw new ServletException("Doctor listings are unavailable.",e);}}
}
