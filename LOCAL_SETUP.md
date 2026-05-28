# PDTS Local Setup Guide

This package is prepared for local PostgreSQL execution.

## Database

Recommended local credentials:

- Database: `pdts_db`
- Username: `pdts_user`
- Password: `Pdts@123`

Run only these two SQL scripts inside `pdts_db`:

1. `database/01_schema.sql`
2. `database/02_seed.sql`

Notes:

- `01_schema.sql` creates the tables/views and includes the local dashboard/delete columns plus local permissions.
- `02_seed.sql` includes the lookup/default data, admin user, and sample dashboard data.

## Run locally

From the project root:

```bash
./run-local.sh
```

Or manually:

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/pdts_db"
export DATABASE_USERNAME="pdts_user"
export DATABASE_PASSWORD="Pdts@123"
export PORT=8080
mvn clean spring-boot:run
```

Open:

```text
http://localhost:8080/login
```

Default seeded login:

```text
Username: admin001
Password: Admin@2025
```

## Note on matching the online Render system

This package is based on the ZIP provided. It is local-ready, but it can only visually match the online Render dashboard if the ZIP is the exact same commit/version deployed online.


Local schema note: 01_schema.sql includes the local dashboard/document-submission columns required by the controllers: applicant_is_deleted, applicant_deleted_at, applicant_educational_background, requirement_file_url, and requirement_storage_path.
