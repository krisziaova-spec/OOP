-- ============================================================
-- PDTS role access cleanup
-- Applies the requested access structure:
-- 1) Head Admission = protected full-access account, maximum of 3 accounts
-- 2) Admin = registrar office staff / operational access only
-- 3) Admission Personnel = limited student-assistant/document support
-- ============================================================

-- Keep role-permission data consistent with the UI/access rules.
DELETE FROM role_permission
WHERE role_id IN (
    SELECT role_id
    FROM role
    WHERE role_name IN ('Admission Personnel', 'Admin', 'Head Admission')
);

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
JOIN permission p ON p.permission_name IN ('UPLOAD_DOCUMENT', 'FILTER_SEARCH')
WHERE r.role_name = 'Admission Personnel'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
JOIN permission p ON p.permission_name IN (
    'UPLOAD_DOCUMENT',
    'REJECT_DOCUMENT',
    'RECEIVE_DOCUMENT',
    'MANAGE_REASONS',
    'REVOKE_TOKEN',
    'FILTER_SEARCH'
)
WHERE r.role_name = 'Admin'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM role r
JOIN permission p ON p.permission_name IN (
    'UPLOAD_DOCUMENT',
    'REJECT_DOCUMENT',
    'RECEIVE_DOCUMENT',
    'VIEW_LOGS',
    'MANAGE_USERS',
    'MANAGE_REASONS',
    'REVOKE_TOKEN',
    'FILTER_SEARCH',
    'MANAGE_SETTINGS'
)
WHERE r.role_name = 'Head Admission'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Allow up to three Head Admission accounts.
-- This removes the previous one-account database restriction if it was already applied.
DROP INDEX IF EXISTS uq_one_head_admission_account;

-- The maximum-of-3 rule is enforced in UserPageController when creating accounts.
-- PostgreSQL partial indexes cannot directly enforce a maximum count above 1.
