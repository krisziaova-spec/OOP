# PDTS — PUPOUS Document Tracking System

**Academic Project | Information Management / Document Tracking System**

PDTS, or **PUPOUS Document Tracking System**, is a web-based document tracking prototype for managing incoming applicant documents, application records, document status updates, staff users, and audit logs.

> **Privacy note:** This README uses sample placeholders only. Do not publish real passwords, API keys, private database URLs, applicant data, staff accounts, or production credentials.

---

## Overview

Many schools still receive admission documents through manual forms and physical document submissions. PDTS helps encode and track those incoming documents in a structured database so authorized staff can monitor the status of each applicant requirement.

The system supports:

- Applicant record management
- Application/admission record tracking
- Requirement and document status monitoring
- Staff login and role-based access
- Audit/activity logs
- Public applicant status lookup using a reference/tracking token
- Email notification support through environment variables
- Deployment-ready configuration using Docker

---

## Main Features

- **Staff Authentication** — login-based access for authorized staff users.
- **Role-Based Access Control** — different permissions for admission staff, admin, and head admission roles.
- **Applicant Management** — create, view, search, and update applicant records.
- **Application Tracking** — connect each applicant to an admission/application record.
- **Requirement Monitoring** — track document requirements such as received, pending, rejected, or for resubmission.
- **Tracking Reference** — generate and use tracking numbers/tokens for applicant status lookup.
- **Email Notifications** — optional email support for applicant tokens and document status updates.
- **User Management** — activate/deactivate staff accounts.
- **Activity Logs** — monitor actions performed inside the system.
- **Reports and Lookup Pages** — support administrative document monitoring.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot |
| Web/UI | Thymeleaf, HTML5, CSS3, JavaScript |
| Security | Spring Security, BCrypt |
| Database | PostgreSQL for deployment; MySQL-compatible scripts may be used for Information Management documentation |
| Persistence | Spring Data JPA, Hibernate, JdbcTemplate |
| Email | Resend API or configured email service |
| Build Tool | Maven |
| Deployment | Docker, Render or similar hosting platform |

---

## Project Structure

```text
pdts-IM-main/
├── .github/
│   └── workflows/
│       └── daily-ping.yml              # Optional scheduled ping for hosted deployment
├── Dockerfile                          # Container build configuration
├── README.md                           # Main project documentation
├── database/
│   ├── 01_schema.sql                   # Main database schema
│   ├── 02_seed.sql                     # Required seed/reference data
│   ├── 03_sample_data.sql              # Optional sample data for testing
│   └── mysql_original/                 # MySQL reference scripts, if included
├── pom.xml                             # Maven configuration
└── src/
    └── main/
        ├── java/com/pdts/
        │   ├── PdtsApplication.java    # Spring Boot entry point
        │   ├── config/                 # Security and configuration classes
        │   ├── controller/             # MVC and REST controllers
        │   ├── model/                  # Entity models
        │   ├── repository/             # Data repositories
        │   └── service/                # Business logic services
        └── resources/
            ├── application.properties
            ├── application-mysql.properties
            ├── static/                 # CSS, JavaScript, images
            └── templates/              # Thymeleaf pages
```

> If `pdts_db/pdts_db.sqlproj` is still present, it is a Microsoft SQL Server project file and is not required for the Spring Boot/PostgreSQL/MySQL setup unless specifically needed for a separate SQL Server tool workflow.

---

## Prerequisites

Install the following before running the project locally:

- Java JDK 21
- Maven 3.9 or later
- PostgreSQL 13+ for the default deployed setup  
  **or** MySQL/MariaDB if using the MySQL version for Information Management documentation
- Git
- VS Code, IntelliJ IDEA, NetBeans, or another Java IDE
- pgAdmin, psql, phpMyAdmin, or another database management tool

---

## Local Setup

### 1. Clone the Repository

```bash
git clone <your-repository-url>
cd pdts-IM-main
```

### 2. Create the Database

For PostgreSQL:

```sql
CREATE DATABASE pdts_db;
```

For MySQL/MariaDB:

```sql
CREATE DATABASE pdts_db;
USE pdts_db;
```

### 3. Run the SQL Scripts

Run the SQL files in this order:

```text
database/01_schema.sql
database/02_seed.sql
database/03_sample_data.sql   optional
```

The first two scripts are required. The third script is only for testing and demonstration data.

---

## Environment Variables

Configure these values locally or in your hosting platform. Use sample values only in public documentation.

| Variable | Required | Sample Value |
|---|---:|---|
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/pdts_db` |
| `DATABASE_USERNAME` | Yes | `your_database_username` |
| `DATABASE_PASSWORD` | Yes | `your_database_password` |
| `RESEND_API_KEY` | Required only for email | `your_resend_api_key` |
| `RESEND_FROM_EMAIL` | Optional | `PDTS Registrar <no-reply@example.edu>` |
| `PORT` | Optional | `8080` locally or `10000` for hosted deployment |

### Windows PowerShell Example

```powershell
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/pdts_db"
$env:DATABASE_USERNAME="your_database_username"
$env:DATABASE_PASSWORD="your_database_password"
$env:RESEND_API_KEY="your_resend_api_key"
$env:PORT="8080"
```

### macOS/Linux Example

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/pdts_db"
export DATABASE_USERNAME="your_database_username"
export DATABASE_PASSWORD="your_database_password"
export RESEND_API_KEY="your_resend_api_key"
export PORT="8080"
```

---

## Run the Application

From the project root folder:

```bash
mvn spring-boot:run
```

Open the local application:

```text
http://localhost:8080/login
```

If you use a different port, replace `8080` with your configured port.

---

## Demo Login

For public repositories, do **not** publish real usernames or passwords.

Use one of these safer options:

```text
Username: sample_admin
Password: ChangeMe123!
```

or

```text
See database/02_seed.sql for sample development credentials.
Change all sample passwords before any real deployment.
```

> Never use sample credentials in production.

---

## Important Routes

| Route | Purpose | Access |
|---|---|---|
| `/login` | Staff login page | Public |
| `/dashboard` | Staff dashboard | Staff only |
| `/applicants` | Applicant management | Staff only |
| `/requirements` | Document/requirement tracking | Staff only |
| `/users` | Staff user management | Authorized staff only |
| `/logs` | Activity log viewer | Authorized staff only |
| `/email-notifications` | Manual email notification page | Staff only |
| `/tracking-lookup` | Tracking lookup utility | Staff only |
| `/reports` | Reports page | Staff only |
| `/` | Applicant status lookup landing page | Public |
| `/api/portal/verify` | Token/reference verification endpoint | Public |
| `/api/portal/status` | Document status endpoint | Public |

---

## User Roles

| Role | General Purpose |
|---|---|
| Admission Personnel | Encodes applicant records and uploads or updates document requirements. |
| Admin | Reviews documents, manages rejection reasons, and assists with applicant tracking tokens. |
| Head Admission | Has full system access, including staff management and activity logs. |

---

## Database Design Summary

The database is designed to support a normalized document tracking process.

Main entities include:

- Applicant
- Previous Education
- Emergency Contact
- Application
- Requirement/Document Record
- Requirement Type
- Requirement Status
- Rejection Reason
- Staff User
- Role and Permission
- Tracking Sequence
- Applicant Access Token
- Token Access Log
- User Activity Log

The design separates applicant information, application transactions, document requirements, lookup values, and audit logs to reduce redundancy and improve data integrity.

---

## Information Management Notes

For Information Management documentation, the database may be presented in MySQL format using:

- Normalization from UNF to 3NF
- Entity Relationship Diagram
- Data Dictionary
- SQL schema and seed data
- Simple, moderate, and difficult SQL queries
- Manual form-to-database mapping

The system follows this general process:

```text
Manual Incoming Form
        ↓
Staff Encoding
        ↓
Applicant and Application Records
        ↓
Requirement Tracking
        ↓
Status Update / Review
        ↓
Applicant Status Lookup and Reports
```

---

## Email Configuration

Email functionality is configured using environment variables. Do not hard-code API keys or email passwords in the source code.

Example property placeholders:

```properties
resend.api-key=${RESEND_API_KEY}
resend.from-email=${RESEND_FROM_EMAIL:PDTS Registrar <no-reply@example.edu>}
```

Email may be used for:

- Applicant tracking token delivery
- Document status updates
- Rejection or resubmission notices
- Manual staff-triggered notifications

---

## Running with Docker

Build the Docker image:

```bash
docker build -t pdts .
```

Run the container:

```bash
docker run --rm -p 10000:10000 \
  -e PORT=10000 \
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/pdts_db" \
  -e DATABASE_USERNAME="your_database_username" \
  -e DATABASE_PASSWORD="your_database_password" \
  -e RESEND_API_KEY="your_resend_api_key" \
  pdts
```

Open:

```text
http://localhost:10000/login
```

---

## Deployment Notes

For hosted deployment, configure environment variables through the hosting provider dashboard.

Required values:

```text
DATABASE_URL=<your_database_connection_url>
DATABASE_USERNAME=<your_database_username>
DATABASE_PASSWORD=<your_database_password>
RESEND_API_KEY=<your_email_api_key>
RESEND_FROM_EMAIL=<your_verified_sender_email>
PORT=10000
```

If a scheduled ping workflow is used, replace the URL with your own hosted application URL:

```text
https://your-app-name.onrender.com/login
```

---

## Common Issues and Fixes

| Problem | Likely Cause | Fix |
|---|---|---|
| `mvn` is not recognized | Maven is not installed or not in PATH | Install Maven and restart the terminal. |
| Database connection error | Missing or incorrect environment variables | Check `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. |
| Password authentication failed | Incorrect database password | Verify the database username and password. |
| Table or relation does not exist | SQL scripts were not executed | Run the schema and seed scripts in order. |
| Login fails | Sample user was not seeded or is inactive | Check the seed data and user status. |
| Port already in use | Another app is using the same port | Change the `PORT` value or stop the other app. |
| Email does not send | Missing email configuration | Set a valid API key and sender email. |
| Uploaded files disappear after redeployment | Temporary hosting storage | Use persistent storage or external object storage for production. |

---

## Security and Privacy Checklist

Before pushing to GitHub, confirm that the repository does **not** contain:

- Real database passwords
- Real API keys
- `.env` files
- Production database URLs
- Real applicant/student personal information
- Real staff usernames and passwords
- Private deployment URLs, unless the site is intended to be public
- Email passwords or SMTP credentials
- Secret tokens or generated access keys

Recommended files to keep public-safe:

```text
README.md
database/01_schema.sql
database/02_seed.sql        sample credentials only
database/03_sample_data.sql sample data only
src/
pom.xml
Dockerfile
```

---

## Academic Use Notice

This project was developed as an academic prototype for document tracking and Information Management requirements. Before using the system in a real production environment, review and strengthen security, privacy, validation, backup, audit logging, and file storage practices.


Local schema note: 01_schema.sql includes the local dashboard/document-submission columns required by the controllers: applicant_is_deleted, applicant_deleted_at, applicant_educational_background, requirement_file_url, and requirement_storage_path.
