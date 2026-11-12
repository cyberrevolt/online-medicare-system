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
  <script defer src="${pageContext.request.contextPath}/assets/js/app.js"></script>
</head>
<body>
<header class="site-header"><div class="nav-wrap">
  <a class="brand" href="${pageContext.request.contextPath}/"><span class="brand-mark">+</span><span>online<span class="brand-light">medic</span><small>CARE, CONNECTED</small></span></a>
  <button class="menu-toggle" type="button" aria-label="Toggle navigation" aria-expanded="false">☰</button>
  <nav class="main-nav" aria-label="Main navigation">
    <c:if test="${empty sessionScope.user or sessionScope.user.role eq 'PATIENT'}">
      <a href="${pageContext.request.contextPath}/doctors">Find a doctor</a>
      <a href="${pageContext.request.contextPath}/medicines">Pharmacy</a>
      <a href="${pageContext.request.contextPath}/cart">Cart</a>
    </c:if>
    <c:choose><c:when test="${not empty sessionScope.user}">
      <a href="${pageContext.request.contextPath}/dashboard">Dashboard</a><a href="${pageContext.request.contextPath}/notifications">Updates</a><c:if test="${sessionScope.user.role eq 'PATIENT'}"><a href="${pageContext.request.contextPath}/appointments">Appointments</a><a href="${pageContext.request.contextPath}/patient/profile">My profile</a></c:if><c:if test="${sessionScope.user.role eq 'DOCTOR'}"><a href="${pageContext.request.contextPath}/doctor/appointments">Appointments</a><a href="${pageContext.request.contextPath}/doctor/availability">Availability</a></c:if><c:if test="${sessionScope.user.role eq 'PHARMACIST'}"><a href="${pageContext.request.contextPath}/pharmacist/manage">Manage pharmacy</a><a href="${pageContext.request.contextPath}/pharmacist/returns">Returns</a></c:if><c:if test="${sessionScope.user.role eq 'DELIVERY'}"><a href="${pageContext.request.contextPath}/delivery/return-pickups">Return pickups</a></c:if>
      <form class="nav-form" action="${pageContext.request.contextPath}/logout" method="post"><input type="hidden" name="csrfToken" value="${csrfToken}"><button class="nav-link" type="submit">Sign out</button></form>
    </c:when><c:otherwise><a href="${pageContext.request.contextPath}/login">Sign in</a><a class="button button-small" href="${pageContext.request.contextPath}/register">Create account</a></c:otherwise></c:choose>
  </nav>
</div></header>
<main class="page-shell">
<c:if test="${not empty flashSuccess}"><div class="alert alert-success" role="status"><c:out value="${flashSuccess}"/></div></c:if>
<c:if test="${not empty flashError}"><div class="alert alert-error" role="alert"><c:out value="${flashError}"/></div></c:if>
