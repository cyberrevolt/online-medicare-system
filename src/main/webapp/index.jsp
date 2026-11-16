<%@ page contentType="text/html;charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><c:set var="pageTitle" value="Healthcare, made easier"/><jsp:include page="/WEB-INF/views/common/header.jsp"/>

<section class="storefront-hero" aria-labelledby="home-title">
  <div class="storefront-banner">
    <div class="storefront-banner-copy">
      <span class="eyebrow">ONLINE MEDIC · PHARMACY &amp; CARE</span>
      <h1 id="home-title">Healthcare, made <em>easier.</em></h1>
      <p>Find everyday health essentials or book time with a doctor. Keep the next step in your care close at hand.</p>
      <div class="storefront-banner-actions"><a class="button" href="${pageContext.request.contextPath}/medicines">Browse medicines <span>→</span></a><a class="banner-text-link" href="${pageContext.request.contextPath}/doctors">Talk to a doctor <span>↗</span></a></div>
      <div class="banner-assurance"><span aria-hidden="true">✓</span> Prescription medicines are reviewed by a pharmacist</div>
    </div>
    <div class="storefront-banner-visual">
      <div class="banner-visual-circle"></div>
      <img src="${pageContext.request.contextPath}/assets/doctors/demo-doctor-01.jpg" alt="Doctor available for online consultation">
      <div class="banner-floating-card"><span class="floating-card-icon" aria-hidden="true"><img src="${pageContext.request.contextPath}/assets/illustrations/care-companion.svg" alt=""></span><span><strong>Doctor consultations</strong><small>Explore profiles and available times</small></span></div>
    </div>
  </div>
  <form class="storefront-search" method="get" action="${pageContext.request.contextPath}/medicines" role="search">
    <label for="home-medicine-search"><span class="search-glass" aria-hidden="true">⌕</span><span class="sr-only">Search the medicine catalogue</span><input id="home-medicine-search" name="q" type="search" placeholder="Search for a medicine or health product"></label>
    <button class="button" type="submit">Search</button>
  </form>
</section>

<section class="home-categories" aria-labelledby="category-title">
  <div class="store-section-heading"><div><span class="eyebrow">FIND WHAT YOU NEED</span><h2 id="category-title">Shop by category</h2></div><a class="section-link" href="${pageContext.request.contextPath}/medicines">View all medicines <span>→</span></a></div>
  <div class="home-category-grid">
    <a class="home-category" href="${pageContext.request.contextPath}/medicines?category=Vitamins%20%26%20supplements"><span class="home-category-art vitamins-art"><img src="${pageContext.request.contextPath}/assets/catalog/vitamins.jpg" alt=""></span><strong>Vitamins &amp; supplements</strong><small>Everyday wellness</small></a>
    <a class="home-category" href="${pageContext.request.contextPath}/medicines?category=Cold%20%26%20allergy"><span class="home-category-art cold-art"><img src="${pageContext.request.contextPath}/assets/catalog/cold-allergy.jpg" alt=""></span><strong>Cold &amp; allergy</strong><small>Seasonal essentials</small></a>
    <a class="home-category" href="${pageContext.request.contextPath}/medicines?category=Pain%20relief"><span class="home-category-art pain-art"><img src="${pageContext.request.contextPath}/assets/catalog/pain-relief.jpg" alt=""></span><strong>Pain relief</strong><small>Browse pain care</small></a>
    <a class="home-category" href="${pageContext.request.contextPath}/medicines?category=Skin%20care"><span class="home-category-art skin-art"><img src="${pageContext.request.contextPath}/assets/catalog/skin-treatment.jpg" alt=""></span><strong>Skin care</strong><small>Care for skin</small></a>
    <a class="home-category" href="${pageContext.request.contextPath}/medicines?category=First%20aid"><span class="home-category-art firstaid-art"><img src="${pageContext.request.contextPath}/assets/catalog/antiseptic-lotion.jpg" alt=""></span><strong>First aid</strong><small>Home essentials</small></a>
  </div>
</section>

<section class="home-services" aria-labelledby="services-title">
  <div class="store-section-heading"><div><span class="eyebrow">CARE THAT FITS YOUR DAY</span><h2 id="services-title">More ways to look after yourself</h2></div></div>
  <div class="home-service-grid">
    <a class="home-service-card service-medicine" href="${pageContext.request.contextPath}/medicines"><span class="service-symbol" aria-hidden="true">＋</span><span class="service-card-copy"><small>PHARMACY</small><strong>Browse the medicine catalogue</strong><span>See product details, availability and prescription requirements.</span><b>Explore medicines <i>→</i></b></span></a>
    <a class="home-service-card service-doctor" href="${pageContext.request.contextPath}/doctors"><span class="service-symbol" aria-hidden="true">♡</span><span class="service-card-copy"><small>DOCTOR CONSULT</small><strong>Find the right person to talk to</strong><span>Compare doctor profiles, specialities and consultation fees.</span><b>Find a doctor <i>→</i></b></span></a>
    <a class="home-service-card service-account" href="${pageContext.request.contextPath}/login"><span class="service-symbol" aria-hidden="true">▤</span><span class="service-card-copy"><small>YOUR HEALTH RECORDS</small><strong>Keep care details together</strong><span>Manage appointments, prescriptions and order updates in your account.</span><b>Sign in to your account <i>→</i></b></span></a>
  </div>
</section>

<section class="home-trust" aria-labelledby="trust-title">
  <div class="home-trust-heading"><span class="eyebrow">A CLEARER CARE JOURNEY</span><h2 id="trust-title">Thoughtful at every step</h2><p>Simple tools for everyday healthcare, with qualified professionals involved where it matters.</p></div>
  <div class="home-trust-points"><div><span aria-hidden="true">01</span><strong>Know what you’re browsing</strong><p>Product details show stock and when a prescription is required.</p></div><div><span aria-hidden="true">02</span><strong>Choose a consultation</strong><p>Review doctor profiles and available appointment times before booking.</p></div><div><span aria-hidden="true">03</span><strong>Stay up to date</strong><p>Find your care details and order progress in one account.</p></div></div>
</section>

<aside class="safety-note home-safety"><span class="icon-tile blue" aria-hidden="true">⌂</span><div><strong>Medical decisions stay with qualified professionals</strong><p>Online Medic supports communication and care coordination. It does not diagnose conditions or automatically prescribe treatment. Catalogue listings may be demonstration data; check product details and consult a pharmacist when needed.</p></div></aside>

<jsp:include page="/WEB-INF/views/common/footer.jsp"/>
