<%@ page contentType="text/html;charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><c:set var="pageTitle" value="Choose sign-in portal"/><jsp:include page="/WEB-INF/views/common/header.jsp"/>
<section class="page-intro"><div><div class="eyebrow">ACCOUNT ACCESS</div><h1>Choose your sign-in portal</h1><p>Use the portal that matches the role assigned to your account.</p></div></section>
<section class="role-portal-grid">
  <a class="role-portal-card" href="${pageContext.request.contextPath}/login/patient"><span class="icon-tile mint">P</span><h2>Patient</h2><p>Appointments, prescriptions, and pharmacy orders.</p><strong>Patient sign in →</strong></a>
  <a class="role-portal-card" href="${pageContext.request.contextPath}/login/doctor"><span class="icon-tile lavender">D</span><h2>Doctor</h2><p>Consultations, appointments, prescriptions, and availability.</p><strong>Doctor sign in →</strong></a>
  <a class="role-portal-card" href="${pageContext.request.contextPath}/login/pharmacist"><span class="icon-tile peach">Rx</span><h2>Pharmacist</h2><p>Prescription review and pharmacy order workflow.</p><strong>Pharmacist sign in →</strong></a>
  <a class="role-portal-card" href="${pageContext.request.contextPath}/login/delivery"><span class="icon-tile blue">→</span><h2>Delivery staff</h2><p>Assigned deliveries and order status updates.</p><strong>Delivery sign in →</strong></a>
  <a class="role-portal-card" href="${pageContext.request.contextPath}/login/admin"><span class="icon-tile mint">A</span><h2>Administrator</h2><p>User, medicine, inventory, and order administration.</p><strong>Admin sign in →</strong></a>
</section>
<section class="panel-card role-signup-note"><h2>Need an account?</h2><p>Patients can create accounts online. Doctor, pharmacist, and delivery accounts are provisioned by an administrator. The initial administrator is created during server setup.</p><a class="button" href="${pageContext.request.contextPath}/register/patient">Patient signup</a></section>
<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
