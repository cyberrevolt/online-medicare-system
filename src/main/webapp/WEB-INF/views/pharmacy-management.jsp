<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Manage pharmacy"/>
<jsp:include page="/WEB-INF/views/common/header.jsp"/>

<section class="dashboard-welcome">
  <div>
    <div class="eyebrow">PHARMACIST WORKSPACE</div>
    <h1>Manage your pharmacy</h1>
    <p>Update your storefront, manage medicine listings, and maintain stock.</p>
  </div>
  <div class="admin-shortcuts">
    <a class="button button-outline" href="${pageContext.request.contextPath}/pharmacist/orders">Process orders →</a>
    <a class="button button-soft" href="${pageContext.request.contextPath}/prescriptions">Review prescriptions →</a>
  </div>
</section>

<section class="panel-card pharmacy-profile-editor">
  <div class="pharmacy-editor-preview">
    <c:choose>
      <c:when test="${not empty pharmacy.imageUrl}"><img src="${pageContext.request.contextPath}${pharmacy.imageUrl}" alt="Current pharmacy profile"></c:when>
      <c:otherwise><img src="${pageContext.request.contextPath}/assets/catalog/pharmacy-background.jpg" alt="Online pharmacy"></c:otherwise>
    </c:choose>
    <div><span class="eyebrow">STOREFRONT PROFILE</span><h2><c:out value="${pharmacy.name}" default="Online Medic Pharmacy"/></h2><p class="muted">This name and image appear in the public pharmacy catalogue.</p></div>
  </div>
  <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="form-stack" enctype="multipart/form-data">
    <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="saveProfile">
    <div class="form-row"><label>Pharmacy name<input name="pharmacyName" value="<c:out value='${pharmacy.name}'/>" required minlength="2" maxlength="180"></label><label>Pharmacy image<input type="file" name="profileImage" accept="image/jpeg,image/png"><span class="optional">Optional · JPEG or PNG · up to 5 MB</span></label></div>
    <button class="button" type="submit">Save pharmacy profile</button>
  </form>
</section>

<section class="panel-card pharmacy-add-medicine">
  <span class="eyebrow">CATALOGUE</span><h2>Add a medicine</h2><p class="muted">Create a listing with its image, price, prescription status, and starting stock.</p>
  <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="form-stack" enctype="multipart/form-data">
    <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="addMedicine">
    <div class="form-row"><label>Medicine name<input name="name" required minlength="3" maxlength="180"></label><label>Price (₹)<input type="number" name="price" required min="0.01" step="0.01"></label></div>
    <div class="form-row"><label>Category<select name="category" required><c:forEach var="itemCategory" items="${categories}"><option value="<c:out value='${itemCategory}'/>"><c:out value="${itemCategory}"/></option></c:forEach></select></label><label>Manufacturer<select name="manufacturer" required><c:forEach var="itemManufacturer" items="${manufacturers}"><option value="<c:out value='${itemManufacturer}'/>"><c:out value="${itemManufacturer}"/></option></c:forEach></select></label></div>
    <label>Description<textarea name="description" rows="2" maxlength="1200"></textarea></label>
    <label>Medicine photo<input type="file" name="medicineImage" accept="image/jpeg,image/png"><span class="optional">Optional · JPEG or PNG · up to 5 MB</span></label>
    <div class="form-row"><label>Starting stock<input type="number" name="stock" required min="0" max="100000" value="0"></label><label>Low-stock alert at<input type="number" name="minStock" required min="0" max="100000" value="5"></label></div>
    <label class="checkbox-line"><input type="checkbox" name="prescriptionRequired" value="true"> Prescription required for purchase</label>
    <button class="button" type="submit">Add medicine to catalogue</button>
  </form>
</section>

<section class="dashboard-panels inventory-operations">
  <article class="panel-card"><span class="eyebrow">PURCHASING</span><h2>Receive a supplier batch</h2><p class="muted">Track batch number, dates, quantity, and purchase cost. Expired batches are excluded from sale stock.</p>
    <c:choose><c:when test="${empty suppliers}"><p class="alert alert-info">Add a supplier before receiving inventory.</p></c:when><c:otherwise>
    <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="form-stack">
      <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="receiveBatch">
      <label>Medicine<select name="medicineId" required><c:forEach var="item" items="${medicines}"><c:if test="${item.price gt 0}"><option value="${item.id}"><c:out value="${item.name}"/></option></c:if></c:forEach></select></label>
      <label>Supplier<select name="supplierId" required><c:forEach var="supplier" items="${suppliers}"><option value="${supplier.id}"><c:out value="${supplier.name}"/></option></c:forEach></select></label>
      <label>Batch number<input name="batchNumber" required minlength="2" maxlength="90" pattern="[A-Za-z0-9._/-]+" placeholder="e.g. LOT-2026-01"></label>
      <div class="form-row"><label>Manufactured on <span class="optional">Optional</span><input type="date" name="manufacturingDate" max="${today}"></label><label>Expires on<input type="date" name="expiryDate" min="${expiryMin}" required></label></div>
      <div class="form-row"><label>Quantity received<input type="number" name="quantity" min="1" max="50000" required></label><label>Purchase cost per unit (₹)<input type="number" name="unitCost" min="0" max="1000000" step="0.01" required></label></div>
      <button class="button" type="submit">Receive and record batch</button>
    </form></c:otherwise></c:choose>
  </article>
  <article class="panel-card"><span class="eyebrow">SUPPLIERS & EXPIRY</span><h2>Suppliers</h2>
    <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="form-stack"><input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="createSupplier"><label>Supplier name<input name="name" required minlength="2" maxlength="160"></label><div class="form-row"><label>Email <span class="optional">Optional</span><input type="email" name="email" maxlength="190"></label><label>Phone <span class="optional">Optional</span><input type="tel" name="phone" maxlength="30"></label></div><button class="button button-outline" type="submit">Add supplier</button></form>
    <c:if test="${not empty suppliers}"><div class="supplier-list"><c:forEach var="supplier" items="${suppliers}"><div class="order-line"><div><strong><c:out value="${supplier.name}"/></strong><span><c:out value="${supplier.email}" default="No email"/> · <c:out value="${supplier.phone}" default="No phone"/></span></div></div></c:forEach></div></c:if>
    <h3>Expiry watch · next 30 days</h3><c:choose><c:when test="${not empty expiryWatch}"><div class="table-scroll"><table><thead><tr><th>Medicine / batch</th><th>Expires</th><th>Units</th><th>Action</th></tr></thead><tbody><c:forEach var="batch" items="${expiryWatch}"><tr><td><strong><c:out value="${batch.medicineName}"/></strong><span class="table-sub">Batch <c:out value="${batch.batchNumber}"/></span></td><td><c:out value="${batch.expiryDate}"/></td><td><c:out value="${batch.quantity}"/></td><td><span class="pill"><c:choose><c:when test="${batch.expired}">Expired · remove from shelf</c:when><c:otherwise>Expiring soon</c:otherwise></c:choose></span></td></tr></c:forEach></tbody></table></div></c:when><c:otherwise><p class="muted">No expired or soon-to-expire batches on hand.</p></c:otherwise></c:choose>
    <details class="batch-register"><summary>View all on-hand batches (<c:out value="${batchInventory.size()}"/>)</summary><div class="table-scroll"><table><thead><tr><th>Medicine / batch</th><th>Supplier</th><th>Manufactured</th><th>Expires</th><th>Quantity</th><th>Unit cost</th></tr></thead><tbody><c:forEach var="batch" items="${batchInventory}"><tr><td><strong><c:out value="${batch.medicineName}"/></strong><span class="table-sub"><c:out value="${batch.batchNumber}"/></span></td><td><c:out value="${batch.supplierName}" default="Legacy batch"/></td><td><c:out value="${batch.manufacturingDate}" default="—"/></td><td><c:out value="${batch.expiryDate}"/></td><td><c:out value="${batch.quantity}"/></td><td>₹<c:out value="${batch.purchasePrice}"/></td></tr></c:forEach><c:if test="${empty batchInventory}"><tr><td colspan="6">No supplier batches are currently on hand.</td></tr></c:if></tbody></table></div></details>
  </article>
</section>

<section class="panel-card pharmacy-inventory">
  <div class="results-heading"><div><span class="eyebrow">INVENTORY</span><h2>Manage medicine listings</h2></div><span class="result-count"><c:out value="${medicines.size()}"/> listed</span></div>
  <p class="muted">Edit product details at any time. Stock changes are recorded separately.</p>
  <div class="table-scroll">
    <table>
      <thead><tr><th>Medicine</th><th>Category</th><th>Price</th><th>Available</th><th>Manage</th></tr></thead>
      <tbody>
        <c:forEach var="item" items="${medicines}">
          <tr>
            <td>
              <div class="inventory-medicine-name">
                <c:if test="${not empty item.imageUrl}"><img src="${pageContext.request.contextPath}${item.imageUrl}" alt=""></c:if>
                <strong><c:out value="${item.name}"/></strong>
              </div>
            </td>
            <td><c:out value="${item.category}"/></td>
            <td><c:choose><c:when test="${item.price gt 0}">₹<c:out value="${item.price}"/></c:when><c:otherwise>Price to be confirmed</c:otherwise></c:choose></td>
            <td><c:out value="${item.stock}"/></td>
            <td>
              <details class="configure-medicine edit-medicine">
                <summary><c:choose><c:when test="${item.price le 0}">Edit details and set price</c:when><c:otherwise>Edit product details</c:otherwise></c:choose></summary>
                <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="form-stack" enctype="multipart/form-data">
                  <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="updateMedicine"><input type="hidden" name="medicineId" value="${item.id}">
                  <label>Medicine name<input name="name" value="<c:out value='${item.name}'/>" required minlength="3" maxlength="180"></label>
                  <div class="form-row"><label>Price (₹)<input type="number" name="price" value="<c:out value='${item.price}'/>" required min="0.01" step="0.01"></label><label>Low-stock alert at<input type="number" name="minStock" value="<c:out value='${item.minStock}'/>" required min="0" max="100000"></label></div>
                  <div class="form-row"><label>Category<select name="category" required><c:forEach var="itemCategory" items="${categories}"><option value="<c:out value='${itemCategory}'/>" <c:if test="${item.category eq itemCategory}">selected</c:if>><c:out value="${itemCategory}"/></option></c:forEach></select></label><label>Manufacturer<select name="manufacturer" required><c:forEach var="itemManufacturer" items="${manufacturers}"><option value="<c:out value='${itemManufacturer}'/>" <c:if test="${item.manufacturer eq itemManufacturer}">selected</c:if>><c:out value="${itemManufacturer}"/></option></c:forEach></select></label></div>
                  <label>Description<textarea name="description" rows="3" maxlength="1200"><c:out value="${item.description}"/></textarea></label>
                  <label>Replace medicine image<input type="file" name="medicineImage" accept="image/jpeg,image/png"><span class="optional">Leave blank to keep the current image · JPEG or PNG · up to 5 MB</span></label>
                  <label class="checkbox-line"><input type="checkbox" name="prescriptionRequired" value="true" <c:if test="${item.prescriptionRequired}">checked</c:if>> Prescription required for purchase</label>
                  <button class="button button-small" type="submit">Save changes</button>
                </form>
              </details>
              <c:if test="${item.price le 0}">
                <details class="configure-medicine">
                  <summary>Set verified sale details with opening stock</summary>
                  <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="form-stack">
                    <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="configureMedicine"><input type="hidden" name="medicineId" value="${item.id}">
                    <label>Confirmed price (₹)<input type="number" name="price" required min="0.01" step="0.01"></label>
                    <label>Product details<textarea name="description" rows="3" maxlength="1200" required><c:out value="${item.description}"/></textarea></label>
                    <div class="form-row"><label>Opening stock<input type="number" name="stock" required min="0" max="100000" value="0"></label><label>Low-stock alert at<input type="number" name="minStock" required min="0" max="100000" value="5"></label></div>
                    <label class="checkbox-line"><input type="checkbox" name="prescriptionRequired" value="true"> Prescription required for purchase</label>
                    <button class="button button-small" type="submit">Save verified details</button>
                  </form>
                </details>
              </c:if>
              <c:if test="${item.price gt 0}">
                <form method="post" action="${pageContext.request.contextPath}/pharmacist/manage" class="restock-form inventory-restock-form">
                  <input type="hidden" name="csrfToken" value="${csrfToken}"><input type="hidden" name="action" value="restock"><input type="hidden" name="medicineId" value="${item.id}">
                  <label class="sr-only">Add units of <c:out value="${item.name}"/></label><input type="number" name="quantity" min="1" max="5000" placeholder="Add units" required><button class="button button-small" type="submit">Restock</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        <c:if test="${empty medicines}"><tr><td colspan="5">No medicines are listed yet.</td></tr></c:if>
      </tbody>
    </table>
  </div>
</section>
<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
