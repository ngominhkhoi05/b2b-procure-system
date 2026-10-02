-- ============================================================
-- V18: Seed 20 supplier companies + 30 categories (Vietnamese)
-- ============================================================
-- Purpose:
--   Provide realistic seed data for Vietnamese B2B procurement
--   - 20 distinct SUPPLIER companies with Vietnamese names
--   - 30 distinct product categories with Vietnamese names
--
-- Notes:
--   - Companies are added as SUPPLIER only.
--   - Categories are independent of companies (categories are global master data).
--   - All tax_codes are unique (uk_companies_tax_code).
--   - All category names are unique (uk_categories_name).
-- ============================================================

-- ----------------------------
-- 1) Seed 20 SUPPLIER companies
-- ----------------------------
INSERT INTO companies (name, tax_code, email, phone, address, company_type, status, created_at, updated_at) VALUES
    ('Công ty TNHH Thiết Bị Công Nghiệp An Phát',     '0301000001', 'contact@anphat-industrial.vn',   '0901000001', 'Số 12 đường Nguyễn Văn Cừ, Long Biên, Hà Nội',          'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Vật Liệu Xây Dựng Hòa Bình',        '0301000002', 'sales@hoabinh-construction.vn', '0901000002', 'Số 45 đường Lê Hồng Phong, Quận 5, TP. Hồ Chí Minh',    'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Thực Phẩm Sạch Việt Xanh',        '0301000003', 'info@xanhfoods.vn',             '0902000003', 'Số 8 đường Phan Văn Trị, Gò Vấp, TP. Hồ Chí Minh',       'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Dược Phẩm Thiên Phúc',             '0301000004', 'lienhe@thienphuc-pharma.vn',    '0902000004', 'Số 22 đường Trần Hưng Đạo, Quận 1, TP. Hồ Chí Minh',     'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Điện Tử Minh Tâm',                '0301000005', 'support@minhtam-electronics.vn','0903000005', 'Số 99 đường Cầu Giấy, Quận Cầu Giấy, Hà Nội',           'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP May Mặc Thời Trang Phú Xuân',        '0301000006', 'sales@phuxuan-fashion.vn',      '0903000006', 'Số 17 đường Hai Bà Trưng, Quận 3, TP. Hồ Chí Minh',      'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Nông Sản Sạch Tây Nguyên',        '0301000007', 'cskh@taynguyen-organic.vn',     '0904000007', 'Thôn 3, xã Ea Kao, TP. Buôn Ma Thuột, Đắc Lắk',          'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Hóa Chất Công Nghiệp Việt Hóa',     '0301000008', 'kinhdoanh@viethoa-chem.vn',     '0904000008', 'Số 56 đường Quốc Lộ 5, Hải Phòng',                       'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Giấy và Bao Bì Bảo An',           '0301000009', 'info@baoan-packaging.vn',        '0905000009', 'Số 101 đường Bạch Đằng, Quận Hải Châu, Đà Nẵng',         'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Thiết Bị Y Tế Sài Gòn Medic',       '0301000010', 'contact@saigonmedic.vn',        '0905000010', 'Số 220 đường Điện Biên Phủ, Quận Bình Thạnh, TP. HCM',   'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Nội Thất Gỗ Tự Nhiên Gia Phát',  '0301000011', 'showroom@giaphat-wood.vn',      '0906000011', 'Số 35 đường Phạm Văn Đồng, Quận Thủ Đức, TP. HCM',       'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Phụ Tùng Ô Tô Nam Phát',            '0301000012', 'phutung@namphat-auto.vn',       '0906000012', 'Số 88 đường Trường Chinh, Quận Tân Bình, TP. HCM',        'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH In Ấn và Quảng Cáo Tường Vy',     '0301000013', 'print@tungvy-ads.vn',           '0907000013', 'Số 12 đường Lý Thường Kiệt, Quận Hoàn Kiếm, Hà Nội',     'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Cơ Khí Chính Xác Đại Phát',         '0301000014', 'engineering@daiphat-mech.vn',   '0907000014', 'Số 67 đường Quốc Lộ 1A, Quận 12, TP. HCM',               'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Mỹ Phẩm Thiên Nhiên Mỹ Hảo',     '0301000015', 'info@myhao-cosmetics.vn',       '0908000015', 'Số 5 đường Nguyễn Thị Minh Khai, Quận Đống Đa, Hà Nội',  'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Văn Phòng Phẩm Hồng Hà',            '0301000016', 'sales@hongha-stationery.vn',    '0908000016', 'Số 144 đường Xô Viết Nghệ Tĩnh, Quận Bình Thạnh, TP. HCM','SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Thiết Bị Điện Lạnh Bách Khoa',    '0301000017', 'service@bachkhoa-cooling.vn',   '0909000017', 'Số 78 đường Cầu Diễn, Quận Nam Từ Liêm, Hà Nội',         'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Thủy Sản Sông Hậu',                 '0301000018', 'export@songhua-seafood.vn',     '0909000018', 'Số 22 đường Trần Phú, TP. Cần Thơ',                       'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty TNHH Sơn và Chống Thấm Tân Tiến',      '0301000019', 'cskh@tantien-paint.vn',        '0910000019', 'Số 99 đường Phan Đình Phùng, Quận Ba Đình, Hà Nội',       'SUPPLIER', 'ACTIVE', NOW(), NOW()),
    ('Công ty CP Gia Dụng và Đồ Gia Dụng Thanh Tùng', '0301000020', 'lienhe@thanhtung-houseware.vn', '0910000020', 'Số 56 đường Nguyễn Trãi, Quận 5, TP. Hồ Chí Minh',       'SUPPLIER', 'ACTIVE', NOW(), NOW());

-- ----------------------------
-- 2) Seed 30 product categories (Vietnamese, with diacritics)
-- ----------------------------
INSERT INTO categories (name, description, status, created_at, updated_at) VALUES
    ('Thiết bị công nghiệp',      'Máy móc, dây chuyền và thiết bị phục vụ sản xuất công nghiệp.',         'ACTIVE', NOW(), NOW()),
    ('Vật liệu xây dựng',         'Xi, sắt, thép, gạch, ngói, sơn, vật liệu hoàn thiện công trình.',      'ACTIVE', NOW(), NOW()),
    ('Thực phẩm và đồ uống',      'Thực phẩm tươi sống, đồ khô, đồ uống và nguyên liệu chế biến.',        'ACTIVE', NOW(), NOW()),
    ('Dược phẩm và y tế',         'Thuốc, thiết bị y tế, vật tư y tế và chăm sóc sức khỏe.',                'ACTIVE', NOW(), NOW()),
    ('Điện tử và công nghệ',      'Thiết bị điện tử, máy tính, linh kiện và phụ kiện công nghệ.',          'ACTIVE', NOW(), NOW()),
    ('Thời trang và may mặc',     'Quần áo, vải vóc, phụ kiện thời trang công nghiệp và đồng phục.',       'ACTIVE', NOW(), NOW()),
    ('Nông sản và thủy sản',      'Nông sản, thủy sản tươi sống, đông lạnh và chế biến.',                   'ACTIVE', NOW(), NOW()),
    ('Hóa chất công nghiệp',      'Hóa chất sản xuất, dung môi, phụ gia công nghiệp và xử lý nước.',       'ACTIVE', NOW(), NOW()),
    ('Giấy và bao bì',            'Giấy in, bao bì carton, túi giấy, màng bọc và vật tư đóng gói.',        'ACTIVE', NOW(), NOW()),
    ('Thiết bị y tế chuyên dụng', 'Máy siêu âm, máy X-quang, thiết bị phẫu thuật và chẩn đoán hình ảnh.',  'ACTIVE', NOW(), NOW()),
    ('Nội thất và trang trí',    'Bàn ghế, tủ, kệ, vật liệu trang trí nội thất văn phòng và gia đình.',   'ACTIVE', NOW(), NOW()),
    ('Phụ tùng ô tô và xe máy',   'Phụ tùng thay thế, phụ kiện, đồ chơi xe và vật tư bảo dưỡng.',          'ACTIVE', NOW(), NOW()),
    ('In ấn và quảng cáo',        'Dịch vụ in ấn, biển hiệu, banner, standee và vật phẩm quảng cáo.',       'ACTIVE', NOW(), NOW()),
    ('Cơ khí và gia công',        'Cơ khí chính xác, gia công CNC, đúc, dập và hàn kim loại.',              'ACTIVE', NOW(), NOW()),
    ('Mỹ phẩm và chăm sóc cá nhân', 'Mỹ phẩm thiên nhiên, sản phẩm chăm sóc da và vệ sinh cá nhân.',      'ACTIVE', NOW(), NOW()),
    ('Văn phòng phẩm',            'Bút, giấy, sổ, bìa hồ sơ, dụng cụ văn phòng và thiết bị hỗ trợ.',       'ACTIVE', NOW(), NOW()),
    ('Điện lạnh và thiết bị làm mát', 'Máy lạnh, tủ đông, tủ mát, quạt công nghiệp và thiết bị HVAC.',  'ACTIVE', NOW(), NOW()),
    ('Thủy hải sản đông lạnh',    'Cá, tôm, mực và thủy hải sản chế biến đông lạnh xuất khẩu.',             'ACTIVE', NOW(), NOW()),
    ('Sơn và vật liệu chống thấm','Sơn nước, sơn dầu, chống thấm và vật liệu phủ bề mặt.',               'ACTIVE', NOW(), NOW()),
    ('Đồ gia dụng',               'Đồ dùng nhà bếp, phòng tắm, phòng ngủ và thiết bị gia đình thông minh.', 'ACTIVE', NOW(), NOW()),
    ('Dụng cụ cầm tay',           'Búa, kìm, tua vít, máy khoan và dụng cụ sửa chữa chuyên dụng.',          'ACTIVE', NOW(), NOW()),
    ('Thiết bị chiếu sáng',       'Đèn LED, đèn công nghiệp, đèn trang trí và phụ kiện chiếu sáng.',        'ACTIVE', NOW(), NOW()),
    ('An ninh và camera',         'Camera giám sát, hệ thống báo động, khóa thông minh và an ninh.',        'ACTIVE', NOW(), NOW()),
    ('Phân bón và thuốc bảo vệ thực vật', 'Phân bón hữu cơ, vô cơ, thuốc trừ sâu và chế phẩm nông nghiệp.','ACTIVE', NOW(), NOW()),
    ('Máy móc nông nghiệp',       'Máy cày, máy gặn, máy tắm phun và thiết bị canh tác nông nghiệp.',     'ACTIVE', NOW(), NOW()),
    ('Nguyên vật liệu dệt may',  'Sợi, vải, chỉ may và nguyên liệu phụ trợ cho ngành dệt may.',           'ACTIVE', NOW(), NOW()),
    ('Thiết bị bếp công nghiệp',  'Bếp gas, bếp từ, lò nướng, máy rửa chén công nghiệp và thiết bị nhà hàng.','ACTIVE', NOW(), NOW()),
    ('Dụng cụ thể thao và thể hình', 'Dụng cụ gym, yoga, bóng đá, bóng rổ và thiết bị thể thao chuyên dụng.','ACTIVE', NOW(), NOW()),
    ('Đồ chơi và quà tặng trẻ em', 'Đồ chơi an toàn, quà tặng trẻ em và vật phẩm giáo dục.',              'ACTIVE', NOW(), NOW()),
    ('Thiết bị môi trường và xử lý rác', 'Máy xử lý rác, hệ thống lọc nước, thiết bị tái chế và xử lý chất thải.','ACTIVE', NOW(), NOW());