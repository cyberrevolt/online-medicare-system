<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Manage pharmacy and delivery staff"/>
<jsp:include page="/WEB-INF/views/common/header.jsp"/>

<section class="page-intro">
  <div><div class="eyebrow">ADMIN CONSOLE</div><h1>Manage pharmacy and delivery staff</h1><p>Create accounts, update staff details, reset passwords, or pause access.</p></div>
  <a class="button button-outline" href="${pageContext.request.contextPath}/admin">Admin dashboard</a>
</section>

<c:if test="${not empty error}"><div class="alert alert-error"><c:out value="${error}"/></div></c:if>
<section class="panel-card admin-form-card">
  <h2>Add a staff account</h2>
  <form method="post" action="${pageContext.request.contextPath}/admin/staff" class="form-stack" enctype="multipart/form-data">
    <input type="hidden" name="csrfToken" value="${csrfToken}">
    <label>Staff role<select name="role"><option value="PHARMACIST">Pharmacist</option><option value="DELIVERY">Delivery staff</option></select></label>
    <div class="form-row"><label>Full name<input name="name" required minlength="2" maxlength="120" autocomplete="name"></label><label>Email address<input type="email" name="email" required maxlength="190" autocomplete="email"></label></div>
    <div class="form-row"><label>Phone <span class="optional">Required for delivery contact</span><input type="tel" name="phone" maxlength="30" autocomplete="tel"></label><label>Temporary password<input type="password" name="password" required minlength="10" maxlength="128" autocomplete="new-password"></label></div>
    <div class="form-row"><label>License number / vehicle ID<input name="credential" required minlength="2" maxlength="100"></label><label>Pharmacy name <span class="optional">Required for pharmacists</span><input name="location" maxlength="180"></label></div>
    <label>Pharmacy profile image <span class="optional">Optional · JPEG or PNG · up to 5 MB</span><input type="file" name="profileImage" accept="image/jpeg,image/png"></label>
    <button class="button" type="submit">Create staff account <span>→</span></button>
  </form>
</section>

<section class="panel-card admin-doctors-panel staff-management">
  <div class="results-heading"><div><span class="eyebrow">STAFF DIRECTORY</span><h2>Existing accounts</h2><p class="muted">Edit sign-in details and role-specific credentials. Leave the password blank to keep it.</p></div><span class="pill"><c:out value="${staff.size()}"/> accounts</span></div>
  <c:choose>
    <c:when test="${not empty staff}">
      <div class="admin-doctor-list">
        <c:forEach var="person" items="${staff}">
          <article class="admin-doctor-card">
            <div class="admin-doctor-summary">
              <c:choose><c:when test="${person.role eq 'PHARMACIST' and not empty person.image}"><img class="admin-doctor-photo" src="${pageContext.request.contextPath}${person.image}" alt="Pharmacy profile"></c:when><c:otherwise><div class="admin-doctor-avatar"><c:choose><c:when test="${person.role eq 'PHARMACIST'}">Rx</c:when><c:otherwise>→</c:otherwise></c:choose></div></c:otherwise></c:choose>
              <div class="admin-doctor-heading"><div><h3><c:out value="${person.name}"/></h3><span class="specialty"><c:choose><c:when test="${person.role eq 'PHARMACIST'}">Pharmacist</c:when><c:otherwise>Delivery staff</c:otherwise></c:choose></span><span class="admin-doctor-sub"><c:out value="${person.email}"/><c:if test="${not empty person.phone}"> · <c:out value="${person.phone}"/></c:if></span></div><div class="admin-doctor-badges"><c:choose><c:when test="${person.active}"><span class="pill pill-green">Active</span></c:when><c:otherwise><span class="pill">Paused</span></c:otherwise></c:choose></div></div>
            </div>
            <div class="admin-doctor-details"><div><small>Pharmacy / credential</small><strong><c:choose><c:when test="${person.role eq 'PHARMACIST'}"><c:out value="${person.pharmacy}"/> · <c:out value="${person.license}"/></c:when><c:otherwise>Vehicle · <c:out value="${person.vehicle}"/></c:otherwise></c:choose></strong></div></div>
            <details class="admin-doctor-edit"><summary>Edit staff account</summary>
              <form method="post" action="${pageContext.request.contextPath}/admin/staff" class="form-stack">
                <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="updateStaff"><input type="hidden" name="staffId" value="${person.id}"><input type="hidden" name="role" value="${person.role}">
                <div class="form-row"><label>Full name<input name="name" required minlength="2" maxlength="120" value="<c:out value='${person.name}'/>" autocomplete="name"></label><label>Sign-in email<input type="email" name="email" required maxlength="190" value="<c:out value='${person.email}'/>" autocomplete="email"></label></div>
                <div class="form-row"><label>Phone<input type="tel" name="phone" maxlength="30" value="<c:out value='${person.phone}'/>" autocomplete="tel"></label><label><c:choose><c:when test="${person.role eq 'PHARMACIST'}">License number</c:when><c:otherwise>Vehicle ID</c:otherwise></c:choose><input name="credential" required minlength="2" maxlength="100" value="<c:out value='${person.credential}'/>"></label></div>
                <c:if test="${person.role eq 'PHARMACIST'}"><label>Pharmacy name<input name="location" required minlength="2" maxlength="180" value="<c:out value='${person.pharmacy}'/>"></label></c:if>
                <label>Set a new password <span class="optional">Optional · leave blank to keep the current password</span><input type="password" name="newPassword" minlength="10" maxlength="128" autocomplete="new-password"></label>
                <label class="checkbox-line"><input type="checkbox" name="active" value="true" <c:if test="${person.active}">checked</c:if>> Active account (can sign in and receive work)</label>
                <c:if test="${person.role eq 'DELIVERY'}"><p class="optional">A delivery account with assigned active orders cannot be paused until those orders are reassigned.</p></c:if>
                <button class="button button-small" type="submit">Save staff changes</button>
              </form>
            </details>
          </article>
        </c:forEach>
      </div>
    </c:when>
    <c:otherwise><div class="empty-inline">No pharmacy or delivery staff accounts exist yet. Create the first account above.</div></c:otherwise>
  </c:choose>
</section>

<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
