<%@ page contentType="text/html;charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><c:set var="pageTitle" value="Sign in"/><jsp:include page="/WEB-INF/views/common/header.jsp"/>
<section class="auth-layout account-access-layout">
  <div class="auth-aside">
    <div class="eyebrow eyebrow-light">WELCOME TO ONLINE MEDIC</div>
    <h1>Care, connected.</h1>
    <p>Sign in once and we’ll open the workspace that matches your account.</p>
    <div class="auth-quote">Patients, doctors, pharmacists, delivery staff, and administrators all use the same sign-in form.</div>
  </div>
  <div class="auth-panel">
    <span class="eyebrow">YOUR ACCOUNT</span>
    <h2>Sign in</h2>
    <p class="muted">Enter the email address and password for your Online Medic account.</p>
    <c:if test="${not empty error}"><div class="alert alert-error" role="alert"><c:out value="${error}"/></div></c:if>
    <form method="post" action="${pageContext.request.contextPath}/login" class="form-stack">
      <input type="hidden" name="csrfToken" value="${csrfToken}">
      <input type="hidden" name="next" value="<c:out value='${param.next}'/>">
      <label>Email address<input type="email" name="email" autocomplete="username" required maxlength="190" value="<c:out value='${param.email}'/>" placeholder="you@example.com"></label>
      <label>Password<input type="password" name="password" autocomplete="current-password" required maxlength="128" placeholder="Your password"></label>
      <button class="button button-full" type="submit">Sign in <span>→</span></button>
    </form>
    <p class="auth-switch">New patient? <a href="${pageContext.request.contextPath}/register/patient">Create a patient account</a></p>
    <p class="account-access-note">Staff accounts are created by an administrator. Need access? Ask your Online Medic administrator.</p>
  </div>
</section>
<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
