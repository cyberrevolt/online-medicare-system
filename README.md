# Online Medic

Online Medic is an Eclipse and Apache Tomcat web application for doctor discovery, appointment booking, consultation messaging, prescriptions, pharmacy orders, inventory, and delivery workflow. It is a Java MVC demonstration project with local demo data and simulated payments.

> **Healthcare notice:** Online Medic is a healthcare management and communication platform. Medical decisions and prescriptions are provided by qualified healthcare professionals. It does not diagnose conditions or automatically prescribe treatment.

> **Payment notice:** Demo payments are simulated records only. They do not contact a bank, card network, or payment provider and do not move real money.

## Features

- Patient registration and sign-in with PBKDF2 password hashing, session rotation, CSRF tokens, and server-side role checks.
- Doctor search and speciality filters, profiles, availability management, slot booking, simulated payment, cancellation, and demo refund records.
- Private patient-doctor appointment rooms with one-to-one text chat and WebRTC video consultation for confirmed appointments. Completed appointments close to new patient sessions; patients book another appointment to meet again.
- Doctor-authored prescriptions for non-demo profiles, prescription print view, and patient reviews.
- Medicine search, category filter, cart, reusable patient delivery-address book, demo card/COD checkout, transactional stock deduction, order history, pharmacy processing, and delivery status updates.
- Pharmacist prescription verification/rejection, supplier records and purchases, first-expiry-first-out (FEFO) batch tracking, expired-stock exclusion, low-stock/expiry alerts, admin stock adjustments, medicine creation, doctor creation, and audit records.
- Prescription-required medicines can be added to the cart; checkout checks only those items for a matching doctor-issued prescription verified by a pharmacist.
- Delivery costs ₹50 for medicine subtotals of ₹150 or less and is free when the subtotal is above ₹150.
- Patients can cancel before shipment, request an eligible return within seven days after delivery, and request full demo refunds. Pharmacists can initiate a refund after confirming a return; administrators can approve, reject, or mark order refunds processed.
- Pharmacist workspace for storefront name/image, medicine creation with photos, imported-item setup, inventory restocking, orders, and prescription review.
- Admin image uploads for doctor profiles, pharmacy branding, and medicines; doctor and medicine images appear in the public search cards and details.
- Bundled starter medicine photos from `src/main/webapp/assets/catalog`; the app seeds matching catalog rows on startup and links photos to search cards and details.
- In-app notifications, role dashboards, live database counts, 30-day admin sales/appointment summaries and top-medicine reports, H2 demo database, PostgreSQL support, and responsive layouts.
- No paid API, external payment provider, or required third-party SaaS.

## Technology and architecture

- Java 17+, Jakarta Servlet 6, JSP, JSTL 3, Tomcat 10.1+, Maven WAR, PostgreSQL 16+ or H2 for local demos.
- Plain JavaScript and CSS.

~~~text
Browser → JSP views → Servlet controllers → Service layer → DAO/JDBC → PostgreSQL or H2
~~~

Servlets are under src/main/java/com/onlinemedic/controller; business operations under service; SQL access under dao; filters and database/password utilities under filter and util. JSPs are kept in WEB-INF/views. PostgreSQL DDL is in src/main/resources/db/schema.sql.

## Demo accounts

On first startup with the default local H2 database, fictional demo accounts are inserted if no users exist. The five image-backed directory previews are also added at startup for H2 and PostgreSQL. These doctor accounts share the password `DoctorDemo123!`; other H2 demo roles use `Demo123!`.

| Role | Email |
| --- | --- |
| Admin | admin@onlinemedic.local |
| Patient | patient@onlinemedic.local |
| Seeded doctors (H2 demo) | meera@onlinemedic.local, kabir@onlinemedic.local, ananya@onlinemedic.local |
| Pharmacist | pharmacy@onlinemedic.local |
| Delivery staff | delivery@onlinemedic.local |

The PostgreSQL import script contains no demo accounts. When the app starts, it seeds only the five fictional doctor preview accounts listed below; it does not seed default patient, pharmacist, delivery, or admin logins. For a new PostgreSQL database, set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD before the first Tomcat start. The admin password must be at least 14 characters. Remove those variables after the first administrator is created. Patient accounts can self-register.

The image-backed fictional doctor preview accounts are seeded by the application on both H2 and PostgreSQL:

| Doctor | Email | Password |
| --- | --- | --- |
| Dr. Aarav Malhotra | showcase.aarav@onlinemedic.local | DoctorDemo123! |
| Dr. Kavya Iyer | showcase.kavya@onlinemedic.local | DoctorDemo123! |
| Dr. Anika Sen | showcase.anika@onlinemedic.local | DoctorDemo123! |
| Dr. Meera Kapoor | showcase.meera@onlinemedic.local | DoctorDemo123! |
| Dr. Nisha Rao | showcase.nisha@onlinemedic.local | DoctorDemo123! |

The three original H2 demo doctor accounts listed above are also normalized to `DoctorDemo123!`. Admin-created doctor accounts keep the temporary password set when the administrator created them.

## Eclipse and Tomcat setup

Requirements: JDK 17 or newer, Maven 3.8+, Eclipse with Maven support, Apache Tomcat 10.1, and PostgreSQL 16+ only if not using the local H2 demo.

1. In Eclipse select **File → Import → Maven → Existing Maven Projects**.
2. Select this project folder and finish the import. Use **Maven → Update Project** if dependencies need refreshing.
3. In **Window → Preferences → Java → Installed JREs**, select JDK 17+.
4. Add a Tomcat 10.1 server in the **Servers** view and add the online-medic web project.
5. With DATABASE_URL unset, Tomcat uses local H2 at ./data/online_medic. Startup creates the schema and demo data on first run.
6. Right-click the project and select **Run As → Run on Server**.

### PostgreSQL configuration

For a manual import, create a new, empty PostgreSQL database and run `database/online_medic_postgresql.sql` against it. The script creates the tables and indexes and inserts the required role rows; it contains no demo accounts or passwords. Alternatively, the application can create the schema at first startup if its database user has permission to create tables, indexes, and constraints. Add these environment variables to the Tomcat launch configuration:

~~~text
DATABASE_URL=jdbc:postgresql://localhost:5432/online_medic
DATABASE_USER=online_medic
DATABASE_PASSWORD=<your local secret>
BOOTSTRAP_ADMIN_EMAIL=<admin email, first start only>
BOOTSTRAP_ADMIN_PASSWORD=<unique password of at least 14 characters, first start only>
~~~

Tomcat creates the schema from db/schema.sql and applies the idempotent starter catalogue from db/catalog_seed.sql. To import it manually, run `database/online_medic_postgresql.sql` first, then `database/online_medic_catalog_seed.sql`. The imported image-only products have no configured price or stock and are blocked from checkout until the pharmacy enters verified sale details. Demo patient, doctor, pharmacy, and delivery users are not created in PostgreSQL. Never commit database or bootstrap credentials. The .env.example file is illustrative; Eclipse/Tomcat does not load it automatically.

## Maven build and WAR

~~~bash
mvn clean package
~~~

Run tests only with mvn test. The WAR is target/online-medic.war. Deploy it to Tomcat 10.1+ or run the Maven project from Eclipse's Tomcat server.

## End-to-end demo workflow

1. Sign in as the patient or register a patient account.
2. Find a doctor, open the profile, and choose an available slot.
3. Select success or failure on the local demo payment page. Success confirms the visit; failure cancels the temporary reservation and releases the slot.
4. Sign in as that doctor and open the same appointment room. The patient can start a one-to-one video call and the doctor can accept it; both accounts can also use private text chat. A completed appointment closes the patient's room, so book another appointment to start a new visit. Real doctor profiles can issue a prescription and mark the visit complete; fictional preview accounts are for booking and chat/video walkthroughs only and cannot prescribe.
5. Sign in as the patient to view the prescription and review the completed visit. Completed visits no longer offer an appointment room. Add available medicines to the cart; only items marked prescription-required need a matching prescription verified by a pharmacist at checkout.
6. Sign in as pharmacist to update the storefront, configure medicine listings, restock inventory, review prescriptions, and move orders through processing, packing, and shipping. Sign in as delivery staff to mark them out for delivery and delivered.
7. For a delivered order, the patient can request a return within seven days. The pharmacist reviews it, arranges pickup, and confirms receipt; either the patient or pharmacist can then initiate a full demo refund. Admin reviews the request and may approve, reject, or mark it processed. Pre-shipment cancellation of a paid order also creates a refund request.
8. Sign in as admin to view counts and stock alerts, add medicines or doctors, restock, manage the order queue, and review refunds.

## Database and security notes

- The schema uses primary and foreign keys, checks, unique constraints, and indexes.
- Appointment booking locks its availability row while checking and reserving a slot. Failed and cancelled bookings release it.
- Checkout locks medicine rows, checks quantity and prescription restrictions, then creates the address, order, items, delivery assignment, payment, inventory transactions, notifications, and audit event in one transaction.
- Passwords use PBKDF2-HMAC-SHA256 with a random per-password salt. SQL uses prepared statements. Mutating forms require CSRF tokens; JSP text output is escaped with JSTL.
- Uploaded images accept JPEG or PNG up to 5 MB, are decoded and re-encoded, and are stored outside the deployed web application in `~/OnlineMedicData/uploads` by default. Set `UPLOAD_DIR` on the Tomcat launch configuration to choose another persistent folder.
- Session cookies are HTTP-only and responses set basic security headers. Put a production deployment behind HTTPS and configure secure cookies at the Tomcat or proxy layer.
- Demo records are fictional. Do not enter real patient data into the demo installation.

## Known limitations

- Automated database workflow tests run against H2. The application was smoke-tested on embedded Tomcat 10.1.61. Validate the schema against your target PostgreSQL installation before deployment.
- Administrators can add doctors, pharmacists, delivery staff, and medicines after the initial administrator is provisioned.
- The demo uses one shared pharmacy storefront and catalogue. Pharmacists manage that shared storefront; separate inventory per pharmacy location is not modeled.
- Cart contents are session-scoped. Patient profiles include reusable addresses; addresses referenced by prior orders cannot be edited or deleted so historical delivery and invoice details remain stable.
- Prescription-only medicines are blocked from demo checkout pending pharmacist verification. The current prescription editor stores one medicine line per appointment.
- Prescription matching at checkout uses the catalog medicine name against a patient-owned, pharmacist-verified doctor prescription; product names should match.
- Expired stock is excluded from sale counts and checkout, and staff have supplier-receiving and expiry-watch screens. Staff must manually quarantine expired goods; the demo does not automate disposal or supplier credit. Appointment refund records are stored, but they do not use the order-refund admin queue. Order refunds are full-payment demo records; marking one processed does not move real money.
- Doctors can add, edit, and remove future availability windows. Windows with active appointments are locked until those appointments are cancelled. Fictional preview doctors receive simulated appointment slots automatically. Profile photos and file uploads are stored by the application.
- Confirmed appointments include private text chat and one-to-one WebRTC audio/video between the booked patient and doctor. Camera and microphone permissions are requested only after the user starts or accepts a call. Marking an appointment complete, cancelling it, or recording a no-show ends any active call and removes its signaling; the completed patient appointment room is disabled. The demo uses a public STUN server and does not include a TURN relay, so restrictive firewalls or some NAT networks may prevent a direct connection. Video requires HTTPS outside localhost; the application stores only short-lived offer/answer/ICE signaling and deletes it when a call ends or expires. SMTP, SMS, maps, and cloud storage are not included.
- Admin reports include live counters and a last-30-days operational summary with top medicines. Administrators can edit, pause, and reset staff accounts; pausing delivery staff is blocked while they hold active deliveries. Interactive charts, large-list pagination, and patient account administration remain future work.

## Project layout

~~~text
pom.xml
src/main/java/com/onlinemedic/
  controller/   Servlet endpoints
  dao/          JDBC queries and transactions
  filter/       Authentication and security headers
  model/        Domain and view models
  service/      Authentication, payment, order, admin, dashboard logic
  util/         Database, password hashing, application initialization
src/main/resources/db/schema.sql
src/main/resources/db/catalog_seed.sql
src/main/webapp/assets/catalog/   Bundled pharmacy and medicine images
src/main/webapp/
  WEB-INF/views/ JSP pages and shared fragments
  assets/css/    Responsive design system
  assets/js/     Navigation and Fetch consultation chat
  errors/        Friendly error pages
src/test/java/com/onlinemedic/CoreWorkflowTest.java
~~~

## Team Git workflow

Use short feature branches, keep commits focused, and do not check in .env, database files, uploaded private records, or generated target/ output. Review schema changes and run mvn clean package before merging.
