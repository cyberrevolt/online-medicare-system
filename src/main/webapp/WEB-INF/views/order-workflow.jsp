<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Order workflow"/>
<jsp:include page="/WEB-INF/views/common/header.jsp"/>
<section class="page-intro">
  <div><div class="eyebrow"><c:choose><c:when test="${deliveryMode}">DELIVERY STAFF</c:when><c:when test="${adminMode}">ADMINISTRATION</c:when><c:otherwise>PHARMACY TEAM</c:otherwise></c:choose></div><h1><c:choose><c:when test="${deliveryMode}">Assigned deliveries</c:when><c:when test="${adminMode}">Order management</c:when><c:otherwise>Pharmacy order queue</c:otherwise></c:choose></h1><p><c:choose><c:when test="${deliveryMode}">Mark each order as delivered or not delivered.</c:when><c:otherwise>Review prescriptions, prepare orders, then assign a delivery person to dispatch them.</c:otherwise></c:choose></p></div>
  <a class="button button-outline" href="${pageContext.request.contextPath}/dashboard">Dashboard</a>
</section>
<div class="table-card">
  <c:choose>
    <c:when test="${not empty orderQueue}">
      <div class="table-scroll"><table>
        <thead><tr><th>Order</th><th>Patient and address</th><th>Order total</th><th>Payment</th><th>Status</th><th>Prescription</th><c:if test="${not deliveryMode}"><th>Delivery staff</th></c:if><th>Next step</th></tr></thead>
        <tbody><c:forEach var="o" items="${orderQueue}">
          <tr>
            <td><strong>#<c:out value="${o.id}"/></strong><small class="table-sub"><c:out value="${o.createdAt}"/></small></td>
            <td><strong><c:out value="${o.patient}"/></strong><small class="table-sub"><c:out value="${o.address}"/>, <c:out value="${o.city}"/><c:if test="${not empty o.district}">, <c:out value="${o.district}"/></c:if></small></td>
            <td>₹<c:out value="${o.total}"/></td>
            <td><c:out value="${o.method}"/> · <c:out value="${o.payment}"/></td>
            <td><span class="pill"><c:out value="${o.status}"/></span></td>
            <td>
              <c:choose>
                <c:when test="${not empty o.prescriptionStatus}">
                  <span class="pill"><c:out value="${o.prescriptionStatus}"/></span>
                  <c:if test="${not deliveryMode}">
                    <c:choose><c:when test="${adminMode}"><c:url var="prescriptionUrl" value="/admin/order-prescription"><c:param name="orderId" value="${o.id}"/></c:url></c:when><c:otherwise><c:url var="prescriptionUrl" value="/pharmacist/order-prescription"><c:param name="orderId" value="${o.id}"/></c:url></c:otherwise></c:choose>
                    <a class="text-link prescription-download" href="${prescriptionUrl}">Download file</a>
                    <c:if test="${o.prescriptionStatus eq 'PENDING'}">
                      <form method="post" action="${workflowPostUrl}" class="prescription-review-actions">
                        <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="reviewPrescription"><input type="hidden" name="orderId" value="${o.id}">
                        <button class="button button-small" name="decision" value="VERIFIED" type="submit">Verify</button><button class="button button-small button-danger" name="decision" value="REJECTED" type="submit">Reject and cancel</button>
                      </form>
                    </c:if>
                  </c:if>
                </c:when>
                <c:otherwise><span class="muted">No upload</span></c:otherwise>
              </c:choose>
            </td>
            <c:if test="${not deliveryMode}">
              <td>
                <c:if test="${not empty o.assignedDeliveryName}"><strong><c:out value="${o.assignedDeliveryName}"/></strong><small class="table-sub"><c:out value="${o.assignedDeliveryPhone}"/></small></c:if>
                <c:choose>
                  <c:when test="${o.prescriptionStatus eq 'PENDING'}"><small class="table-sub">Verify prescription before dispatch.</small></c:when>
                  <c:when test="${o.status eq 'OUT_FOR_DELIVERY'}"><small class="table-sub">Out for delivery; staff will record the result.</small></c:when>
                  <c:when test="${o.status eq 'PACKED' or o.status eq 'SHIPPED'}">
                    <c:choose><c:when test="${empty deliveryStaff}"><small class="table-sub">Create delivery staff with a phone number first.</small></c:when><c:otherwise>
                      <form method="post" action="${workflowPostUrl}" class="delivery-assignment-form"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="assignDelivery"><input type="hidden" name="orderId" value="${o.id}"><select name="deliveryStaffId" required aria-label="Assign delivery staff for order ${o.id}"><c:if test="${empty o.assignedDeliveryId}"><option value="" selected disabled>Choose delivery staff</option></c:if><c:forEach var="staff" items="${deliveryStaff}"><option value="${staff.id}" <c:if test="${o.assignedDeliveryId eq staff.id}">selected</c:if>><c:out value="${staff.name}"/> · <c:out value="${staff.phone}"/></option></c:forEach></select><button class="button button-small" type="submit">Assign &amp; dispatch</button></form>
                    </c:otherwise></c:choose>
                  </c:when>
                  <c:when test="${o.status eq 'PROCESSING'}"><small class="table-sub">Pack the order before dispatch.</small></c:when>
                  <c:otherwise><small class="table-sub">Move order to Processing before dispatch.</small></c:otherwise>
                </c:choose>
              </td>
            </c:if>
            <td>
              <c:choose>
                <c:when test="${o.prescriptionStatus eq 'PENDING'}"><span class="muted">Review prescription first</span></c:when>
                <c:when test="${deliveryMode}">
                  <form method="post" action="${workflowPostUrl}" class="status-form"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="orderId" value="${o.id}"><select name="status"><c:choose><c:when test="${o.status eq 'SHIPPED'}"><option value="OUT_FOR_DELIVERY">Out for delivery</option></c:when><c:when test="${o.status eq 'OUT_FOR_DELIVERY'}"><option value="DELIVERED">Delivered</option><option value="NOT_DELIVERED">Not delivered</option></c:when></c:choose></select><button class="button button-small" type="submit"><c:choose><c:when test="${o.status eq 'OUT_FOR_DELIVERY'}">Save result</c:when><c:otherwise>Update</c:otherwise></c:choose></button></form>
                </c:when>
                <c:when test="${adminMode}">
                  <c:choose><c:when test="${o.status eq 'PLACED' or o.status eq 'CONFIRMED' or o.status eq 'PROCESSING'}"><form method="post" action="${workflowPostUrl}" class="status-form"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="orderId" value="${o.id}"><select name="status"><c:choose><c:when test="${o.status eq 'PLACED' or o.status eq 'CONFIRMED'}"><option value="PROCESSING">Processing</option></c:when><c:when test="${o.status eq 'PROCESSING'}"><option value="PACKED">Packed</option></c:when></c:choose></select><button class="button button-small" type="submit">Update</button></form></c:when><c:when test="${o.status eq 'OUT_FOR_DELIVERY'}"><span class="muted">Awaiting delivery staff result</span></c:when><c:otherwise><span class="muted">Assign delivery staff to dispatch</span></c:otherwise></c:choose>
                </c:when>
                <c:otherwise>
                  <c:choose><c:when test="${o.status eq 'PLACED' or o.status eq 'CONFIRMED' or o.status eq 'PROCESSING'}"><form method="post" action="${workflowPostUrl}" class="status-form"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="orderId" value="${o.id}"><select name="status"><c:choose><c:when test="${o.status eq 'PLACED' or o.status eq 'CONFIRMED'}"><option value="PROCESSING">Processing</option></c:when><c:when test="${o.status eq 'PROCESSING'}"><option value="PACKED">Packed</option></c:when></c:choose></select><button class="button button-small" type="submit">Update</button></form></c:when><c:otherwise><span class="muted">Assign delivery staff to dispatch</span></c:otherwise></c:choose>
                </c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach></tbody>
      </table></div>
    </c:when>
    <c:otherwise><div class="empty-state"><span>✓</span><h3>Nothing needs your attention</h3><p>New items will appear here as orders move through the workflow.</p></div></c:otherwise>
  </c:choose>
</div>
<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
