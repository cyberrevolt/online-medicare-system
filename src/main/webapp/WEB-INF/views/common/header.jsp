<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <title><c:out value="${pageTitle}" default="Online Medic"/> · Online Medic</title>
  <meta name="description" content="Healthcare consultations and pharmacy ordering in one secure platform.">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/return-workflow.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/doctor-cards.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-doctors.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/profile.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/video-call.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/storefront.css">
  <script defer src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</head>
<body>
<header class="site-header">
  <div class="header-top">
    <div class="header-top-inner">
      <a class="brand" href="${pageContext.request.contextPath}/" aria-label="Online Medic home"><span class="brand-mark"><img src="${pageContext.request.contextPath}/assets/illustrations/care-companion.svg" alt=""></span><span>online<span class="brand-light">medic</span><small>CARE, CONNECTED</small></span></a>
      <c:choose>
        <c:when test="${sessionScope.user.role eq 'PATIENT'}"><a class="header-delivery" href="${pageContext.request.contextPath}/patient/profile"><svg class="header-pin" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2.3"/></svg><span><small>Delivery address</small><strong>Manage in profile</strong></span><span class="header-chevron" aria-hidden="true"></span></a></c:when>
        <c:when test="${empty sessionScope.user}"><div class="header-delivery"><svg class="header-pin" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2.3"/></svg><span><small>Delivery address</small><strong>Added at checkout</strong></span></div></c:when>
        <c:otherwise><a class="header-delivery" href="${pageContext.request.contextPath}/dashboard"><svg class="header-pin workspace-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="4" width="16" height="16" rx="2"/><path d="M8 9h8M8 13h8M8 17h4"/></svg><span><small>Team workspace</small><strong>Open dashboard</strong></span><span class="header-chevron" aria-hidden="true"></span></a></c:otherwise>
      </c:choose>
      <form class="header-search" method="get" action="${pageContext.request.contextPath}/medicines" role="search">
        <svg class="search-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="10.8" cy="10.8" r="6.6"/><path d="m16 16 4.5 4.5"/></svg><input type="search" name="q" placeholder="Search medicines and health products" aria-label="Search medicines and health products"><button type="submit">Search</button>
      </form>
      <div class="header-actions">
        <c:choose><c:when test="${not empty sessionScope.user}"><a class="header-account" href="${pageContext.request.contextPath}/dashboard"><svg class="header-action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.3"/><path d="M5.5 20a6.5 6.5 0 0 1 13 0"/></svg><span><small>Hello</small><strong>My account</strong></span></a></c:when><c:otherwise><a class="header-account" href="${pageContext.request.contextPath}/login"><svg class="header-action-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.3"/><path d="M5.5 20a6.5 6.5 0 0 1 13 0"/></svg><span><small>Welcome</small><strong>Sign in</strong></span></a></c:otherwise></c:choose>
        <c:if test="${empty sessionScope.user or sessionScope.user.role eq 'PATIENT'}"><a class="header-cart" href="${pageContext.request.contextPath}/cart" aria-label="Cart"><svg class="header-action-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M3.5 4.5h2l2.1 11.2h10.7l2-8H7"/><circle cx="9.5" cy="19.5" r="1.2"/><circle cx="17" cy="19.5" r="1.2"/></svg>Cart</a></c:if>
        <button class="menu-toggle" type="button" aria-label="Open navigation" aria-controls="main-navigation" aria-expanded="false"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg></button>
      </div>
    </div>
  </div>
  <div class="header-nav-row"><div class="header-nav-inner">
    <nav class="main-nav" id="main-navigation" aria-label="Main navigation">
      <c:choose>
        <c:when test="${empty sessionScope.user or sessionScope.user.role eq 'PATIENT'}">
          <a href="${pageContext.request.contextPath}/medicines">Pharmacy</a>
          <a href="${pageContext.request.contextPath}/doctors">Find a doctor</a>
          <c:if test="${sessionScope.user.role eq 'PATIENT'}">
            <a href="${pageContext.request.contextPath}/appointments">Appointments</a>
            <a href="${pageContext.request.contextPath}/orders">My orders</a>
            <a href="${pageContext.request.contextPath}/prescriptions">Prescriptions</a>
          </c:if>
        </c:when>
        <c:when test="${sessionScope.user.role eq 'DOCTOR'}">
          <a href="${pageContext.request.contextPath}/doctor/appointments">Appointments</a>
          <a href="${pageContext.request.contextPath}/doctor/availability">Availability</a>
        </c:when>
        <c:when test="${sessionScope.user.role eq 'PHARMACIST'}">
          <a href="${pageContext.request.contextPath}/pharmacist/manage">Manage pharmacy</a>
          <a href="${pageContext.request.contextPath}/pharmacist/orders">Process orders</a>
          <a href="${pageContext.request.contextPath}/prescriptions">Prescriptions</a>
          <a href="${pageContext.request.contextPath}/pharmacist/returns">Returns</a>
        </c:when>
        <c:when test="${sessionScope.user.role eq 'DELIVERY'}">
          <a href="${pageContext.request.contextPath}/delivery">Delivery queue</a>
          <a href="${pageContext.request.contextPath}/delivery/return-pickups">Return pickups</a>
        </c:when>
        <c:otherwise>
          <a href="${pageContext.request.contextPath}/admin">Administration</a>
          <a href="${pageContext.request.contextPath}/admin/orders">Order queue</a>
          <a href="${pageContext.request.contextPath}/admin/doctors">Doctors</a>
          <a href="${pageContext.request.contextPath}/admin/staff">Staff</a>
        </c:otherwise>
      </c:choose>
      <c:if test="${not empty sessionScope.user}">
        <form class="nav-form" action="${pageContext.request.contextPath}/logout" method="post"><input type="hidden" name="csrfToken" value="${csrfToken}"><button class="nav-link" type="submit">Sign out</button></form>
      </c:if>
    </nav>
  </div></div>
</header>
<main class="page-shell">
<c:if test="${not empty flashSuccess}"><div class="alert alert-success" role="status"><c:out value="${flashSuccess}"/></div></c:if>
<c:if test="${not empty flashError}"><div class="alert alert-error" role="alert"><c:out value="${flashError}"/></div></c:if>
