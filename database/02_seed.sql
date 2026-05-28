-- ============================================================
-- PDTS — Seed Data | PostgreSQL
-- Run after 01_schema.sql
-- ============================================================

-- ============================================================
-- Curriculum types
-- ============================================================

INSERT INTO educational_background_category (
    category_id,
    category_name,
    category_code,
    category_description,
    category_is_active,
    category_created_at
) VALUES
('OLD-001', 'Old Curriculum', 'OLD', 'Pre-K12 traditional high school program.', 1, NOW()),
('SHS-002', 'Senior High School', 'SHS', 'K-12 SHS graduate (Grades 11-12).', 1, NOW()),
('ALS-003', 'Alternative Learning System', 'ALS', 'Non-formal ALS / A&E passers.', 1, NOW()),
('COL-004', 'College Undergraduate', 'COL', 'Tertiary-level degree program applicants.', 1, NOW()),
('TVT-005', 'TVET', 'TVET', 'Technical-Vocational Education and Training graduates.', 1, NOW())
ON CONFLICT (category_id) DO NOTHING;

-- ============================================================
-- Roles
-- ============================================================

INSERT INTO role (role_id, role_name, role_description) VALUES
(1, 'Admission Personnel', 'Can create and update applicant profiles and upload documents.'),
(2, 'Admin', 'Can change document statuses and manage rejection reasons.'),
(3, 'Head Admission', 'Full system access including user management and logs.')
ON CONFLICT (role_name) DO NOTHING;

-- ============================================================
-- Permissions
-- ============================================================

INSERT INTO permission (permission_id, permission_name, permission_description) VALUES
(1, 'UPLOAD_DOCUMENT', 'Upload scanned document images for applicants.'),
(2, 'REJECT_DOCUMENT', 'Reject a submitted document with a reason.'),
(3, 'RECEIVE_DOCUMENT', 'Mark a document as Verified/Received.'),
(4, 'VIEW_LOGS', 'View the system activity audit trail.'),
(5, 'MANAGE_USERS', 'Create, deactivate, and manage staff accounts.'),
(6, 'MANAGE_REASONS', 'Add, edit, or deactivate rejection reasons.'),
(7, 'REVOKE_TOKEN', 'Revoke or regenerate applicant access tokens.'),
(8, 'FILTER_SEARCH', 'Use the advanced filter and search panel.'),
(9, 'MANAGE_SETTINGS', 'Manage system configuration, campuses, programs, and master data.')
ON CONFLICT (permission_name) DO NOTHING;

-- ============================================================
-- Role-permission mappings
-- ============================================================

INSERT INTO role_permission (role_id, permission_id) VALUES
-- Admission Personnel: limited student-assistant/document encoding access
(1,1),(1,8),
-- Admin: registrar office staff operational access; no users/settings/logs control
(2,1),(2,2),(2,3),(2,6),(2,7),(2,8),
-- Head Admission: full system access
(3,1),(3,2),(3,3),(3,4),(3,5),(3,6),(3,7),(3,8),(3,9)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================
-- Application statuses
-- ============================================================

INSERT INTO application_status (
    application_status_id,
    application_status_name,
    application_status_color
) VALUES
(1, 'Pending', '#FFA500'),
(2, 'Under Review', '#2E75B6'),
(3, 'Approved', '#28A745'),
(4, 'Rejected', '#DC3545')
ON CONFLICT (application_status_name) DO NOTHING;

-- ============================================================
-- Requirement statuses
-- ============================================================

INSERT INTO requirement_status (
    status_id,
    requirement_status_name,
    requirement_status_color,
    requirement_status_desc,
    is_final
) VALUES
(1, 'Pending', '#FFA500', 'Document uploaded; awaiting Registrar initial action.', 0),
(2, 'Under Review', '#2E75B6', 'Registrar is actively reviewing the document.', 0),
(3, 'Verified/Received', '#28A745', 'Document fully verified and accepted into the official record.', 1),
(4, 'Rejected', '#DC3545', 'Document denied; rejection reason recorded and emailed.', 1),
(5, 'For Resubmission', '#C8A951', 'Flagged for corrected resubmission; guidance notes attached.', 0)
ON CONFLICT (requirement_status_name) DO NOTHING;

-- ============================================================
-- Requirement types
-- ============================================================

INSERT INTO requirement_type (
    type_id,
    requirement_type_name,
    type_is_active
) VALUES
(1, 'PSA Birth Certificate', 1),
(2, 'Form 137 / Form 138', 1),
(3, 'Transcript of Records (TOR)', 1),
(4, 'Diploma (Certified Copy)', 1),
(5, '2x2 ID Pictures (4 pcs)', 1),
(6, 'X-Ray Result (within 6 months)', 1),
(7, 'Certificate of Good Moral Character', 1),
(8, 'NBI / Police Clearance', 1),
(9, 'Letter of Endorsement', 1),
(10, 'ALS Certificate of Rating', 1),
(11, 'TVET National Certificate (NC II/III)', 1),
(12, 'PSA Marriage Certificate', 1)
ON CONFLICT (requirement_type_name) DO NOTHING;


INSERT INTO requirement_due_rule (
    requirement_type_id,
    first_due_days,
    resend_after_days
)
SELECT
    type_id,
    30,
    30
FROM requirement_type
WHERE COALESCE(type_is_active, 1) = 1
ON CONFLICT (requirement_type_id) DO UPDATE
SET first_due_days = 30,
    resend_after_days = 30,
    rule_is_active = 1;

-- ============================================================
-- Rejection reasons
-- ============================================================

INSERT INTO rejection_reason (
    rejection_reason_name,
    rejection_reason_description,
    rejection_reason_is_active
) VALUES
('Document Blurry', 'The uploaded document image is too blurry to be legible. Please resubmit a clear, high-resolution scan.', 1),
('Expired Certificate', 'The submitted certificate or clearance has expired. Please provide a document issued within the last 6 months.', 1),
('Wrong Document Type', 'The uploaded file does not match the required document type. Please upload the correct document.', 1),
('Incomplete Document', 'The submitted document appears to be incomplete or is missing pages. Please resubmit the complete document.', 1),
('Photo Background Invalid', 'The ID photo background must be plain white. Colored or patterned backgrounds are not accepted.', 1),
('Unreadable File Format', 'The file format is not supported or is corrupted. Please resubmit as a clear JPEG or PDF.', 1),
('Not PSA-Authenticated', 'The submitted civil registry document is not PSA-authenticated. Please submit a PSA-authenticated copy.', 1),
('Old NSO Copy', 'The submitted document is an old NSO copy. Please submit the updated PSA-issued document.', 1),
('Blurred/Unrecognized Registry Number', 'The registry number is blurred, unreadable, or cannot be verified. Please submit a clearer copy.', 1),
('Name Mismatch', 'The name on the submitted document does not match the applicant record. Please submit a corrected document or supporting proof.', 1),
('Birth Date Mismatch', 'The birth date on the submitted document does not match the applicant record. Please submit the correct document.', 1)
ON CONFLICT (rejection_reason_name) DO NOTHING;

-- ============================================================
-- Programs
-- ============================================================

INSERT INTO program (
    program_name,
    program_code,
    program_is_active
) VALUES
('Bachelor of Science in Information Technology', 'BSIT', 1),
('Bachelor of Science in Business Administration', 'BSBA', 1),
('Bachelor of Science in Office Administration', 'BSOA', 1),
('Bachelor of Science in Tourism Management', 'BSTM', 1),
('Bachelor of Science in Entrepreneurship', 'BSENT', 1),
('Bachelor of Science in Criminology', 'BSCrim', 1),
('Bachelor of Science in Nursing', 'BSN', 1),
('Bachelor of Technology and Livelihood Education', 'BTLE', 1),
('NC II — Computer Hardware Servicing', 'NC2-CHS', 1),
('NC II — Bread and Pastry Production', 'NC2-BPP', 1)
ON CONFLICT (program_code) DO NOTHING;

-- ============================================================
-- Campuses
-- ============================================================

INSERT INTO campus (
    campus_name,
    campus_address,
    campus_is_active
) VALUES
('PUP Main Campus — Sta. Mesa, Manila', 'Anonas St., Sta. Mesa, Manila, 1008', 1),
('PUP Open University System', 'Anonas St., Sta. Mesa, Manila, 1008', 1),
('PUP Paranaque Campus', 'Dr. A. Santos Ave., Sucat, Paranaque City', 1),
('PUP Lopez, Quezon', 'Quezon Province Campus', 1),
('PUP San Juan Campus', 'San Juan, Metro Manila', 1)
ON CONFLICT (campus_name) DO NOTHING;

-- ============================================================
-- System settings
-- ============================================================

INSERT INTO system_setting (
    setting_key,
    setting_value,
    setting_label,
    setting_type,
    setting_options,
    setting_is_active,
    setting_updated_at
) VALUES
('academic_year', '2025-2026', 'Academic Year', 'text', NULL, 1, NOW()),
('current_semester', 'First Semester', 'Current Semester', 'select', 'First Semester,Second Semester,Summer', 1, NOW()),
('max_photo_upload_kb', '300', 'Max Photo Upload Size KB', 'number', NULL, 1, NOW()),
('email_reminder_day', 'Monday', 'Email Reminder Day', 'select', 'Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday', 1, NOW()),
('portal_status', 'OPEN', 'Applicant Portal Status', 'select', 'OPEN,CLOSED', 1, NOW()),
('registrar_email', 'admin@pup.edu.ph', 'Registrar Email Address', 'email', NULL, 1, NOW()),
('school_year_label', 'AY 2025-2026', 'School Year Display Label', 'text', NULL, 1, NOW())
ON CONFLICT (setting_key) DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    setting_label = EXCLUDED.setting_label,
    setting_type = EXCLUDED.setting_type,
    setting_options = EXCLUDED.setting_options,
    setting_is_active = EXCLUDED.setting_is_active,
    setting_updated_at = NOW();

-- ============================================================
-- Tracking sequences
-- ============================================================

INSERT INTO tracking_sequences (
    tracking_sequences_entity_type,
    tracking_sequences_prefix,
    tracking_sequences_last_sequence,
    tracking_sequences_current_year
) VALUES
('student', 'STU', 0, EXTRACT(YEAR FROM CURRENT_DATE)::INT),
('document', 'DOC', 0, EXTRACT(YEAR FROM CURRENT_DATE)::INT)
ON CONFLICT (tracking_sequences_entity_type) DO NOTHING;

-- ============================================================
-- Curriculum requirements
-- ============================================================

INSERT INTO curriculum_requirement (category_id, type_id, is_mandatory) VALUES
('OLD-001',1,1),('OLD-001',2,1),('OLD-001',5,1),('OLD-001',6,1),('OLD-001',7,1),('OLD-001',8,1),
('SHS-002',1,1),('SHS-002',2,1),('SHS-002',4,1),('SHS-002',5,1),('SHS-002',6,1),('SHS-002',7,1),('SHS-002',8,1),
('ALS-003',1,1),('ALS-003',5,1),('ALS-003',10,1),('ALS-003',8,1),
('COL-004',1,1),('COL-004',3,1),('COL-004',4,1),('COL-004',5,1),('COL-004',6,1),('COL-004',7,1),('COL-004',8,1),('COL-004',9,1),
('TVT-005',1,1),('TVT-005',5,1),('TVT-005',11,1),('TVT-005',8,1),('TVT-005',7,1)
ON CONFLICT (category_id, type_id) DO NOTHING;

-- ============================================================
-- Default master admin account
-- Username: admin001
-- Password: Admin@2025
-- ============================================================

INSERT INTO app_user (
    user_last_name,
    user_first_name,
    user_middle_name,
    role_id,
    user_email_address,
    user_password_hash,
    user_username,
    user_is_active
) VALUES (
    'Administrator',
    'System',
    NULL,
    3,
    'admin@pup.edu.ph',
    '{noop}Admin@2025',
    'admin001',
    1
)
ON CONFLICT (user_username) DO NOTHING;

-- =====================================================
-- SAMPLE DATA FOR LOCAL DASHBOARD / TESTING
-- Merged from the previous 03_sample_data.sql so only two SQL files are needed.
-- =====================================================
-- Optional sample data for dashboard and CRUD testing.
-- Run this after 01_schema.sql and 02_seed.sql while connected to pdts_db.

INSERT INTO applicant (
    applicant_first_name, applicant_middle_name, applicant_last_name,
    applicant_sex, applicant_civil_status, applicant_house_number_street,
    applicant_barangay, applicant_city_municipality, applicant_province,
    applicant_region, applicant_zip_code, applicant_birth_date,
    applicant_email_address, applicant_contact_number,
    educational_background_category_id, applicant_enrollment_status, user_id
) VALUES
('Juan', 'Santos', 'Dela Cruz', 1, 1, '123 Mabini Street', 'Barangay 1', 'Manila', 'Metro Manila', 'NCR', '1008', '2003-05-15', 'juan.delacruz@example.com', '09171234567', 'SHS-002', 'continuing', 1),
('Maria', 'Reyes', 'Santos', 2, 1, '45 Bonifacio Avenue', 'Barangay 2', 'Quezon City', 'Metro Manila', 'NCR', '1100', '2002-09-21', 'maria.santos@example.com', '09181234567', 'COL-004', 'continuing', 1)
ON CONFLICT DO NOTHING;

INSERT INTO application (
    applicant_id, program_id, campus_id, application_status_id,
    application_date, application_semester, application_academic_year,
    application_reference_number
)
SELECT applicant_id, 1, 2, 1, CURRENT_DATE, 'First Semester', '2026-2027', 'APP-2026-' || LPAD(applicant_id::TEXT, 4, '0')
FROM applicant
WHERE applicant_email_address IN ('juan.delacruz@example.com', 'maria.santos@example.com')
ON CONFLICT (application_reference_number) DO NOTHING;

INSERT INTO requirement (
    application_id, requirement_type_id, requirement_status_id,
    requirement_tracking_no, requirement_file_name, requirement_image_path,
    requirement_uploaded_by_user_id
)
SELECT a.application_id, 1, 1, 'DOC-2026-' || LPAD(a.application_id::TEXT, 4, '0'), 'birth_certificate.pdf', 'uploads/birth_certificate.pdf', 1
FROM application a
WHERE a.application_reference_number LIKE 'APP-2026-%'
ON CONFLICT (application_id, requirement_type_id) DO NOTHING;

-- ============================================================
-- PDTS Demo Data: 15 dummy Student/Applicant records
-- Purpose: Prototype dashboard/UI testing with more students
-- Safe to run on an existing database. Does not modify schema.
-- NOTE: Your actual document table is named "requirement".
-- ============================================================

-- SAFETY BACKUP: run once. These backup tables are only created if they do not already exist.
CREATE TABLE IF NOT EXISTS demo_backup_applicant AS
SELECT * FROM applicant;

CREATE TABLE IF NOT EXISTS demo_backup_application AS
SELECT * FROM application;

CREATE TABLE IF NOT EXISTS demo_backup_requirement AS
SELECT * FROM requirement;

CREATE TABLE IF NOT EXISTS demo_backup_emergency_contact AS
SELECT * FROM applicant_emergency_contact;

BEGIN;

-- Ensure the latest Admission Status values exist.
INSERT INTO application_status (
    application_status_id,
    application_status_name,
    application_status_color
) VALUES
    (1, 'Pending', '#FFA500'),
    (2, 'Temporarily Enrolled', '#C8A951'),
    (3, 'Enrolled', '#28A745'),
    (4, 'Non-Compliant', '#DC3545'),
    (5, 'Did Not Continue', '#6B7280'),
    (6, 'Cancelled', '#991B1B')
ON CONFLICT (application_status_id) DO UPDATE
SET application_status_name = EXCLUDED.application_status_name,
    application_status_color = EXCLUDED.application_status_color;

SELECT setval(
    pg_get_serial_sequence('application_status', 'application_status_id'),
    GREATEST((SELECT MAX(application_status_id) FROM application_status), 6),
    true
);

-- ============================================================
-- 1) Insert / update 15 demo applicants
-- ============================================================
WITH demo_applicants AS (
    SELECT * FROM (VALUES
        (1,  'Kriszia',   NULL::VARCHAR, 'Pamintuan', 2, 1, '101 Mabini St.',       'Barangay 1',       'Manila',          'Metro Manila',      'NCR',                           '1008', DATE '2004-01-12', 'kriszia.pamintuan@pdts-demo.test',  '09170000001', 'SHS-002', 'BSIT',    'PUP Open University System',          3),
        (2,  'Joan',      NULL::VARCHAR, 'Balakwit',  2, 1, '22 Luna Road',         'Poblacion',        'Baguio City',     'Benguet',           'CAR',                           '2600', DATE '2003-03-08', 'joan.balakwit@pdts-demo.test',      '09170000002', 'ALS-003', 'BSBA',    'PUP Main Campus — Sta. Mesa, Manila', 2),
        (3,  'Jonathan',  NULL::VARCHAR, 'San Pedro', 1, 1, '34 Rizal Avenue',      'San Vicente',      'Laoag City',      'Ilocos Norte',      'Region I - Ilocos Region',       '2900', DATE '2002-07-19', 'jonathan.sanpedro@pdts-demo.test',   '09170000003', 'COL-004', 'BSOA',    'PUP Paranaque Campus',               2),
        (4,  'Catherine', NULL::VARCHAR, 'Gravoso',   2, 1, '78 Bonifacio St.',     'Centro',           'Tuguegarao City', 'Cagayan',           'Region II - Cagayan Valley',     '3500', DATE '2004-11-04', 'catherine.gravoso@pdts-demo.test',   '09170000004', 'TVT-005', 'BSTM',    'PUP Lopez, Quezon',                  3),
        (5,  'Gracheil',  NULL::VARCHAR, 'Cioco',     2, 1, '15 Burgos St.',        'Maligaya',         'San Fernando',    'Pampanga',          'Region III - Central Luzon',     '2000', DATE '2003-09-22', 'gracheil.cioco@pdts-demo.test',      '09170000005', 'OLD-001', 'BSENT',   'PUP San Juan Campus',                1),
        (6,  'Emma',      NULL::VARCHAR, 'Stone',     2, 1, '19 Emerald Lane',      'Mayapa',           'Calamba City',    'Laguna',            'Region IV-A - CALABARZON',       '4027', DATE '2002-05-02', 'emma.stone@pdts-demo.test',          '09170000006', 'SHS-002', 'BSCrim',  'PUP Open University System',          2),
        (7,  'Ryan',      NULL::VARCHAR, 'Gosling',   1, 1, '88 Sunset Drive',      'San Pedro',        'Calapan City',    'Oriental Mindoro',  'MIMAROPA Region',                '5200', DATE '2001-12-17', 'ryan.gosling@pdts-demo.test',        '09170000007', 'ALS-003', 'BSN',     'PUP Main Campus — Sta. Mesa, Manila', 3),
        (8,  'Zendaya',   NULL::VARCHAR, 'Coleman',   2, 1, '27 Orchid Street',     'Daraga',           'Legazpi City',    'Albay',             'Region V - Bicol Region',        '4500', DATE '2004-02-28', 'zendaya.coleman@pdts-demo.test',     '09170000008', 'COL-004', 'BTLE',    'PUP Paranaque Campus',               4),
        (9,  'Chris',     NULL::VARCHAR, 'Evans',     1, 1, '40 Liberty Avenue',    'Jaro',             'Iloilo City',     'Iloilo',            'Region VI - Western Visayas',    '5000', DATE '2003-06-13', 'chris.evans@pdts-demo.test',         '09170000009', 'TVT-005', 'NC2-CHS', 'PUP Lopez, Quezon',                  5),
        (10, 'Scarlett',  NULL::VARCHAR, 'Johansson', 2, 1, '55 Magnolia St.',      'Lahug',            'Cebu City',       'Cebu',              'Region VII - Central Visayas',   '6000', DATE '2002-10-30', 'scarlett.johansson@pdts-demo.test',  '09170000010', 'OLD-001', 'NC2-BPP', 'PUP San Juan Campus',                6),
        (11, 'Tom',       NULL::VARCHAR, 'Holland',   1, 1, '12 Spider Road',       'Palo',             'Tacloban City',   'Leyte',             'Region VIII - Eastern Visayas',  '6500', DATE '2004-04-21', 'tom.holland@pdts-demo.test',         '09170000011', 'SHS-002', 'BSIT',    'PUP Open University System',          2),
        (12, 'Natalie',   NULL::VARCHAR, 'Portman',   2, 2, '14 Academy Avenue',    'Tetuan',           'Zamboanga City',  'Zamboanga del Sur', 'Region IX - Zamboanga Peninsula', '7000', DATE '2001-08-09', 'natalie.portman@pdts-demo.test',     '09170000012', 'ALS-003', 'BSBA',    'PUP Main Campus — Sta. Mesa, Manila', 3),
        (13, 'Leonardo',  NULL::VARCHAR, 'DiCaprio',  1, 1, '98 Harbor Street',     'Carmen',           'Cagayan de Oro',  'Misamis Oriental',  'Region X - Northern Mindanao',   '9000', DATE '2002-01-25', 'leonardo.dicaprio@pdts-demo.test',   '09170000013', 'COL-004', 'BSOA',    'PUP Paranaque Campus',               1),
        (14, 'Jennifer',  NULL::VARCHAR, 'Lawrence',  2, 1, '36 Katipunan Road',    'Buhangin',         'Davao City',      'Davao del Sur',     'Region XI - Davao Region',       '8000', DATE '2003-11-11', 'jennifer.lawrence@pdts-demo.test',   '09170000014', 'TVT-005', 'BSTM',    'PUP Lopez, Quezon',                  2),
        (15, 'Keanu',     NULL::VARCHAR, 'Reeves',    1, 1, '77 Peace Street',      'Lagao',            'General Santos',  'South Cotabato',    'Region XII - SOCCSKSARGEN',      '9500', DATE '2001-09-02', 'keanu.reeves@pdts-demo.test',        '09170000015', 'OLD-001', 'BSENT',   'PUP San Juan Campus',                1)
    ) AS v(demo_no, first_name, middle_name, last_name, sex, civil_status, house_street, barangay, city, province, region, zip_code, birth_date, email, contact_no, category_id, program_code, campus_name, app_status_id)
)
INSERT INTO applicant (
    applicant_first_name,
    applicant_middle_name,
    applicant_last_name,
    applicant_sex,
    applicant_civil_status,
    applicant_house_number_street,
    applicant_barangay,
    applicant_city_municipality,
    applicant_province,
    applicant_region,
    applicant_zip_code,
    applicant_birth_date,
    applicant_email_address,
    applicant_contact_number,
    educational_background_category_id,
    applicant_enrollment_status,
    applicant_educational_background,
    applicant_is_protected,
    applicant_is_deleted,
    user_id,
    applicant_created_at,
    applicant_updated_at
)
SELECT
    d.first_name,
    d.middle_name,
    d.last_name,
    d.sex,
    d.civil_status,
    d.house_street,
    d.barangay,
    d.city,
    d.province,
    d.region,
    d.zip_code,
    d.birth_date,
    d.email,
    d.contact_no,
    d.category_id,
    'continuing',
    'Demo record for dashboard testing',
    1,
    0,
    COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1),
    NOW() - (d.demo_no || ' days')::INTERVAL,
    NOW()
FROM demo_applicants d
ON CONFLICT DO NOTHING;

-- ============================================================
-- 2) Insert / update one application per demo applicant
-- ============================================================
WITH demo_applications AS (
    SELECT * FROM (VALUES
        (1,  'kriszia.pamintuan@pdts-demo.test',  'BSIT',    'PUP Open University System',          3, 'APP-DEMO-0001'),
        (2,  'joan.balakwit@pdts-demo.test',      'BSBA',    'PUP Main Campus — Sta. Mesa, Manila', 2, 'APP-DEMO-0002'),
        (3,  'jonathan.sanpedro@pdts-demo.test',  'BSOA',    'PUP Paranaque Campus',               2, 'APP-DEMO-0003'),
        (4,  'catherine.gravoso@pdts-demo.test',  'BSTM',    'PUP Lopez, Quezon',                  3, 'APP-DEMO-0004'),
        (5,  'gracheil.cioco@pdts-demo.test',     'BSENT',   'PUP San Juan Campus',                1, 'APP-DEMO-0005'),
        (6,  'emma.stone@pdts-demo.test',         'BSCrim',  'PUP Open University System',          2, 'APP-DEMO-0006'),
        (7,  'ryan.gosling@pdts-demo.test',       'BSN',     'PUP Main Campus — Sta. Mesa, Manila', 3, 'APP-DEMO-0007'),
        (8,  'zendaya.coleman@pdts-demo.test',    'BTLE',    'PUP Paranaque Campus',               4, 'APP-DEMO-0008'),
        (9,  'chris.evans@pdts-demo.test',        'NC2-CHS', 'PUP Lopez, Quezon',                  5, 'APP-DEMO-0009'),
        (10, 'scarlett.johansson@pdts-demo.test', 'NC2-BPP', 'PUP San Juan Campus',                6, 'APP-DEMO-0010'),
        (11, 'tom.holland@pdts-demo.test',        'BSIT',    'PUP Open University System',          2, 'APP-DEMO-0011'),
        (12, 'natalie.portman@pdts-demo.test',    'BSBA',    'PUP Main Campus — Sta. Mesa, Manila', 3, 'APP-DEMO-0012'),
        (13, 'leonardo.dicaprio@pdts-demo.test',  'BSOA',    'PUP Paranaque Campus',               1, 'APP-DEMO-0013'),
        (14, 'jennifer.lawrence@pdts-demo.test',  'BSTM',    'PUP Lopez, Quezon',                  2, 'APP-DEMO-0014'),
        (15, 'keanu.reeves@pdts-demo.test',       'BSENT',   'PUP San Juan Campus',                1, 'APP-DEMO-0015')
    ) AS v(demo_no, email, program_code, campus_name, app_status_id, application_reference_number)
)
INSERT INTO application (
    applicant_id,
    program_id,
    campus_id,
    application_status_id,
    application_date,
    application_semester,
    application_academic_year,
    application_reference_number,
    application_last_notified_date
)
SELECT
    ap.applicant_id,
    p.program_id,
    c.campus_id,
    d.app_status_id,
    CURRENT_DATE - (d.demo_no || ' days')::INTERVAL,
    COALESCE((SELECT setting_value FROM system_setting WHERE setting_key = 'current_semester'), 'First Semester'),
    COALESCE((SELECT setting_value FROM system_setting WHERE setting_key = 'academic_year'), '2025-2026'),
    d.application_reference_number,
    CASE WHEN d.demo_no % 3 = 0 THEN NOW() - (d.demo_no || ' hours')::INTERVAL ELSE NULL END
FROM demo_applications d
JOIN applicant ap ON ap.applicant_email_address = d.email
JOIN program p ON p.program_code = d.program_code
JOIN campus c ON c.campus_name = d.campus_name
ON CONFLICT (application_reference_number) DO UPDATE
SET applicant_id = EXCLUDED.applicant_id,
    program_id = EXCLUDED.program_id,
    campus_id = EXCLUDED.campus_id,
    application_status_id = EXCLUDED.application_status_id,
    application_date = EXCLUDED.application_date,
    application_semester = EXCLUDED.application_semester,
    application_academic_year = EXCLUDED.application_academic_year,
    application_last_notified_date = EXCLUDED.application_last_notified_date;

-- ============================================================
-- 3) Refresh demo emergency contacts
-- ============================================================
DELETE FROM applicant_emergency_contact ec
USING applicant ap
WHERE ec.applicant_id = ap.applicant_id
  AND ap.applicant_email_address LIKE '%@pdts-demo.test';

WITH demo_contacts AS (
    SELECT * FROM (VALUES
        ('kriszia.pamintuan@pdts-demo.test',  'Karen Pamintuan',      'Mother',   '09270000001', 'Manila, Metro Manila'),
        ('joan.balakwit@pdts-demo.test',      'Jose Balakwit',        'Father',   '09270000002', 'Baguio City, Benguet'),
        ('jonathan.sanpedro@pdts-demo.test',  'Marites San Pedro',    'Mother',   '09270000003', 'Laoag City, Ilocos Norte'),
        ('catherine.gravoso@pdts-demo.test',  'Carlo Gravoso',        'Brother',  '09270000004', 'Tuguegarao City, Cagayan'),
        ('gracheil.cioco@pdts-demo.test',     'Grace Cioco',          'Mother',   '09270000005', 'San Fernando, Pampanga'),
        ('emma.stone@pdts-demo.test',         'Emily Stone',          'Guardian', '09270000006', 'Calamba City, Laguna'),
        ('ryan.gosling@pdts-demo.test',       'Robert Gosling',       'Father',   '09270000007', 'Calapan City, Oriental Mindoro'),
        ('zendaya.coleman@pdts-demo.test',    'Claire Coleman',       'Mother',   '09270000008', 'Legazpi City, Albay'),
        ('chris.evans@pdts-demo.test',        'Lisa Evans',           'Mother',   '09270000009', 'Iloilo City, Iloilo'),
        ('scarlett.johansson@pdts-demo.test', 'Melanie Johansson',    'Guardian', '09270000010', 'Cebu City, Cebu'),
        ('tom.holland@pdts-demo.test',        'Dominic Holland',      'Father',   '09270000011', 'Tacloban City, Leyte'),
        ('natalie.portman@pdts-demo.test',    'Shelley Portman',      'Mother',   '09270000012', 'Zamboanga City'),
        ('leonardo.dicaprio@pdts-demo.test',  'Irmelin DiCaprio',     'Mother',   '09270000013', 'Cagayan de Oro'),
        ('jennifer.lawrence@pdts-demo.test',  'Karen Lawrence',       'Mother',   '09270000014', 'Davao City'),
        ('keanu.reeves@pdts-demo.test',       'Patricia Reeves',      'Mother',   '09270000015', 'General Santos City')
    ) AS v(email, contact_name, relationship, contact_number, contact_address)
)
INSERT INTO applicant_emergency_contact (
    applicant_id,
    contact_name,
    relationship,
    contact_number,
    contact_address
)
SELECT
    ap.applicant_id,
    dc.contact_name,
    dc.relationship,
    dc.contact_number,
    dc.contact_address
FROM demo_contacts dc
JOIN applicant ap ON ap.applicant_email_address = dc.email;

-- ============================================================
-- 4A) Full verified checklist for enrolled demo students
-- ============================================================
WITH enrolled_apps AS (
    SELECT a.application_id, a.application_reference_number, ap.educational_background_category_id
    FROM application a
    JOIN applicant ap ON ap.applicant_id = a.applicant_id
    WHERE a.application_reference_number IN ('APP-DEMO-0001', 'APP-DEMO-0004', 'APP-DEMO-0007', 'APP-DEMO-0012')
)
INSERT INTO requirement (
    application_id,
    requirement_type_id,
    requirement_status_id,
    requirement_tracking_no,
    requirement_file_name,
    requirement_image_path,
    requirement_file_url,
    requirement_storage_path,
    requirement_upload_date,
    requirement_uploaded_by_user_id,
    requirement_date_received,
    requirement_processed_by_user_id,
    requirement_processed_at,
    requirement_remarks,
    requirement_is_email_sent
)
SELECT
    ea.application_id,
    cr.type_id,
    3,
    'DOC-DEMO-' || RIGHT(ea.application_reference_number, 4) || '-' || LPAD(cr.type_id::TEXT, 2, '0'),
    regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    'uploads/demo/' || ea.application_reference_number || '/' || regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    'uploads/demo/' || ea.application_reference_number || '/' || regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    'uploads/demo/' || ea.application_reference_number || '/' || regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    NOW() - ((cr.type_id % 8) || ' hours')::INTERVAL,
    COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1),
    NOW() - ((cr.type_id % 6) || ' hours')::INTERVAL,
    COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1),
    NOW() - ((cr.type_id % 5) || ' hours')::INTERVAL,
    'Demo verified document.',
    1
FROM enrolled_apps ea
JOIN curriculum_requirement cr ON cr.category_id = ea.educational_background_category_id AND cr.is_mandatory = 1
JOIN requirement_type rt ON rt.type_id = cr.type_id
ON CONFLICT (application_id, requirement_type_id) DO UPDATE
SET requirement_status_id = EXCLUDED.requirement_status_id,
    requirement_file_name = EXCLUDED.requirement_file_name,
    requirement_image_path = EXCLUDED.requirement_image_path,
    requirement_file_url = EXCLUDED.requirement_file_url,
    requirement_storage_path = EXCLUDED.requirement_storage_path,
    requirement_upload_date = EXCLUDED.requirement_upload_date,
    requirement_date_received = EXCLUDED.requirement_date_received,
    requirement_processed_by_user_id = EXCLUDED.requirement_processed_by_user_id,
    requirement_processed_at = EXCLUDED.requirement_processed_at,
    requirement_remarks = EXCLUDED.requirement_remarks,
    requirement_is_email_sent = EXCLUDED.requirement_is_email_sent;

-- ============================================================
-- 4B) Partial and action-needed documents for other demo students
-- ============================================================
WITH partial_docs AS (
    SELECT * FROM (VALUES
        ('APP-DEMO-0002', 1, 2,  2, 'Demo document currently under review.', NULL::INT),
        ('APP-DEMO-0002', 5, 1,  5, 'Demo pending document.', NULL::INT),
        ('APP-DEMO-0003', 1, 4,  3, 'Demo rejected due to blurry upload.', 1),
        ('APP-DEMO-0003', 3, 2,  7, 'Demo TOR under review.', NULL::INT),
        ('APP-DEMO-0006', 1, 3,  6, 'Demo verified initial requirement.', NULL::INT),
        ('APP-DEMO-0006', 2, 5,  9, 'Demo needs corrected resubmission.', 4),
        ('APP-DEMO-0008', 1, 5, 12, 'Non-compliant demo: correction requested but not completed.', 4),
        ('APP-DEMO-0008', 8, 4, 18, 'Non-compliant demo: rejected clearance.', 2),
        ('APP-DEMO-0009', 11, 1, 24, 'Did not continue demo: pending TVET certificate.', NULL::INT),
        ('APP-DEMO-0011', 1, 2,  4, 'Demo PSA under registrar review.', NULL::INT),
        ('APP-DEMO-0011', 5, 1, 10, 'Demo ID pictures pending.', NULL::INT),
        ('APP-DEMO-0014', 11, 3,  1, 'Demo TVET certificate verified.', NULL::INT),
        ('APP-DEMO-0014', 8, 2,  3, 'Demo police clearance under review.', NULL::INT)
    ) AS v(app_ref, type_id, status_id, hours_ago, remarks, rejection_reason_id)
)
INSERT INTO requirement (
    application_id,
    requirement_type_id,
    requirement_status_id,
    requirement_tracking_no,
    requirement_file_name,
    requirement_image_path,
    requirement_file_url,
    requirement_storage_path,
    requirement_upload_date,
    requirement_uploaded_by_user_id,
    requirement_date_received,
    requirement_processed_by_user_id,
    requirement_processed_at,
    rejection_reason_id,
    rejection_reason_rejected_by_user_id,
    rejection_reason_rejected_at,
    requirement_remarks,
    requirement_is_email_sent
)
SELECT
    a.application_id,
    pd.type_id,
    pd.status_id,
    'DOC-DEMO-' || RIGHT(a.application_reference_number, 4) || '-' || LPAD(pd.type_id::TEXT, 2, '0'),
    regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    'uploads/demo/' || a.application_reference_number || '/' || regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    'uploads/demo/' || a.application_reference_number || '/' || regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    'uploads/demo/' || a.application_reference_number || '/' || regexp_replace(lower(rt.requirement_type_name), '[^a-z0-9]+', '_', 'g') || '.pdf',
    NOW() - (pd.hours_ago || ' hours')::INTERVAL,
    COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1),
    CASE WHEN pd.status_id = 3 THEN NOW() - ((pd.hours_ago - 1) || ' hours')::INTERVAL ELSE NULL END,
    CASE WHEN pd.status_id IN (2,3,4,5) THEN COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1) ELSE NULL END,
    CASE WHEN pd.status_id IN (2,3,4,5) THEN NOW() - ((pd.hours_ago - 1) || ' hours')::INTERVAL ELSE NULL END,
    pd.rejection_reason_id,
    CASE WHEN pd.status_id IN (4,5) THEN COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1) ELSE NULL END,
    CASE WHEN pd.status_id IN (4,5) THEN NOW() - ((pd.hours_ago - 1) || ' hours')::INTERVAL ELSE NULL END,
    pd.remarks,
    CASE WHEN pd.status_id IN (3,4,5) THEN 1 ELSE 0 END
FROM partial_docs pd
JOIN application a ON a.application_reference_number = pd.app_ref
JOIN requirement_type rt ON rt.type_id = pd.type_id
ON CONFLICT (application_id, requirement_type_id) DO UPDATE
SET requirement_status_id = EXCLUDED.requirement_status_id,
    requirement_file_name = EXCLUDED.requirement_file_name,
    requirement_image_path = EXCLUDED.requirement_image_path,
    requirement_file_url = EXCLUDED.requirement_file_url,
    requirement_storage_path = EXCLUDED.requirement_storage_path,
    requirement_upload_date = EXCLUDED.requirement_upload_date,
    requirement_date_received = EXCLUDED.requirement_date_received,
    requirement_processed_by_user_id = EXCLUDED.requirement_processed_by_user_id,
    requirement_processed_at = EXCLUDED.requirement_processed_at,
    rejection_reason_id = EXCLUDED.rejection_reason_id,
    rejection_reason_rejected_by_user_id = EXCLUDED.rejection_reason_rejected_by_user_id,
    rejection_reason_rejected_at = EXCLUDED.rejection_reason_rejected_at,
    requirement_remarks = EXCLUDED.requirement_remarks,
    requirement_is_email_sent = EXCLUDED.requirement_is_email_sent;

-- ============================================================
-- 5) Add demo audit log item for dashboard/audit testing
-- ============================================================
INSERT INTO user_activity_log (
    user_activity_log_user_id,
    user_activity_log_action_type,
    user_activity_log_entity_type,
    user_activity_log_description,
    user_activity_log_new_value,
    user_activity_log_ip_address,
    user_activity_log_performed_at
)
SELECT
    COALESCE((SELECT user_id FROM app_user WHERE user_username = 'admin001' LIMIT 1), 1),
    'DEMO_DATA_INSERT',
    'Applicant',
    'Inserted or refreshed 15 demo applicants for dashboard testing.',
    '15 demo applicants, applications, emergency contacts, and sample document submissions.',
    '127.0.0.1',
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM user_activity_log
    WHERE user_activity_log_action_type = 'DEMO_DATA_INSERT'
      AND user_activity_log_description = 'Inserted or refreshed 15 demo applicants for dashboard testing.'
);

COMMIT;

-- ============================================================
-- QUICK VERIFICATION QUERIES
-- Run these after the script if you want to confirm.
-- ============================================================
-- SELECT COUNT(*) AS total_applicants FROM applicant;
--
-- SELECT applicant_first_name, applicant_last_name, applicant_region
-- FROM applicant
-- WHERE applicant_email_address LIKE '%@pdts-demo.test'
-- ORDER BY applicant_id;
--
-- SELECT ast.application_status_name, COUNT(*)
-- FROM application a
-- JOIN application_status ast ON ast.application_status_id = a.application_status_id
-- WHERE a.application_reference_number LIKE 'APP-DEMO-%'
-- GROUP BY ast.application_status_name
-- ORDER BY ast.application_status_name;
