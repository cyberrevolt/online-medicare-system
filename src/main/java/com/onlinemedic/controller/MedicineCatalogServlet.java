package com.onlinemedic.controller;
import com.onlinemedic.dao.MedicineDao;
import jakarta.servlet.*;import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;
@WebServlet("/medicines")
public class MedicineCatalogServlet extends HttpServlet {
 private final MedicineDao dao=new MedicineDao();
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse s)throws ServletException,IOException{try{String id=q.getParameter("id");if(id!=null){var item=dao.find(Long.parseLong(id));if(item.isEmpty()){s.sendError(404);return;}q.setAttribute("medicine",item.get());q.getRequestDispatcher("/WEB-INF/views/medicine-detail.jsp").forward(q,s);return;}q.setAttribute("medicines",dao.search(q.getParameter("q"),q.getParameter("category"),q.getParameter("sort")));q.setAttribute("categories",dao.categories());q.setAttribute("pharmacyProfile",dao.pharmacyProfile().orElse(null));q.getRequestDispatcher("/WEB-INF/views/medicines.jsp").forward(q,s);}catch(NumberFormatException e){s.sendError(400);}catch(Exception e){throw new ServletException("Medicine catalogue is unavailable.",e);}}
}
