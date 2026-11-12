package com.onlinemedic.filter;
import com.onlinemedic.model.User;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;

public class AuthenticationFilter implements Filter {
    private static final Map<String,String> ROLE=Map.of("patient","PATIENT","doctor","DOCTOR","admin","ADMIN","pharmacist","PHARMACIST","delivery","DELIVERY");
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        HttpServletRequest req=(HttpServletRequest)request;HttpServletResponse res=(HttpServletResponse)response;User user=(User)req.getSession(true).getAttribute("user");
        String path=req.getServletPath();String segment=path.substring(1,path.indexOf('/',1)>0?path.indexOf('/',1):path.length());String required=ROLE.get(segment);
        if(user==null){res.sendRedirect(req.getContextPath()+"/login?next="+java.net.URLEncoder.encode(req.getRequestURI().substring(req.getContextPath().length()),java.nio.charset.StandardCharsets.UTF_8));return;}
        if(!user.role().equals(required)){res.sendError(403,"You do not have access to this area.");return;}
        chain.doFilter(request,response);
    }
}
