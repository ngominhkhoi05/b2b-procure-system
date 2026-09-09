# B2B Procure System

Hệ thống B2B E-Procurement (Thu mua trực tuyến cho doanh nghiệp) được xây dựng trên nền tảng Spring Boot 3+ / Java 21, hỗ trợ quy trình chào thầu, đặt hàng, quản lý danh mục, doanh nghiệp và tính toán hoa hồng.

---

## 📁 Cấu trúc thư mục dự án

```text
b2b-procure-system/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── b2bprocure/
│   │   │           └── system/
│   │   │               │
│   │   │               ├── B2bProcureSystemApplication.java
│   │   │               │
│   │   │               ├── config/
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   ├── JwtConfig.java
│   │   │               │   ├── JpaConfig.java
│   │   │               │   └── OpenApiConfig.java
│   │   │               │
│   │   │               ├── security/
│   │   │               │   ├── JwtAuthenticationFilter.java
│   │   │               │   ├── JwtTokenProvider.java
│   │   │               │   └── CustomUserDetailsService.java
│   │   │               │
│   │   │               ├── common/
│   │   │               │   ├── constant/
│   │   │               │   ├── enums/
│   │   │               │   ├── exception/
│   │   │               │   ├── response/
│   │   │               │   └── util/
│   │   │               │
│   │   │               ├── auth/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── user/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── company/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── category/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── product/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── cart/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── order/
│   │   │               │   ├── controller/
│   │   │               │   ├── dto/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               ├── commission/
│   │   │               │   ├── entity/
│   │   │               │   ├── repository/
│   │   │               │   └── service/
│   │   │               │
│   │   │               └── admin/
│   │   │                   ├── controller/
│   │   │                   ├── dto/
│   │   │                   └── service/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       │
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── b2bprocure/
│                   └── system/
│
├── .gitignore
├── README.md
└── build.gradle
```

---

## 🛠️ Công nghệ sử dụng

- **Ngôn ngữ**: Java 21 LTS
- **Framework**: Spring Boot 3+ (Spring WebMVC, Spring Data JPA, Spring Security, Validation)
- **Xác thực & Phân quyền**: JWT (io.jsonwebtoken), Spring Security Filter Chain
- **Tài liệu API**: OpenAPI 3 / Swagger UI (`org.springdoc`)
- **Cơ sở dữ liệu**: PostgreSQL
- **Build Tool**: Gradle (Kotlin/Groovy DSL)
- **Tối ưu code**: Lombok

---

## 🚀 Hướng dẫn cài đặt & Khởi chạy

### 1. Yêu cầu hệ thống
- JDK 21 trở lên
- Gradle 8+ hoặc sử dụng `./gradlew` đi kèm
- PostgreSQL 14+

### 2. Cấu hình cơ sở dữ liệu
Tạo database PostgreSQL cho môi trường dev:
```sql
CREATE DATABASE b2b_procure_dev;
```

Cập nhật thông tin kết nối trong `src/main/resources/application-dev.yml` nếu cần (username/password mặc định: `postgres`/`postgres`).

### 3. Chạy ứng dụng

Môi trường Development (mặc định):
```bash
./gradlew bootRun
```

Hoặc chỉ định profile Prod:
```bash
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### 4. Truy cập tài liệu Swagger UI
Sau khi khởi động ứng dụng thành công:
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON Docs**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🏛️ Kiến trúc module (Package-by-Feature)

| Module | Mô tả nhiệm vụ |
|---|---|
| `config` | Cấu hình Security, JWT Properties, JPA Auditing, Swagger OpenAPI |
| `security` | Filter xác thực JWT, Provider mã hoá và kiểm tra token, UserDetailsService |
| `common` | Các constant dùng chung, enum hệ thống, custom exception, API response wrapper, utility helpers |
| `auth` | Xử lý đăng nhập, đăng ký, refresh token |
| `user` | Quản lý tài khoản người dùng, phân quyền (Buyer, Supplier, Admin) |
| `company` | Quản lý hồ sơ công ty, chứng nhận, mã số thuế doanh nghiệp |
| `category` | Danh mục phân loại sản phẩm đa cấp |
| `product` | Quản lý sản phẩm, tồn kho, bảng giá sỉ B2B |
| `cart` | Giỏ hàng B2B, quản lý báo giá sơ bộ |
| `order` | Đơn hàng B2B, hợp đồng mua hàng, trạng thái xử lý |
| `commission` | Tính toán hoa hồng sàn, đối soát công nợ |
| `admin` | Dashboard quản trị hệ thống, duyệt tài khoản, báo cáo tổng thể |
"# b2b-procure-system" 
