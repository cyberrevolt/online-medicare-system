<%@ page contentType="text/html;charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Doctor profile"/><jsp:include page="/WEB-INF/views/common/header.jsp"/>
<div class="breadcrumb"><a href="${pageContext.request.contextPath}/doctors">Doctors</a><span>›</span><c:out value="${doctor.fullName}"/></div>
<section class="profile-layout">
  <article class="profile-card">
    <div class="profile-hero">
      <c:choose><c:when test="${not empty doctor.profileImage}"><img class="doctor-photo avatar-profile" src="${pageContext.request.contextPath}${doctor.profileImage}" alt="Doctor profile"></c:when><c:otherwise><div class="doctor-avatar avatar-profile">MD</div></c:otherwise></c:choose>
      <div><c:choose><c:when test="${doctor.demoProfile}"><span class="pill pill-demo">Fictional demo · simulated appointments</span></c:when><c:otherwise><span class="pill pill-green">Accepting patients</span></c:otherwise></c:choose><h1><c:out value="${doctor.fullName}"/></h1><p class="specialty"><c:out value="${doctor.specialization}"/></p><span class="muted"><c:out value="${doctor.qualification}"/></span></div>
    </div>
    <div class="profile-stats">
      <c:choose><c:when test="${doctor.demoProfile}"><div><strong>Sample profile</strong><span>Demo account</span></div><div><strong>Chat + video</strong><span>One-to-one interaction</span></div><div><strong>₹<c:out value="${doctor.fee}"/></strong><span>Simulated fee</span></div></c:when>
      <c:otherwise><div><strong><c:out value="${doctor.experienceYears}"/> years</strong><span>Experience</span></div><div><strong>★ <c:out value="${doctor.rating}"/></strong><span>Patient rating</span></div><div><strong>₹<c:out value="${doctor.fee}"/></strong><span>Consultation fee</span></div></c:otherwise></c:choose>
    </div>
    <section class="profile-section"><h2>About</h2><c:choose><c:when test="${doctor.demoProfile}"><p>This fictional profile is included to demonstrate appointment booking, private patient–doctor messaging, and video calls. It is not a real healthcare professional and cannot provide medical care.</p></c:when><c:otherwise><p><c:out value="${doctor.about}"/></p></c:otherwise></c:choose></section>
    <section class="profile-section"><h2>Practice</h2><p><strong><c:out value="${doctor.clinic}"/></strong></p><p class="muted">Confirmed appointments include a private room for text chat and one-to-one video consultation.</p></section>
    <section class="profile-section"><h2>Available appointments</h2>
      <c:if test="${doctor.demoProfile}"><div class="demo-profile-notice"><strong>Demo appointment service</strong><p>This booking uses simulated fees and fictional provider details. Confirmed bookings open a private appointment room with text chat and video for this patient and demo doctor. It is not a real appointment or medical service.</p></div></c:if>
      <c:if test="${empty sessionScope.user}"><p class="muted">Sign in with a patient account to reserve an available time.</p><a class="button" href="${pageContext.request.contextPath}/login">Patient sign in to book</a></c:if>
      <c:if test="${not empty sessionScope.user and sessionScope.user.role ne 'PATIENT'}"><p class="muted">Appointment booking is available to patient accounts.</p></c:if>
      <c:if test="${not empty sessionScope.user and sessionScope.user.role eq 'PATIENT'}"><c:choose>
        <c:when test="${not empty slots}"><form method="post" action="${pageContext.request.contextPath}/appointments" class="booking-form"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="doctorId" value="${doctor.id}">
          <label>Choose a date and time<select name="slot" required><c:forEach var="slot" items="${slots}"><option value="<c:out value='${slot}'/>"><c:out value="${slot.replace('|',' · ')}"/></option></c:forEach></select></label>
          <label>Interaction type<select name="type" required><c:choose><c:when test="${doctor.demoProfile}"><option value="Secure chat">Private text chat</option><option value="Video consultation">One-to-one video consultation</option></c:when><c:otherwise><option value="In person">In person</option><option value="Secure chat">Private text chat</option><option value="Video consultation">One-to-one video consultation</option></c:otherwise></c:choose></select></label>
          <div class="price-row"><span><c:choose><c:when test="${doctor.demoProfile}">Simulated fee</c:when><c:otherwise>Fee due at booking</c:otherwise></c:choose></span><strong>₹<c:out value="${doctor.fee}"/></strong></div>
          <button class="button button-full" type="submit">Continue to demo payment <span>→</span></button><p class="fine-print">Payment is simulated; it does not charge a real bank account. Confirmed bookings open the private appointment room for chat and video.</p>
        </form></c:when><c:otherwise><div class="empty-inline">No upcoming slots are available right now. Please check back soon.</div></c:otherwise>
      </c:choose></c:if>
    </section>
  </article>
  <aside class="profile-aside"><div class="aside-card"><span class="icon-tile mint">♡</span><c:choose><c:when test="${doctor.demoProfile}"><h3>Demo interaction</h3><p>After a simulated booking is confirmed, the patient and doctor can use private text chat or start a one-to-one video call in their appointment room.</p></c:when><c:otherwise><h3>Care led by a professional</h3><p>Review the doctor's profile and choose an open slot before booking.</p></c:otherwise></c:choose><ul class="check-list"><li>Secure account access</li><li>Appointment history</li><li>Private appointment chat and video</li></ul></div><div class="care-disclaimer"><strong>Health information</strong><p>Online Medic helps connect patients and professionals. Medical decisions are made by your healthcare professional. The demo profiles are fictional.</p></div></aside>
</section>
<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
