package com.onlinemedic.filter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

public class SecurityHeadersFilter implements Filter {
    private final SecureRandom random = new SecureRandom();
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        HttpServletRequest req=(HttpServletRequest)request;req.setCharacterEncoding("UTF-8");HttpServletResponse res=(HttpServletResponse)response;HttpSession session=req.getSession(true);
        if(session.getAttribute("csrfToken")==null){byte[] bytes=new byte[32];random.nextBytes(bytes);session.setAttribute("csrfToken",Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));}
        req.setAttribute("csrfToken",session.getAttribute("csrfToken"));
        Object ok=session.getAttribute("flashSuccess"),bad=session.getAttribute("flashError");if(ok!=null){req.setAttribute("flashSuccess",ok);session.removeAttribute("flashSuccess");}if(bad!=null){req.setAttribute("flashError",bad);session.removeAttribute("flashError");}
        res.setHeader("X-Content-Type-Options","nosniff");res.setHeader("X-Frame-Options","DENY");res.setHeader("Referrer-Policy","strict-origin-when-cross-origin");res.setHeader("Permissions-Policy","camera=(self), microphone=(self), geolocation=()");
        res.setHeader("Content-Security-Policy","default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        if("POST".equalsIgnoreCase(req.getMethod())){String submitted=req.getParameter("csrfToken"),expected=(String)session.getAttribute("csrfToken");if(submitted==null||!java.security.MessageDigest.isEqual(submitted.getBytes(java.nio.charset.StandardCharsets.UTF_8),expected.getBytes(java.nio.charset.StandardCharsets.UTF_8))){res.sendError(403,"Security token expired. Reload the page and try again.");return;}}
        chain.doFilter(request,response);
    }
}
