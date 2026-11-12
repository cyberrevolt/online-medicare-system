<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:url var="patientLoginUrl" value="/login/patient"><c:param name="next" value="/cart"/></c:url>
<c:url var="patientRegisterUrl" value="/register/patient"><c:param name="next" value="/cart"/></c:url>
<c:set var="pageTitle" value="Your cart"/><jsp:include page="/WEB-INF/views/common/header.jsp"/>

<section class="page-intro"><div><div class="eyebrow">PHARMACY CHECKOUT</div><h1>Your cart</h1><p>Review quantities and payment. Delivery is ₹50 for orders of ₹150 or less, and free above ₹150.</p></div><a class="button button-outline" href="${pageContext.request.contextPath}/medicines">Continue browsing</a></section>
<c:if test="${not empty prescriptionItems}"><div class="care-disclaimer"><strong>Prescription needed for selected items</strong><p>Only the medicines listed here require a doctor-issued prescription verified by a pharmacist. You can use an existing verified prescription or upload one at checkout.</p><ul><c:forEach var="name" items="${prescriptionItems}"><li><c:out value="${name}"/></li></c:forEach></ul><a class="text-link" href="${pageContext.request.contextPath}/prescriptions">View your prescriptions →</a></div></c:if>

<div class="cart-layout">
  <div class="table-card">
    <c:choose>
      <c:when test="${not empty lines}"><div class="cart-items"><c:forEach var="line" items="${lines}"><div class="cart-row"><div class="mini-product">✚</div><div class="cart-product"><strong><c:out value="${line.medicine.name}"/></strong><span><c:out value="${line.medicine.category}"/> · ₹<c:out value="${line.medicine.price}"/> each <c:if test="${line.medicine.prescriptionRequired}"><span class="pill pill-rx">Prescription required</span></c:if></span></div><form class="quantity-form" method="post" action="${pageContext.request.contextPath}/cart"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="medicineId" value="${line.medicine.id}"><input type="hidden" name="action" value="update"><label class="sr-only">Quantity</label><input type="number" name="quantity" value="${line.quantity}" min="1" max="30"><button class="text-link" type="submit">Update</button></form><strong class="line-total">₹<c:out value="${line.lineTotal}"/></strong><form method="post" action="${pageContext.request.contextPath}/cart"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="medicineId" value="${line.medicine.id}"><input type="hidden" name="action" value="remove"><button class="remove-button" type="submit" aria-label="Remove item">×</button></form></div></c:forEach></div></c:when>
      <c:otherwise><div class="empty-state"><span>＋</span><h3>Your cart is empty</h3><p>Browse the pharmacy catalogue to add items.</p><a class="button" href="${pageContext.request.contextPath}/medicines">Browse pharmacy</a></div></c:otherwise>
    </c:choose>
  </div>

  <c:if test="${not empty lines}"><aside class="checkout-card order-summary">
    <span class="eyebrow">ORDER SUMMARY</span><div class="summary-row"><span>Subtotal</span><strong>₹<c:out value="${total}"/></strong></div><div class="summary-row"><span>Delivery</span><strong><c:choose><c:when test="${deliveryFee eq 0}">Free</c:when><c:otherwise>₹<c:out value="${deliveryFee}"/></c:otherwise></c:choose></strong></div><div class="summary-row summary-total"><span>Total</span><strong>₹<c:out value="${grandTotal}"/></strong></div>
    <c:choose>
      <c:when test="${sessionScope.user.role eq 'PATIENT'}">
        <form method="post" action="${pageContext.request.contextPath}/cart" class="form-stack checkout-form" enctype="multipart/form-data" data-pin-lookup="${pageContext.request.contextPath}/pin-lookup">
          <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="checkout">
          <c:if test="${not empty savedAddresses}"><label>Saved delivery address<select id="savedAddressSelect" name="savedAddressId"><option value="">Enter a new address</option><c:forEach var="address" items="${savedAddresses}"><option value="${address.id}" <c:if test="${address.isDefault}">selected</c:if>><c:out value="${address.label}"/> · <c:out value="${address.line1}"/>, <c:out value="${address.city}"/>, <c:out value="${address.state}"/> <c:out value="${address.postalCode}"/></option></c:forEach></select><span class="optional">Your default address is selected. You can manage saved addresses in your profile.</span></label></c:if>
          <div id="manualCheckoutAddress"><label>Delivery address<input name="line1" required minlength="5" maxlength="220" placeholder="House / street address"></label>
          <label>State / union territory<select id="checkoutState" name="state" required><option value="">Choose a state</option><option>Andhra Pradesh</option><option>Arunachal Pradesh</option><option>Assam</option><option>Bihar</option><option>Chhattisgarh</option><option>Goa</option><option>Gujarat</option><option>Haryana</option><option>Himachal Pradesh</option><option>Jharkhand</option><option>Karnataka</option><option>Kerala</option><option>Madhya Pradesh</option><option>Maharashtra</option><option>Manipur</option><option>Meghalaya</option><option>Mizoram</option><option>Nagaland</option><option>Odisha</option><option>Punjab</option><option>Rajasthan</option><option>Sikkim</option><option>Tamil Nadu</option><option>Telangana</option><option>Tripura</option><option>Uttar Pradesh</option><option>Uttarakhand</option><option>West Bengal</option><option>Andaman and Nicobar Islands</option><option>Chandigarh</option><option>Dadra and Nagar Haveli and Daman and Diu</option><option>Delhi</option><option>Jammu and Kashmir</option><option>Ladakh</option><option>Lakshadweep</option><option>Puducherry</option></select></label>
          <label>PIN code<input id="checkoutPin" name="postalCode" inputmode="numeric" autocomplete="postal-code" pattern="[0-9]{6}" minlength="6" maxlength="6" required placeholder="6-digit PIN code"></label>
          <p class="pin-lookup-status" id="pinLookupStatus" role="status">Choose your state and enter a six-digit PIN code to load matching districts and post offices.</p>
          <div class="form-row" id="pinLookupResults"><label>District<select id="checkoutDistrict" name="district" required disabled><option value="">Enter PIN code first</option></select></label><label>City / post office<select id="checkoutCity" name="city" required disabled><option value="">Enter PIN code first</option></select></label></div>
          <button class="text-link manual-location-toggle" id="manualLocationToggle" type="button">Can't load PIN results? Enter city and district manually</button>
          <div class="form-row manual-location-fields" id="manualLocationFields" hidden><label>District<input name="districtFallback" maxlength="100" disabled></label><label>City<input name="cityFallback" maxlength="100" disabled></label></div>
          </div>
          <c:if test="${not empty prescriptionItems}">
            <div class="checkout-prescription"><strong>Prescription for your order</strong>
              <c:choose>
                <c:when test="${prescriptionUploadRequired}"><label>Upload prescription <span class="optional">Required unless a matching verified prescription is already on file</span><input type="file" name="prescriptionFile" accept="application/pdf,image/jpeg,image/png" required></label></c:when>
                <c:otherwise><label>Upload prescription <span class="optional">Optional · uploading a new file pauses the order for pharmacist review</span><input type="file" name="prescriptionFile" accept="application/pdf,image/jpeg,image/png"></label></c:otherwise>
              </c:choose>
              <span class="optional">Doctor-issued PDF, JPEG, or PNG · maximum 5 MB. Prescription files are private and only pharmacy staff can review them.</span>
            </div>
          </c:if>
          <label>Payment method<select name="method"><option value="DEMO_CARD">Demo card (simulated)</option><option value="COD">Cash on delivery</option></select></label>
          <label>Demo card outcome<select name="outcome"><option value="success">Simulate success</option><option value="failure">Simulate failure</option></select></label>
          <button class="button button-full" type="submit">Place order <span>→</span></button>
        </form>
      </c:when>
      <c:when test="${empty sessionScope.user}"><div class="guest-checkout"><strong>Sign in to complete your order</strong><p>Your cart will be kept while you sign in or create a patient account.</p><a class="button button-full" href="${patientLoginUrl}">Sign in as patient <span>→</span></a><p class="auth-switch">New patient? <a href="${patientRegisterUrl}">Create an account</a></p></div></c:when>
      <c:otherwise><div class="guest-checkout"><strong>Patient account required</strong><p>Pharmacy orders can only be completed from a patient account. Sign out, then sign in as a patient.</p></div></c:otherwise>
    </c:choose>
    <p class="fine-print">Demo payment does not charge a real bank account. Orders with a newly uploaded prescription wait for pharmacy review.</p>
  </aside></c:if>
</div>
<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
