# BÁO CÁO TOÀN DIỆN CÁC REST API HỆ THỐNG B2B PROCURE

> **Dự án**: `b2b-procure-system` (Backend REST API)  
> **Công nghệ**: Java 21 | Spring Boot 4.1.1 | PostgreSQL | Spring Data JPA | Spring Security & JWT  
> **Cập nhật**: 2026-09-17  
> **Tổng số API hiện có**: **39 endpoints** (8 Controllers)

---

## MỤC LỤC
- [1. Chuẩn định dạng Response](#1-chuẩn-định-dạng-response)
- [2. Module Authentication & OAuth2 (`/api/v1/auth`)](#2-module-authentication--oauth2)
- [3. Module Quản lý Người dùng (`/api/v1/users`)](#3-module-quản-lý-người-dùng)
- [4. Module Quản lý Công ty (`/api/v1/companies`)](#4-module-quản-lý-công-ty)
- [5. Module Quản lý Danh mục Sản phẩm (`/api/v1/categories`)](#5-module-quản-lý-danh-mục-sản-phẩm)
- [6. Module Quản lý Sản phẩm (`/api/v1/products`)](#6-module-quản-lý-sản-phẩm)
- [7. Module Quản lý Bậc giá theo số lượng (`/api/v1/products/{id}/prices`)](#7-module-quản-lý-bậc-giá-theo-số-lượng)
- [8. Module Quản lý Giỏ hàng (`/api/v1/cart`)](#8-module-quản-lý-giỏ-hàng)
- [9. Module Cấu hình Hệ thống (`/api/v1/settings`)](#9-module-cấu-hình-hệ-thống)
- [10. Ma trận Phân quyền RBAC (Role-Based Access Control)](#10-ma-trận-phân-quyền-rbac)

---

## 1. CHUẨN ĐỊNH DẠNG RESPONSE

Mọi HTTP response trả về từ hệ thống đều được đồng nhất hóa thông qua wrapper `ApiResponse<T>`.

### 1.1. Cấu trúc Thành công (`HTTP 200 OK` / `HTTP 201 CREATED`)

#### a) Trả về đơn đối tượng (Single Object)
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "id": 1,
    "username": "buyer_company_a",
    "email": "buyer@companya.com",
    "fullName": "Nguyen Van Buyer",
    "role": "BUYER"
  },
  "timestamp": "2026-09-17 14:00:00"
}
```

#### b) Trả về danh sách có phân trang (`PageResponse<T>`)
```json
{
  "success": true,
  "message": "Products retrieved successfully",
  "data": {
    "content": [
      {
        "id": 10,
        "sku": "SKU-STEEL-001",
        "name": "Thép cuộn cán nóng"
      }
    ],
    "pageNo": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1,
    "first": true,
    "last": true
  },
  "timestamp": "2026-09-17 14:00:00"
}
```

#### c) Thao tác không trả về dữ liệu (Void Response)
```json
{
  "success": true,
  "message": "Password changed successfully",
  "timestamp": "2026-09-17 14:00:00"
}
```

---

### 1.2. Cấu trúc Thất bại

#### a) Lỗi nghiệp vụ / Lỗi phân quyền / Không tìm thấy tài nguyên
```json
{
  "success": false,
  "message": "Username is already taken: buyer_admin",
  "timestamp": "2026-09-17 14:00:00"
}
```

#### b) Lỗi Validation dữ liệu đầu vào (`HTTP 400 Bad Request`)
```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "email": "Email must be valid",
    "password": "Password must be at least 6 characters"
  },
  "timestamp": "2026-09-17 14:00:00"
}
```

---

### 1.3. Bảng mã trạng thái HTTP

| HTTP Code | Định nghĩa | Ý nghĩa áp dụng |
|:---:|:---|:---|
| **`200`** | `OK` | Truy vấn hoặc cập nhật dữ liệu thành công. |
| **`201`** | `CREATED` | Tạo mới tài nguyên thành công (Insert database). |
| **`400`** | `BAD REQUEST` | Dữ liệu gửi lên sai định dạng, vi phạm validation hoặc quy tắc logic nghiệp vụ. |
| **`401`** | `UNAUTHORIZED` | Chưa đăng nhập, sai thông tin xác thực, token JWT hết hạn hoặc không hợp lệ. |
| **`403`** | `FORBIDDEN` | Đã xác thực nhưng không đủ quyền hạn (sai Role hoặc không phải chủ sở hữu tài nguyên). |
| **`404`** | `NOT FOUND` | Không tìm thấy bản ghi theo ID/Khóa truy vấn. |
| **`409`** | `CONFLICT` | Xung đột dữ liệu duy nhất (Unique constraint: `username`, `email`, `tax_code`, `sku`...). |
| **`500`** | `INTERNAL SERVER ERROR` | Lỗi máy chủ không mong muốn. |

---

## 2. MODULE AUTHENTICATION & OAUTH2
* **Base URL**: `/api/v1/auth`  
* **Controller**: `AuthController.java`

---

### 2.1. Đăng ký tài khoản truyền thống
* **Phương thức & URL**: `POST /api/v1/auth/register`
* **Quyền sử dụng**: `Public` (Không yêu cầu đăng nhập)
* **Tác dụng**: Đăng ký người dùng mới, tự động liên kết hoặc tạo mới công ty và cấp vai trò `BUYER` hoặc `SUPPLIER`.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | `username` chưa tồn tại | `409 CONFLICT` (Username is already taken) |
  | `users` | `email` chưa tồn tại | `409 CONFLICT` (Email is already registered) |
  | `companies` | Nếu truyền `companyId`: Công ty phải tồn tại, `status = 'ACTIVE'`, `company_type` khớp loại yêu cầu | `404 NOT FOUND` hoặc `400 BAD REQUEST` |
  | `companies` | Nếu tạo công ty mới: `tax_code` chưa từng tồn tại | `409 CONFLICT` (Tax code already exists) |
  | `roles` | Tìm bản ghi theo tên `BUYER` hoặc `SUPPLIER` từ bảng Roles master | `404 NOT FOUND` |
* **Mã thành công**: `201 CREATED` $\rightarrow$ Trả về `LoginResponse` (kèm JWT Access Token).
* **Mã thất bại**: `400 BAD REQUEST`, `404 NOT FOUND`, `409 CONFLICT`.

---

### 2.2. Đăng nhập hệ thống
* **Phương thức & URL**: `POST /api/v1/auth/login`
* **Quyền sử dụng**: `Public`
* **Tác dụng**: Xác thực danh tính bằng username/email và mật khẩu, cấp JWT Access Token.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Tìm theo `username` hoặc `email` | `401 UNAUTHORIZED` (Invalid username or password) |
  | `users` | Khớp mật khẩu mã hóa BCrypt (`passwordEncoder.matches`) | `401 UNAUTHORIZED` (Invalid username or password) |
  | `users` | `status = 'ACTIVE'` | `401 UNAUTHORIZED` (Account is disabled or inactive) |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `LoginResponse` (gồm `accessToken`, `tokenType`, `userId`, `username`, `role`).
* **Mã thất bại**: `401 UNAUTHORIZED`.

---

### 2.3. Hoàn tất đăng ký Google OAuth2 lần đầu
* **Phương thức & URL**: `POST /api/v1/auth/oauth2/register`
* **Quyền sử dụng**: `Public` (kèm Registration Token trong Header hoặc Body)
* **Tác dụng**: Cập nhật thông tin công ty và role cho tài khoản đăng nhập qua Google OAuth2 lần đầu.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | Token | Token tạm thời còn hạn, chứa claims `provider = GOOGLE`, `providerUserId`, `email` | `401 UNAUTHORIZED` |
  | `auth_accounts` | `provider` và `provider_user_id` chưa từng liên kết | `409 CONFLICT` (Google account is already registered) |
  | `users` | `email` chưa tồn tại trong bảng users | `409 CONFLICT` (Email is already registered) |
  | `companies` | Tương tự quy tắc kiểm tra công ty ở mục 2.1 | `400 BAD REQUEST` / `404` / `409` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `LoginResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `401 UNAUTHORIZED`, `409 CONFLICT`.

---

### 2.4. Liên kết tài khoản Google OAuth2 với tài khoản hiện tại
* **Phương thức & URL**: `POST /api/v1/auth/oauth2/link`
* **Quyền sử dụng**: `Authenticated` (Bắt buộc đăng nhập tài khoản hiện có)
* **Tác dụng**: Gắn tài khoản Google vào user hiện tại để đăng nhập nhanh trong tương lai.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | User hiện tại tồn tại và `status = 'ACTIVE'` | `400 BAD REQUEST` |
  | `auth_accounts` | Tài khoản Google chưa được gắn vào bất kỳ user nào khác | `409 CONFLICT` |
  | `auth_accounts` | User hiện tại chưa từng liên kết tài khoản Google nào khác | `409 CONFLICT` |
* **Mã thành công**: `200 OK` (Message: "Google account linked successfully").
* **Mã thất bại**: `400 BAD REQUEST`, `401 UNAUTHORIZED`, `403 FORBIDDEN`, `409 CONFLICT`.

---

## 3. MODULE QUẢN LÝ NGƯỜI DÙNG
* **Base URL**: `/api/v1/users`  
* **Controller**: `UserController.java`

---

### 3.1. Lấy thông tin cá nhân của User hiện tại
* **Phương thức & URL**: `GET /api/v1/users/me`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Người dùng xem thông tin cá nhân, vai trò và công ty của mình.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Tìm theo ID người dùng trong SecurityContext | `401 UNAUTHORIZED` / `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `UserResponse`.
* **Mã thất bại**: `401 UNAUTHORIZED`.

---

### 3.2. Cập nhật thông tin cá nhân của User hiện tại
* **Phương thức & URL**: `PUT /api/v1/users/me`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Cập nhật họ tên, số điện thoại, avatar, ảnh bìa. (Không được thay đổi role, công ty, username, email).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Tìm theo ID hiện tại, cập nhật `full_name`, `phone`, `avatar_url`, `cover_image_url` | `401 UNAUTHORIZED` / `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `UserResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `401 UNAUTHORIZED`.

---

### 3.3. Đổi mật khẩu
* **Phương thức & URL**: `PATCH /api/v1/users/me/password`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Đổi mật khẩu đăng nhập.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Cột `password` không được null (tài khoản thuần OAuth2 không hỗ trợ đổi mật khẩu) | `400 BAD REQUEST` |
  | `users` | Mật khẩu cũ phải khớp mã hóa trong DB (`passwordEncoder.matches`) | `400 BAD REQUEST` (Current password is incorrect) |
  | `users` | Mật khẩu mới phải khác mật khẩu cũ | `400 BAD REQUEST` (New password must be different) |
* **Mã thành công**: `200 OK` (Message: "Password changed successfully").
* **Mã thất bại**: `400 BAD REQUEST`, `401 UNAUTHORIZED`.

---

### 3.4. Danh sách người dùng (Phân trang & Lọc)
* **Phương thức & URL**: `GET /api/v1/users`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Quản trị viên tra cứu danh sách người dùng toàn sàn kèm bộ lọc.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users`, `roles` | Lọc theo `roles.name`, `users.status`, từ khóa (`username`, `email`, `full_name`) | `403 FORBIDDEN` (nếu không phải Admin) |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `PageResponse<UserResponse>`.
* **Mã thất bại**: `401 UNAUTHORIZED`, `403 FORBIDDEN`.

---

### 3.5. Xem chi tiết người dùng theo ID
* **Phương thức & URL**: `GET /api/v1/users/{id}`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Admin xem hồ sơ chi tiết của một tài khoản bất kỳ.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Bản ghi tồn tại với khóa chính `id` | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `UserResponse`.
* **Mã thất bại**: `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 3.6. Admin cập nhật thông tin người dùng
* **Phương thức & URL**: `PUT /api/v1/users/{id}`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Quản trị viên cập nhật thông tin người dùng theo ID.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Bản ghi tồn tại với khóa chính `id` | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `UserResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 3.7. Cập nhật trạng thái người dùng
* **Phương thức & URL**: `PATCH /api/v1/users/{id}/status`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Khóa/mở khóa tài khoản (`ACTIVE`, `INACTIVE`, `BLOCKED`).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | Bản ghi tồn tại với khóa chính `id` | `404 NOT FOUND` |
  | Nghiệp vụ | Admin **không được tự khóa/vô hiệu hóa** tài khoản của chính mình | `400 BAD REQUEST` |
  | Dữ liệu | Giá trị `status` phải nằm trong tập `['ACTIVE', 'INACTIVE', 'BLOCKED']` | `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `UserResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

## 4. MODULE QUẢN LÝ CÔNG TY
* **Base URL**: `/api/v1/companies`  
* **Controller**: `CompanyController.java`

---

### 4.1. Lấy thông tin công ty của User hiện tại
* **Phương thức & URL**: `GET /api/v1/companies/me`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Doanh nghiệp Buyer hoặc Supplier xem thông tin công ty mình đang trực thuộc.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | User phải có liên kết `company_id != null` | `400 BAD REQUEST` (User does not belong to any company) |
  | `companies` | Bản ghi tồn tại theo `users.company_id` | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CompanyResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `401 UNAUTHORIZED`.

---

### 4.2. Xem thông tin công ty theo ID
* **Phương thức & URL**: `GET /api/v1/companies/{id}`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Xem chi tiết công ty theo ID.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | Phân quyền | Nếu không phải `ADMIN`, chỉ được phép xem công ty của chính mình (`currentUser.companyId == id`) | `403 FORBIDDEN` (Cannot access another company) |
  | `companies` | Bản ghi tồn tại theo `id` | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CompanyResponse`.
* **Mã thất bại**: `401 UNAUTHORIZED`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 4.3. Danh sách công ty (Phân trang & Lọc)
* **Phương thức & URL**: `GET /api/v1/companies`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Quản trị viên tra cứu danh sách toàn bộ doanh nghiệp trên sàn.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `companies` | Lọc theo `company_type` (`BUYER`, `SUPPLIER`), `status`, từ khóa (`name`, `tax_code`) | `403 FORBIDDEN` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `PageResponse<CompanyResponse>`.
* **Mã thất bại**: `401 UNAUTHORIZED`, `403 FORBIDDEN`.

---

### 4.4. Cập nhật hồ sơ công ty của chính mình
* **Phương thức & URL**: `PUT /api/v1/companies/me`
* **Quyền sử dụng**: `BUYER`, `SUPPLIER`
* **Tác dụng**: Doanh nghiệp tự chỉnh sửa tên, địa chỉ, số điện thoại, mã số thuế. (Cấm sửa `company_type`).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `companies` | `tax_code` mới (nếu sửa) không được trùng với công ty khác | `409 CONFLICT` (Tax code already exists) |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CompanyResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `401 UNAUTHORIZED`, `409 CONFLICT`.

---

### 4.5. Admin cập nhật công ty theo ID
* **Phương thức & URL**: `PUT /api/v1/companies/{id}`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Quản trị viên cập nhật thông tin bất kỳ công ty nào.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `companies` | Tồn tại `id`, `tax_code` không trùng với công ty khác | `404 NOT FOUND` / `409 CONFLICT` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CompanyResponse`.
* **Mã thất bại**: `403 FORBIDDEN`, `404 NOT FOUND`, `409 CONFLICT`.

---

### 4.6. Admin cập nhật trạng thái công ty
* **Phương thức & URL**: `PATCH /api/v1/companies/{id}/status`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Phê duyệt, tạm khóa hoặc hủy kích hoạt công ty (`ACTIVE`, `INACTIVE`, `BLOCKED`).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `companies` | Tồn tại `id`, giá trị trạng thái hợp lệ | `404 NOT FOUND` / `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CompanyResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

## 5. MODULE QUẢN LÝ DANH MỤC SẢN PHẨM
* **Base URL**: `/api/v1/categories`  
* **Controller**: `CategoryController.java`

---

### 5.1. Tạo danh mục sản phẩm mới
* **Phương thức & URL**: `POST /api/v1/categories`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Tạo mới một phân loại sản phẩm.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `categories` | Tên danh mục không được để trống | `400 BAD REQUEST` |
  | `categories` | Tên danh mục duy nhất không phân biệt hoa thường (`uk_categories_name`) | `409 CONFLICT` (Category name already exists) |
* **Mã thành công**: `201 CREATED` $\rightarrow$ Trả về `CategoryResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `409 CONFLICT`.

---

### 5.2. Lấy danh sách danh mục (Phân trang)
* **Phương thức & URL**: `GET /api/v1/categories`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Xem danh sách danh mục sản phẩm.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `categories` | Nếu là `ADMIN`: Xem được mọi trạng thái (`ACTIVE`, `INACTIVE`) | `401 UNAUTHORIZED` |
  | `categories` | Nếu là `BUYER` hoặc `SUPPLIER`: Bắt buộc chỉ hiển thị `status = 'ACTIVE'` | `401 UNAUTHORIZED` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `PageResponse<CategoryResponse>`.
* **Mã thất bại**: `401 UNAUTHORIZED`.

---

### 5.3. Xem chi tiết danh mục theo ID
* **Phương thức & URL**: `GET /api/v1/categories/{id}`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Xem chi tiết thông tin danh mục.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `categories` | `ADMIN`: xem theo `id` | `404 NOT FOUND` |
  | `categories` | `BUYER` / `SUPPLIER`: phải có `id` VÀ `status = 'ACTIVE'` (nếu bị Inactive sẽ trả về 404 để giấu dữ liệu) | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CategoryResponse`.
* **Mã thất bại**: `404 NOT FOUND`.

---

### 5.4. Cập nhật thông tin danh mục
* **Phương thức & URL**: `PUT /api/v1/categories/{id}`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Sửa tên và mô tả của danh mục.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `categories` | Tồn tại `id`, tên mới không được trùng danh mục khác | `404 NOT FOUND` / `409 CONFLICT` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CategoryResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`, `409 CONFLICT`.

---

### 5.5. Cập nhật trạng thái danh mục
* **Phương thức & URL**: `PATCH /api/v1/categories/{id}/status`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Bật/tắt trạng thái hiển thị của danh mục (`ACTIVE`, `INACTIVE`).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `categories` | Tồn tại `id`, trạng thái phải là `ACTIVE` hoặc `INACTIVE` | `404 NOT FOUND` / `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CategoryResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

## 6. MODULE QUẢN LÝ SẢN PHẨM
* **Base URL**: `/api/v1/products`  
* **Controller**: `ProductController.java`

---

### 6.1. Tạo sản phẩm mới
* **Phương thức & URL**: `POST /api/v1/products`
* **Quyền sử dụng**: `ADMIN`, `SUPPLIER`
* **Tác dụng**: Đăng tải sản phẩm mới lên sàn.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `companies` | Nếu là Supplier: Tự động lấy `currentUser.companyId`, công ty phải là `SUPPLIER` | `400 BAD REQUEST` |
  | `companies` | Nếu là Admin: `supplierCompanyId` bắt buộc có và là loại `SUPPLIER` | `400 BAD REQUEST` / `404` |
  | `categories` | `categoryId` phải tồn tại VÀ `status = 'ACTIVE'` | `400 BAD REQUEST` / `404` |
  | `products` | Mã `sku` không trùng lặp toàn hệ thống (`uk_products_sku`) | `409 CONFLICT` (SKU already exists) |
  | `products` | `stock_quantity >= 0` | `400 BAD REQUEST` |
* **Mã thành công**: `201 CREATED` $\rightarrow$ Trả về `ProductResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`, `409 CONFLICT`.

---

### 6.2. Tra cứu & Phân trang danh sách sản phẩm
* **Phương thức & URL**: `GET /api/v1/products`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Lọc sản phẩm theo từ khóa, danh mục, nhà cung cấp, trạng thái.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | `ADMIN`: Xem được toàn bộ sản phẩm của tất cả Supplier | `401 UNAUTHORIZED` |
  | `products` | `SUPPLIER`: Chỉ xem được sản phẩm do công ty mình sở hữu (`supplier_company_id = currentUser.companyId`) | `401 UNAUTHORIZED` |
  | `products`, `categories` | `BUYER`: Bắt buộc chỉ hiển thị sản phẩm `ACTIVE` có danh mục `ACTIVE` | `401 UNAUTHORIZED` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `PageResponse<ProductResponse>`.
* **Mã thất bại**: `401 UNAUTHORIZED`.

---

### 6.3. Xem chi tiết sản phẩm theo ID
* **Phương thức & URL**: `GET /api/v1/products/{id}`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Xem thông tin chi tiết một sản phẩm.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | Tồn tại bản ghi theo `id` | `404 NOT FOUND` |
  | Phân quyền | `SUPPLIER`: Nếu sản phẩm không thuộc công ty mình $\rightarrow$ chặn quyền | `403 FORBIDDEN` |
  | Phân quyền | `BUYER`: Nếu sản phẩm hoặc danh mục không `ACTIVE` $\rightarrow$ trả về 404 | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `ProductResponse`.
* **Mã thất bại**: `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 6.4. Cập nhật thông tin sản phẩm
* **Phương thức & URL**: `PUT /api/v1/products/{id}`
* **Quyền sử dụng**: `ADMIN`, `SUPPLIER`
* **Tác dụng**: Chỉnh sửa tên, mô tả, ảnh, tồn kho, SKU, danh mục của sản phẩm.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | Tồn tại bản ghi; Supplier chỉ được sửa sản phẩm của công ty mình | `403 FORBIDDEN` / `404 NOT FOUND` |
  | `products` | Nếu đổi `sku`: SKU mới không trùng sản phẩm khác | `409 CONFLICT` |
  | `categories` | Nếu đổi `categoryId`: Danh mục mới phải tồn tại và `status = 'ACTIVE'` | `400 BAD REQUEST` / `404` |
  | `products` | `stock_quantity >= 0` | `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `ProductResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`, `409 CONFLICT`.

---

### 6.5. Cập nhật trạng thái sản phẩm
* **Phương thức & URL**: `PATCH /api/v1/products/{id}/status`
* **Quyền sử dụng**: `ADMIN`, `SUPPLIER`
* **Tác dụng**: Bật/tắt kinh doanh sản phẩm (`ACTIVE`, `INACTIVE`).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | Tồn tại `id`; kiểm tra quyền sở hữu đối với Supplier | `403 FORBIDDEN` / `404 NOT FOUND` |
  | `products` | `status` gửi lên phải là `ACTIVE` hoặc `INACTIVE` | `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `ProductResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

## 7. MODULE QUẢN LÝ BẬC GIÁ THEO SỐ LƯỢNG
* **Base URL**: `/api/v1/products/{productId}/prices`  
* **Controller**: `ProductPriceController.java`

---

### 7.1. Thêm bậc giá số lượng cho sản phẩm
* **Phương thức & URL**: `POST /api/v1/products/{productId}/prices`
* **Quyền sử dụng**: `ADMIN`, `SUPPLIER`
* **Tác dụng**: Thiết lập bảng giá bán buôn/sỉ theo số lượng (Tier Pricing).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | Tồn tại `productId`; kiểm tra quyền sở hữu với Supplier | `403 FORBIDDEN` / `404 NOT FOUND` |
  | `product_prices`| `min_quantity >= 1` và `unit_price > 0` | `400 BAD REQUEST` |
  | `product_prices`| Nếu có `max_quantity`: Bắt buộc `max_quantity >= min_quantity` | `400 BAD REQUEST` |
  | `product_prices`| **Quy tắc không chồng lấn**: Khoảng `[min, max]` không được giao thoa với bất kỳ bậc giá nào trước đó của sản phẩm này | `400 BAD REQUEST` (Tier overlaps with existing tier) |
* **Mã thành công**: `201 CREATED` $\rightarrow$ Trả về `ProductPriceResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 7.2. Xem danh sách bậc giá của sản phẩm
* **Phương thức & URL**: `GET /api/v1/products/{productId}/prices`
* **Quyền sử dụng**: `Authenticated`
* **Tác dụng**: Xem danh sách thang giá theo số lượng tăng dần.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | Tồn tại `productId`; Buyer chỉ xem được nếu sản phẩm & danh mục `ACTIVE` | `404 NOT FOUND` |
  | `product_prices`| Lấy toàn bộ theo `product_id`, sắp xếp `ORDER BY min_quantity ASC` | `200 OK` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về mảng `List<ProductPriceResponse>`.
* **Mã thất bại**: `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 7.3. Cập nhật một bậc giá
* **Phương thức & URL**: `PUT /api/v1/products/{productId}/prices/{priceId}`
* **Quyền sử dụng**: `ADMIN`, `SUPPLIER`
* **Tác dụng**: Sửa khoảng số lượng và đơn giá của bậc giá.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `product_prices`| `priceId` tồn tại và trỏ đúng vào `productId` (`price.product.id == productId`) | `400 BAD REQUEST` / `404` |
  | `product_prices`| Khoảng giá mới không được chồng chéo với các tier còn lại của cùng sản phẩm | `400 BAD REQUEST` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `ProductPriceResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 7.4. Xóa một bậc giá
* **Phương thức & URL**: `DELETE /api/v1/products/{productId}/prices/{priceId}`
* **Quyền sử dụng**: `ADMIN`, `SUPPLIER`
* **Tác dụng**: Xóa bậc giá không còn áp dụng.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `product_prices`| `priceId` tồn tại và thuộc đúng sản phẩm `productId` | `400 BAD REQUEST` / `404` |
* **Mã thành công**: `200 OK` (Message: "Price tier deleted successfully").
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

## 8. MODULE QUẢN LÝ GIỎ HÀNG
* **Base URL**: `/api/v1/cart`  
* **Controller**: `CartController.java`

---

### 8.1. Xem giỏ hàng của Buyer hiện tại
* **Phương thức & URL**: `GET /api/v1/cart`
* **Quyền sử dụng**: `BUYER` (Chỉ Buyer mới sở hữu giỏ hàng)
* **Tác dụng**: Lấy danh sách item trong giỏ, tự động tính đơn giá thực tế theo số lượng mua dựa vào bậc giá (Tier Pricing) và tính tổng tiền giỏ hàng.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `users` | User đăng nhập phải có vai trò `BUYER` | `403 FORBIDDEN` |
  | `carts` | Lấy giỏ hàng 1:1 theo `user_id` | Tự trả về giỏ rỗng nếu chưa có |
  | `products`, `categories` | Kiểm tra tính khả dụng `available = (product.status == ACTIVE && category.status == ACTIVE && stock >= item.quantity)` | Hiển thị cờ `available: true/false` |
  | `product_prices` | Lấy các tier giá của sản phẩm để tự động khớp `unit_price` chính xác nhất | Tự tính `unitPrice` và `subtotal` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CartResponse`.
* **Mã thất bại**: `401 UNAUTHORIZED`, `403 FORBIDDEN`.

---

### 8.2. Thêm sản phẩm vào giỏ hàng
* **Phương thức & URL**: `POST /api/v1/cart/items`
* **Quyền sử dụng**: `BUYER`
* **Tác dụng**: Thêm sản phẩm vào giỏ hàng (nếu đã có thì cộng dồn số lượng).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `products` | Sản phẩm phải tồn tại và `status = 'ACTIVE'` | `400 BAD REQUEST` / `404` |
  | `categories` | Danh mục sản phẩm phải có `status = 'ACTIVE'` | `400 BAD REQUEST` |
  | `products` | Tổng số lượng sau khi cộng dồn không được vượt quá tồn kho `stock_quantity` | `400 BAD REQUEST` (Exceeds available stock) |
  | `carts` | Tự động tạo bản ghi trong bảng `carts` nếu user chưa có | Thành công |
  | `cart_items` | Lưu hoặc cộng dồn theo ràng buộc duy nhất `uk_cart_items_cart_product` | Thành công |
* **Mã thành công**: `201 CREATED` $\rightarrow$ Trả về `CartItemResponse`.
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 8.3. Cập nhật số lượng sản phẩm trong giỏ
* **Phương thức & URL**: `PUT /api/v1/cart/items/{productId}`
* **Quyền sử dụng**: `BUYER`
* **Tác dụng**: Điều chỉnh số lượng một mặt hàng trong giỏ.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `cart_items` | Sản phẩm `productId` phải đang có trong giỏ của Buyer | `404 NOT FOUND` |
  | `products` | Sản phẩm và danh mục phải `ACTIVE` | `400 BAD REQUEST` |
  | `products` | Số lượng mới `quantity <= stock_quantity` | `400 BAD REQUEST` (Exceeds available stock) |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `CartItemResponse` (tính lại đơn giá theo tier).
* **Mã thất bại**: `400 BAD REQUEST`, `404 NOT FOUND`.

---

### 8.4. Xóa một sản phẩm khỏi giỏ hàng
* **Phương thức & URL**: `DELETE /api/v1/cart/items/{productId}`
* **Quyền sử dụng**: `BUYER`
* **Tác dụng**: Xóa mặt hàng khỏi giỏ.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `cart_items` | Tìm theo `cart_id` và `product_id`. Xóa bản ghi nếu tìm thấy | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` (Message: "Item removed from cart successfully").
* **Mã thất bại**: `404 NOT FOUND`.

---

### 8.5. Xóa rỗng giỏ hàng (Clear Cart)
* **Phương thức & URL**: `DELETE /api/v1/cart`
* **Quyền sử dụng**: `BUYER`
* **Tác dụng**: Xóa toàn bộ sản phẩm trong giỏ hàng.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `cart_items` | Gọi lệnh xóa toàn bộ bản ghi theo `cart_id` của user | Thành công |
* **Mã thành công**: `200 OK` (Message: "Cart cleared successfully").
* **Mã thất bại**: `401 UNAUTHORIZED`, `403 FORBIDDEN`.

---

## 9. MODULE CẤU HÌNH HỆ THỐNG
* **Base URL**: `/api/v1/settings`  
* **Controller**: `SystemSettingController.java`

---

### 9.1. Lấy toàn bộ tham số cấu hình hệ thống
* **Phương thức & URL**: `GET /api/v1/settings`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Quản trị viên xem tất cả cấu hình tham số hệ thống (thời gian timeout thanh toán, thời gian duyệt đơn...).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | Phân quyền | Chỉ người dùng có vai trò `ADMIN` | `403 FORBIDDEN` |
  | `system_settings` | Lấy toàn bộ bản ghi theo thứ tự `ORDER BY id ASC` | `200 OK` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về mảng `List<SystemSettingResponse>`.
* **Mã thất bại**: `401 UNAUTHORIZED`, `403 FORBIDDEN`.

---

### 9.2. Xem tham số cấu hình theo Key
* **Phương thức & URL**: `GET /api/v1/settings/{key}`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Xem chi tiết một tham số cấu hình cụ thể (ví dụ: `PAYMENT_TIMEOUT_MINUTES`).
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `system_settings` | Tìm kiếm theo cột `setting_key` | `404 NOT FOUND` (Setting not found) |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `SystemSettingResponse`.
* **Mã thất bại**: `403 FORBIDDEN`, `404 NOT FOUND`.

---

### 9.3. Cập nhật giá trị tham số cấu hình
* **Phương thức & URL**: `PUT /api/v1/settings/{key}`
* **Quyền sử dụng**: `ADMIN`
* **Tác dụng**: Quản trị viên điều chỉnh tham số hệ thống.
* **Kiểm tra điều kiện & Bảng dữ liệu**:
  | Bảng | Cột / Điều kiện kiểm tra | Kết quả khi vi phạm |
  |---|---|---|
  | `system_settings` | Tìm kiếm theo cột `setting_key` | `404 NOT FOUND` |
  | Nghiệp vụ | Giá trị `setting_value` không được rỗng | `400 BAD REQUEST` |
  | Nghiệp vụ | Với `PAYMENT_TIMEOUT_MINUTES` & `SUPPLIER_CONFIRM_TIMEOUT_HOURS`: Giá trị phải là số nguyên dương $> 0$ | `400 BAD REQUEST` |
  | `users` | Lưu thông tin người sửa đổi vào cột `updated_by` | `404 NOT FOUND` |
* **Mã thành công**: `200 OK` $\rightarrow$ Trả về `SystemSettingResponse` (kèm `updatedBy` và `updatedAt`).
* **Mã thất bại**: `400 BAD REQUEST`, `403 FORBIDDEN`, `404 NOT FOUND`.

---

## 10. MA TRẬN PHÂN QUYỀN RBAC

> **Ký hiệu**:  
> * `✓` : Có toàn quyền truy cập.  
> * `(Own)` : Chỉ được thao tác với tài nguyên thuộc công ty / tài khoản của chính mình.  
> * `(Active)` : Chỉ được xem các bản ghi đang ở trạng thái `ACTIVE`.  
> * `-` : Không có quyền truy cập (Bị chặn `401` hoặc `403`).

| STT | Endpoint | Method | Public | Authenticated | BUYER | SUPPLIER | ADMIN |
|:---:|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **1** | `/api/v1/auth/register` | `POST` | ✓ | - | - | - | - |
| **2** | `/api/v1/auth/login` | `POST` | ✓ | - | - | - | - |
| **3** | `/api/v1/auth/oauth2/register` | `POST` | ✓ | - | - | - | - |
| **4** | `/api/v1/auth/oauth2/link` | `POST` | - | ✓ | ✓ | ✓ | ✓ |
| **5** | `/api/v1/users/me` | `GET` | - | ✓ | ✓ | ✓ | ✓ |
| **6** | `/api/v1/users/me` | `PUT` | - | ✓ | ✓ | ✓ | ✓ |
| **7** | `/api/v1/users/me/password` | `PATCH` | - | ✓ | ✓ | ✓ | ✓ |
| **8** | `/api/v1/users` | `GET` | - | - | - | - | ✓ |
| **9** | `/api/v1/users/{id}` | `GET` | - | - | - | - | ✓ |
| **10**| `/api/v1/users/{id}` | `PUT` | - | - | - | - | ✓ |
| **11**| `/api/v1/users/{id}/status` | `PATCH` | - | - | - | - | ✓ |
| **12**| `/api/v1/companies/me` | `GET` | - | ✓ | ✓ | ✓ | - |
| **13**| `/api/v1/companies/{id}` | `GET` | - | (Own) | (Own) | (Own) | ✓ |
| **14**| `/api/v1/companies` | `GET` | - | - | - | - | ✓ |
| **15**| `/api/v1/companies/me` | `PUT` | - | - | ✓ | ✓ | - |
| **16**| `/api/v1/companies/{id}` | `PUT` | - | - | - | - | ✓ |
| **17**| `/api/v1/companies/{id}/status` | `PATCH` | - | - | - | - | ✓ |
| **18**| `/api/v1/categories` | `POST` | - | - | - | - | ✓ |
| **19**| `/api/v1/categories` | `GET` | - | - | (Active) | (Active) | ✓ |
| **20**| `/api/v1/categories/{id}` | `GET` | - | - | (Active) | (Active) | ✓ |
| **21**| `/api/v1/categories/{id}` | `PUT` | - | - | - | - | ✓ |
| **22**| `/api/v1/categories/{id}/status` | `PATCH` | - | - | - | - | ✓ |
| **23**| `/api/v1/products` | `POST` | - | - | - | (Own) | ✓ |
| **24**| `/api/v1/products` | `GET` | - | - | (Active) | (Own) | ✓ |
| **25**| `/api/v1/products/{id}` | `GET` | - | - | (Active) | (Own) | ✓ |
| **26**| `/api/v1/products/{id}` | `PUT` | - | - | - | (Own) | ✓ |
| **27**| `/api/v1/products/{id}/status` | `PATCH` | - | - | - | (Own) | ✓ |
| **28**| `/api/v1/products/{id}/prices` | `POST` | - | - | - | (Own) | ✓ |
| **29**| `/api/v1/products/{id}/prices` | `GET` | - | - | (Active) | (Own) | ✓ |
| **30**| `/api/v1/products/{id}/prices/{priceId}` | `PUT` | - | - | - | (Own) | ✓ |
| **31**| `/api/v1/products/{id}/prices/{priceId}` | `DELETE` | - | - | - | (Own) | ✓ |
| **32**| `/api/v1/cart` | `GET` | - | - | ✓ | - | - |
| **33**| `/api/v1/cart/items` | `POST` | - | - | ✓ | - | - |
| **34**| `/api/v1/cart/items/{productId}` | `PUT` | - | - | ✓ | - | - |
| **35**| `/api/v1/cart/items/{productId}` | `DELETE` | - | - | ✓ | - | - |
| **36**| `/api/v1/cart` | `DELETE` | - | - | ✓ | - | - |
| **37**| `/api/v1/settings` | `GET` | - | - | - | - | ✓ |
| **38**| `/api/v1/settings/{key}` | `GET` | - | - | - | - | ✓ |
| **39**| `/api/v1/settings/{key}` | `PUT` | - | - | - | - | ✓ |
