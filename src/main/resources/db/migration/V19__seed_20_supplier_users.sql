-- ============================================================
-- V19: Seed 20 SUPPLIER users (1 user per supplier company id 3-22)
-- ============================================================
-- Purpose:
--   Each of the 20 supplier companies seeded in V18 gets exactly one
--   SUPPLIER user so the system can authenticate and authorize
--   product-management flows.
--
-- Convention:
--   - username: supplier_{company_id}        (e.g. supplier_3, supplier_4, ...)
--   - email:    supplier{company_id}@supplier.vn
--   - password: password123 (BCrypt hash from V2)
--   - role_id:  SUPPLIER (role_id = (SELECT id FROM roles WHERE name='SUPPLIER'))
--   - company_id: matches the company seeded in V18
--   - status:   ACTIVE
--
-- Notes:
--   - Re-uses the same BCrypt hash used by V2 for password 'password123'.
--   - User 1 (admin), 2 (buyer), 3 (supplier, company 2) remain untouched.
--   - username and email are unique by DB constraint.
-- ============================================================

INSERT INTO users (
    role_id,
    company_id,
    username,
    password,
    full_name,
    email,
    phone,
    status,
    created_at,
    updated_at
)
VALUES
((SELECT id FROM roles WHERE name='SUPPLIER'),  3, 'supplier_3',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Nguyễn Văn An',     'supplier3@supplier.vn',  '0911000003', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'),  4, 'supplier_4',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Trần Thị Bình',     'supplier4@supplier.vn',  '0911000004', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'),  5, 'supplier_5',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Lê Hoàng Cường',   'supplier5@supplier.vn',  '0911000005', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'),  6, 'supplier_6',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Phạm Thị Dung',    'supplier6@supplier.vn',  '0911000006', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'),  7, 'supplier_7',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Đỗ Minh Đức',     'supplier7@supplier.vn',  '0911000007', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'),  8, 'supplier_8',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Hoàng Thị Em',     'supplier8@supplier.vn',  '0911000008', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'),  9, 'supplier_9',  '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Ngô Văn Phong',    'supplier9@supplier.vn',  '0911000009', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 10, 'supplier_10', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Bùi Thị Giang',    'supplier10@supplier.vn', '0911000010', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 11, 'supplier_11', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Vũ Quang Hiếu',    'supplier11@supplier.vn', '0911000011', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 12, 'supplier_12', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Đặng Thị Hoa',     'supplier12@supplier.vn', '0911000012', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 13, 'supplier_13', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Phan Văn Khải',    'supplier13@supplier.vn', '0911000013', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 14, 'supplier_14', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Lý Thị Lan',       'supplier14@supplier.vn', '0911000014', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 15, 'supplier_15', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Trịnh Văn Minh',   'supplier15@supplier.vn', '0911000015', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 16, 'supplier_16', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Cao Thị Ngọc',     'supplier16@supplier.vn', '0911000016', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 17, 'supplier_17', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Tô Văn Oai',       'supplier17@supplier.vn', '0911000017', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 18, 'supplier_18', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Đinh Thị Phương',  'supplier18@supplier.vn', '0911000018', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 19, 'supplier_19', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Mai Văn Quân',     'supplier19@supplier.vn', '0911000019', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 20, 'supplier_20', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Hồ Thị Sen',       'supplier20@supplier.vn', '0911000020', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 21, 'supplier_21', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Châu Văn Tài',     'supplier21@supplier.vn', '0911000021', 'ACTIVE', NOW(), NOW()),
((SELECT id FROM roles WHERE name='SUPPLIER'), 22, 'supplier_22', '$2a$10$OVdBmE4Qh45Jy2QfcIWbOOvQ9a6dfYyusnUUDi.wTOA4G09nizYZy', 'Lâm Thị Uyên',     'supplier22@supplier.vn', '0911000022', 'ACTIVE', NOW(), NOW());