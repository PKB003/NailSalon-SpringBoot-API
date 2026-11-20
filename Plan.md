# Plan.md — Đánh giá Phase 1 & Kế hoạch hoàn thiện Backend Nail Salon

> **Ngày lập:** 2026-11-14  
> **Cập nhật gần nhất:** 2026-11-14 — **CHỐT FINAL D1–D18**: D7/D12 loyalty Phương án 3 (ngưỡng 300/800 EUR), D15 tip không VAT, D16 §19 hiện tại + switchable, **D17 TSE = fiskaly SIGN DE (cloud TSE)**; bổ sung runbook thuế/TSE từng bước (mục 5.8).  
> **Phạm vi:** Backend `booking-service` (Spring Boot 3.2.2 / Java 17 / MySQL)  
> **Căn cứ đánh giá:** đọc toàn bộ source `src/main/java`, cấu hình, Docker, tài liệu API và chạy `mvnw test`.  
> **Kết quả test hiện tại:** `Tests run: 3, Failures: 0, Errors: 0` — build OK, nhưng độ phủ nghiệp vụ còn thấp.  
> **Trạng thái tài liệu:** **FINAL — đã chốt nội bộ ngày 2026-11-14** (biên bản mục 15.1). Còn chờ chữ ký sign-off (mục 15) và xác nhận pháp lý cuối cùng của Steuerberater trước go-live.

---

## 0. Kết luận nhanh (Executive Summary)

### 0.1. Mức độ đáp ứng

| Phạm vi | Đánh giá | Ghi chú |
|---|---|---|
| **Phase 1 hiện tại (code đã có)** | **~30–35% MVP** | Có auth, service catalog cơ bản, booking đơn lẻ, admin dashboard, state machine booking. |
| **Toàn bộ nghiệp vụ yêu cầu** | **~20–22%** | Thiếu gần như toàn bộ Staff/Scheduling, POS/Payment, Loyalty, Promotion, Review, Notification, Walk-in, Reporting nâng cao. |
| **Mức độ sẵn sàng production** | **~35%** | Chưa có migration, CI/CD, observability, audit, test concurrency, phân quyền đủ actor. |

> **Kết luận:** Phase 1 **chưa phải backend hoàn thiện**. Đây là một **vertical slice đầu tiên chạy được end-to-end** (login → xem dịch vụ → đặt lịch → admin xử lý → thống kê), nhưng **lõi khó nhất của bài toán nail (availability theo thợ + duration + skill + buffer, chống double-booking thật, POS + loyalty + payment) chưa được triển khai**.

### 0.2. Các quyết định đã chốt — **FINAL** (2026-11-14)

| # | Quyết định | Đã chốt | Tác động tới kế hoạch |
|---|---|---|---|
| D1 | Thị trường & ngôn ngữ | ✅ **Đức — EUR, giao diện/hoá đơn tiếng Đức** | Toàn bộ tiền tệ `BigDecimal` EUR; seed/message giữ tiếng Đức; API vẫn trả `errorCode` để FE map ngôn ngữ |
| D2 | Mô hình chi nhánh | ✅ **1 chi nhánh MVP**, schema có `branch_id` + `timezone` từ đầu | Không làm UI multi-branch; mọi bảng nghiệp vụ có `branch_id` |
| D3 | Nhân viên | ✅ **Entity `staff` riêng** (optional link `user`), role `NAIL_TECH`, `RECEPTIONIST`, `MANAGER` | S1 làm staff/shift/skill; RBAC mở rộng |
| D4 | Cọc / hủy | ✅ **Chốt phương án tối ưu**: MVP **không cọc**; hủy miễn phí khi còn ≥24h; trong 24h **WARN + ghi late-cancel, không chặn**; no-show chỉ đánh dấu, chưa thu phí. Admin có thể bật `FEE`/`BLOCK`/deposit sau bằng cấu hình | Bảng `cancellation_policy`, `deposit_policy`, `cancellation_record`; dữ liệu late-cancel/no-show dùng để chốt policy sau 2–3 tháng |
| D5 | Giờ mở cửa & buffer | ✅ **T2–T7 09:00–18:00, buffer 10 phút, Chủ nhật nghỉ** | Cấu hình theo branch + weekday; seed shift mẫu |
| D6 | Thanh toán | ✅ **MVP: tiền mặt + thẻ tại quầy, thanh toán sau khi xong dịch vụ**; bổ sung **chuyển khoản**; **Stripe để mở rộng tương lai** | MVP POS ghi nhận payment method, không cần gateway online; Stripe adapter ở Phase 3 |
| D7 | Tích điểm | ✅ **CHỐT FINAL — Phương án 3** — điểm cố định 0,05 EUR + hạng Silver/Gold/Platinum, hệ số 1,0x/1,25x/1,5x, cap 10/15/20%, bỏ giới hạn bill <100 EUR | S4 triển khai đầy đủ ledger + tier + config; xem mục 5.4 |
| D8 | Tip & commission | ✅ **Tip chia 80% thợ / 20% tiệm**; commission dịch vụ theo cấu hình | Bảng `tip_allocation`, báo cáo commission; xử lý thuế theo D15 |
| D9 | Multi-service | ✅ **Chọn phương án tối ưu**: tuần tự, tối đa 3 dịch vụ, **ưu tiên cùng 1 thợ**, fallback nhiều thợ có handoff; auto-assign khi khách chọn "any" | Availability Engine xếp lịch liên tiếp + buffer; scoring tối ưu (mục 5.1) |
| D10 | Notification | ✅ **Email + SMS** cho MVP; Zalo OA Phase 3 | Outbox + adapter email/SMS; template tiếng Đức |
| D11 | **Thuế / kế toán** | ✅ **Ưu tiên "dễ khai thuế"**: hoá đơn tuân thủ §14 UStG, GoBD, DATEV export, TSE | Thêm module `tax` + `invoice`; S3 mở rộng thêm 3–5 ngày; xem mục 5.6 |
| D12 | **Chương trình loyalty & hạng khách** | ✅ **CHỐT FINAL — Phương án 3** — triển khai tier ngay trong MVP | S4 làm loyalty + tier + referral + QR + voucher |
| D13 | **Kênh thanh toán mở rộng** | ✅ Chuyển khoản (ghi nhận thủ công) trong MVP; Stripe (Checkout/Terminal) Phase 3 | Thiết kế `PaymentGateway` adapter ngay từ S3 để cắm Stripe sau |
| D14 | **Hoá đơn & lưu trữ** | ✅ Hoá đơn PDF + số tuần tự không trống; lưu 10 năm; không sửa hoá đơn đã phát hành (chỉ Storno) | Bảng `invoice`, `invoice_sequence`, `audit_log`; chính sách immutability |
| D15 | **Tip & thuế** | ✅ **CHỐT FINAL — Tip không chịu VAT**; tách khỏi doanh thu dịch vụ, không tính điểm; phần 80/20 chỉ là phân chia nội bộ | Không đưa tip vào VAT base/invoice service lines; báo cáo tip riêng |
| D16 | **Kleinunternehmer §19 UStG** | ✅ **CHỐT FINAL — hiện tại áp dụng §19**, thiết kế **switchable** vì tương lai có thể mở thêm tiệm/vượt ngưỡng | `tax.mode` theo legal entity + hiệu lực ngày; chuyển sang `STANDARD_19` không cần sửa code; hoá đơn cũ giữ nguyên; runbook 5.6.9 |
| D17 | **TSE / POS provider** | ✅ **CHỐT FINAL — fiskaly SIGN DE (cloud TSE)**: REST API, có sandbox, không cần phần cứng, hợp kiến trúc backend; dự phòng hardware TSE nếu tiệm mất mạng thường xuyên | Thêm module `tse` + bảng `tse_transaction`; export DSFinV-K; hướng dẫn tích hợp 5.8.3 |
| D18 | **Ngưỡng hạng loyalty** | ✅ **CHỐT FINAL**: Silver 0–299 / Gold 300–799 / Platinum ≥800 EUR trong 12 tháng; hệ số 1,0/1,25/1,5; cap 10/15/20% | Seed `loyalty_program_config`; tier engine ở S4 |

> **Trạng thái:** D1–D18 **đã chốt ngày 2026-11-14** (biên bản mục 15.1). Không mở lại trừ khi có thay đổi pháp lý, yêu cầu của Finanzamt/Steuerberater hoặc chủ tiệm đổi mô hình kinh doanh — khi đó tạo version mới của Plan kèm ngày hiệu lực.

---

## 1. Những gì Phase 1 đã làm được (đã verify)

### 1.1. Chức năng đã có

| Module | Đã có | File chính |
|---|---|---|
| Auth | Đăng ký, đăng nhập bằng email/SĐT, JWT HS512, BCrypt(12), logout blacklist, refresh, đổi mật khẩu, profile `/auth/me` | `users/service/AuthService.java`, `security/JwtUtil.java`, `config/SecurityConfig.java` |
| Phân quyền | Role `ADMIN` / `USER`; `@PreAuthorize`; CORS; public GET services/availability; public POST booking | `config/SecurityConfig.java`, `users/service/UserService.java` |
| Service catalog | CRUD dịch vụ (name, description, duration, price, category, popularity), seed 6 dịch vụ | `services/*` |
| Booking | Tạo booking guest/user, danh sách, hủy, đổi trạng thái, state machine 4 trạng thái, chính sách hủy 24h cơ bản | `bookings/service/BookingService.java` |
| Availability | Trả slot 09:00–18:00 bước 30 phút, ẩn slot quá khứ | `BookingService.getAvailability()` |
| Chống trùng | Pessimistic lock trên các booking trong slot + capacity 10 | `BookingRepository.findLockedBySlotWindow()` |
| Khách hàng | Danh sách khách + số lần ghé + lần ghé cuối (admin) | `customers/service/CustomerService.java` |
| Thống kê | Dashboard: booking hôm nay, doanh thu tháng, tổng khách, top service, trend | `stats/service/StatsService.java` |
| Envelope | `{ code, message, timeStamp, data }`, giữ thêm `result` để tương thích | `common/dto/ApiResponse.java` |
| Test | 1 smoke test contract + 1 test user + 1 context load | `src/test/*` |
| Docker | Dockerfile + docker-compose MySQL 8.0.41 | `Dockerfile`, `docker-compose.yml` |

### 1.2. Điểm mạnh

- Cấu trúc package theo feature (`bookings`, `services`, `users`, `customers`, `stats`) — dễ mở rộng.
- Có state machine booking và guard terminal (`abgeschlossen`/`storniert` bất biến) — tư duy đúng hướng kế toán.
- Có API contract markdown + smoke test end-to-end — thuận lợi để frontend ghép.
- Không hardcode mật khẩu admin; đọc `ADMIN_PASSWORD`, fallback sinh random và log một lần.
- Có blacklist token + cron dọn token hết hạn.

---

## 2. Gap analysis theo module nghiệp vụ

Chú thích: ✅ đạt | ⚠️ một phần | ❌ chưa có

| # | Module yêu cầu | Trạng thái | Đã có | Thiếu / rủi ro chính |
|---|---|---|---|---|
| 2.1 | **Service catalog** | ⚠️ 35% | CRUD, duration, price, category, popularity, seed | Add-on, combo/package, ảnh, skill mapping, price matrix theo độ dài/thiết kế, soft-delete |
| 2.2 | **Appointment / Booking** | ⚠️ 25% | Đặt 1 dịch vụ, slot cố định 30', capacity 10, hủy 24h, state machine | Availability theo thợ + duration + skill + buffer; multi-service (D9); any-available; chống double-booking đúng; policy cọc/hủy config-driven (D4); reschedule; waitlist; reminder; no-show |
| 2.3 | **Walk-in / Queue** | ❌ 0% | — | Ticket, hàng đợi, ước tính chờ, gán thợ, trạng thái waiting/in-service/done |
| 2.4 | **Staff & Scheduling** | ❌ 5% | `Booking.employee` là **string tự do** | Entity staff, ca làm, ngày nghỉ, time-off, lịch trống realtime, commission, role thợ/lễ tân |
| 2.5 | **Customer / CRM** | ⚠️ 20% | Tên, email, SĐT, ngày sinh, lịch sử booking | Ghi chú dị ứng/form móng/màu yêu thích/thợ quen; loyalty; hạng thành viên; referral; QR check-in |
| 2.6 | **Payment / POS** | ❌ 5% | Chỉ hiển thị giá từ service | Checkout tại quầy (cash/card/bank transfer), split payment, tip 80/20, deposit (feature flag), refund/void, receipt/invoice, audit log, idempotency; Stripe Phase 3 |
| 2.7 | **Promotion & Discount** | ❌ 0% | — | Coupon, voucher, happy hour, first-time, birthday, stacking rule, usage limit, expiry |
| 2.8 | **Review & Feedback** | ❌ 0% | — | Review chỉ khi appointment completed, rating + ảnh, moderation, phản hồi tiệm |
| 2.9 | **Notification** | ❌ 0% | — | Xác nhận, reminder 24h/2h, hủy/đổi lịch, cộng điểm; email/SMS/Zalo OA/push |
| 2.10 | **Reporting & Analytics** | ⚠️ 20% | 5 chỉ số dashboard cơ bản | Doanh thu theo thợ/dịch vụ/ngày, occupancy, no-show rate, retention, top staff, commission report, **báo cáo thuế** |
| 2.11 | **Tax & Accounting (Đức)** | ❌ 0% | — | §19 hiện tại + switchable sang VAT 19%, hoá đơn §14 UStG, số hoá đơn tuần tự, GoBD immutability, DATEV export, TSE/KassenSichV, sổ quỹ, tip không VAT/commission report |
| 3 | **Actors / RBAC** | ⚠️ 40% | Guest, Customer, Admin | Nail tech, Receptionist, Manager/Owner; quyền theo chi nhánh; thợ chỉ thấy lịch của mình |
| 4 | **Flow A — đặt online** | ⚠️ 40% | Chọn 1 service → chọn slot → confirm → nhận id | Multi-service, chọn thợ, cọc online, notification, reminder |
| 5 | **Flow B — walk-in** | ❌ 0% | — | Toàn bộ |
| 6 | **Flow C — checkout** | ❌ 0% | — | Toàn bộ |
| 7 | **Flow D — QR loyalty** | ❌ 0% | — | Toàn bộ |
| 8 | **Flow E — hủy/đổi** | ⚠️ 35% | Hủy 24h, admin hủy mọi lúc, guard terminal | Đổi lịch có policy, hoàn cọc, giải phóng slot + notify waitlist |
| 9 | **Edge cases backend** | ⚠️ 15% | Có lock + terminal guard | Race đúng, timezone, refund điểm, voucher hết hạn, thợ nghỉ đột xuất, idempotency |

### 2.1. Bảng điểm tổng hợp

```
Tổng nghiệp vụ:        ████░░░░░░░░░░░░░░░░  ~21%
MVP Phase 1:           ███████░░░░░░░░░░░░░  ~32%
Sẵn sàng production:   ███████░░░░░░░░░░░░░  ~35%
```

---

## 3. Danh sách lỗi & rủi ro cần xử lý (ưu tiên P0 → P2)

> P0 = chặn release / sai nghiệp vụ tiền bạc hoặc slot; P1 = phải sửa trước khi mở cho khách thật; P2 = cải thiện trong 1–2 sprint.

| ID | Mức | Vấn đề | Vị trí | Hệ quả | Hướng sửa | Sprint |
|---|---|---|---|---|---|---|
| B-01 | **P0** | Availability không tính theo **thợ + duration + skill + buffer**; chỉ đếm capacity 10 booking / slot 30' | `bookings/service/BookingService.java:52-64, 229-249` | Không thể biết thợ nào rảnh; 1 thợ vẫn nhận 10 khách cùng lúc; dịch vụ 90' vẫn nhận slot 17:30 | Xây `AvailabilityEngine` mới (mục 5.1) | S1 |
| B-02 | **P0** | Không có entity **Staff / Shift / TimeOff / Skill**; `employee` chỉ là string | `bookings/entity/Booking.java:28-29` | Không có lịch thợ, ca làm, nghỉ phép, gán thợ, commission | Tạo schema staff + schedule (mục 6) | S1 |
| B-03 | **P0** | **Race condition**: lock query không khóa được "khoảng trống" khi chưa có row; không unique constraint | `BookingRepository.java:19-22`, `BookingService.validateSlot()` | 2 request đồng thời cùng thợ/cùng giờ đều pass → double booking | Khóa row `staff` (`SELECT ... FOR UPDATE`) hoặc bảng `slot_lock` unique; test 2 thread | S1 |
| B-04 | **P0** | `validateOpeningHours` chỉ check **giờ bắt đầu**, không check giờ kết thúc theo duration | `BookingService.java:67-75, 273-276` | Dịch vụ 90' đặt 17:30, kết thúc 19:00 ngoài giờ | Tính `end = start + duration + buffer`, check `end <= closing` | S1 |
| B-05 | **P1** | Legacy endpoints bỏ qua validate: `/booking/guest`, `/booking/user`, `PUT /booking/user/{id}` | `BookingController.java:39-66`, `BookingService.java:98-181` | Guest đặt ngày quá khứ / ngoài giờ; khách tự đổi lịch sát giờ, đổi dịch vụ, né policy 24h | Deprecate legacy, hoặc dùng chung 1 validation service | S0/S2 |
| B-06 | **P1** | Không lưu **giá snapshot**; giá booking lấy live từ bảng service | `BookingService.resolvePrice()` | Đổi giá dịch vụ làm sai doanh thu lịch sử | Thêm `price_snapshot`, `total_amount`, `currency` vào booking | S2 |
| B-07 | **P1** | Xóa service không kiểm tra booking tham chiếu; không FK | `services/service/ServiceService.java:47-53` | Booking mồ côi, mất tên/giá | Soft-delete + chặn xóa nếu có booking active | S2 |
| B-08 | **P1** | `GET /users` trả entity `User`, quan hệ 2 chiều `User.bookingList` ↔ `Booking.user` | `users/controller/UserController.java:20-23` | Nguy cơ vòng lặp JSON, lộ dữ liệu, payload lớn | Trả `UserDTO`, phân trang, bỏ legacy | S0 |
| B-09 | **P1** | Chưa có **audit log** cho hành động admin (sửa/xóa/đổi trạng thái/hoàn tiền) | Toàn hệ thống | Không truy vết khi mất tiền/điểm | Bảng `audit_log` append-only + AOP | S2 |
| B-10 | **P1** | `application.properties.example` dùng `jwt.secret-key` nhưng code đọc `JWT_SECRET_KEY`; thiếu `ADMIN_PASSWORD` | `src/main/resources/application.properties.example` | Deploy theo example → app không start / admin random | Sửa example khớp code, bổ sung biến | S0 |
| B-11 | **P1** | `ddl-auto=update`, không có Flyway/Liquibase | `application.properties` | Schema production không kiểm soát, khó rollback | Flyway baseline + migration versioned | S0 |
| B-12 | **P1** | Test quá ít: 3 test, 1 smoke test lớn; không test concurrency/availability edge case | `src/test/*` | Không phát hiện double booking, sai policy | Test pyramid + Testcontainers + JaCoCo gate | S0–S5 |
| B-13 | **P1** | Docker single-stage, base image `openjdk:17-jdk-slim` cũ, chạy root, không healthcheck, cần build jar trước | `Dockerfile` | Build không reproducible, image nặng, không an toàn | Multi-stage + `eclipse-temurin:21-jre-alpine` + non-root + HEALTHCHECK | S0 |
| B-14 | **P1** | Không có CI/CD, không có Actuator/metrics/log tracing | Repo | Deploy thủ công, không biết lỗi production | GitHub Actions + Actuator + Micrometer + JSON log + Sentry | S0 |
| B-15 | **P1** | Không rate limit / lockout login; chưa verify email/SĐT; chưa quên mật khẩu | `AuthController`, `SecurityConfig` | Brute force, spam booking guest, không khôi phục tài khoản | Rate limit (Bucket4j/Redis), OTP, reset flow | S2 |
| B-16 | **P2** | `CustomerService` N+1: mỗi user query booking riêng; `StatsService` load toàn bộ booking vào RAM | `customers/service/CustomerService.java:31-49`, `stats/service/StatsService.java:33` | Chậm khi dữ liệu lớn | Query aggregate + projection + pagination | S5 |
| B-17 | **P2** | Danh sách admin không phân trang/sort/filter | `bookings`, `customers`, `users` controllers | UI admin treo khi > vài nghìn bản ghi | Chuẩn phân trang `{content,page,size,totalElements,totalPages}` | S2 |
| B-18 | **P2** | Error semantics chưa chuẩn: `SLOT_FULL` HTTP 406 + code 666; message lẫn Đức/Anh | `ErrorCode.java:10` | Frontend khó map lỗi, không i18n được | Đổi 409 + mã lỗi ổn định; backend trả `errorCode`, frontend map message | S0 |
| B-19 | **P2** | Envelope trả **cả `result` và `data`** | `ApiResponse.java:27-30` | Payload gấp đôi, dễ lệch hợp đồng | Chốt 1 field `data`, deprecate `result` có lộ trình | S1 |
| B-20 | **P2** | Thời gian dùng `LocalDateTime`, phụ thuộc timezone server; chưa có `branch.timezone` | Toàn hệ thống | Sai slot nếu đổi server/timezone hoặc multi-branch | Lưu UTC + `zoneId` của branch; convert ở API | S3 |
| B-21 | **P2** | Chưa có idempotency cho POST booking/checkout | Booking/Payment | Retry mạng → double booking/trừ tiền 2 lần | `Idempotency-Key` header + bảng unique key | S1 (booking), S3 (payment) |
| B-22 | **P2** | Booking mới set `in Bearbeitung` (đang thực hiện) thay vì `pending/confirmed` | `BookingService.java:287` | Sai ngữ nghĩa state machine; dashboard lệch | State machine mới: `PENDING_PAYMENT → CONFIRMED → CHECKED_IN → IN_SERVICE → COMPLETED` | S2 |
| B-23 | **P2** | Không có API tra cứu booking guest bằng mã/SĐT | — | Guest mất mã không xem được lịch | `GET /bookings/lookup?code=&phone=` (rate limit + OTP) | S2 |
| B-24 | **P2** | Không có OpenAPI machine-readable | — | Frontend phải đọc markdown, dễ lệch | springdoc-openapi + publish `/v3/api-docs` | S0 |

---

## 4. Kiến trúc & domain model đích

### 4.1. Package structure đề xuất

```
com.ttnails.booking_service
├── auth/                 # login, register, token, OTP, password reset
├── user/                 # tài khoản, profile, role
├── staff/                # staff, shift, time-off, skill, commission
├── catalog/              # service, category, add-on, combo, price matrix
├── booking/              # appointment, availability engine, waitlist, policy
├── pos/                  # checkout, payment, invoice, refund
├── loyalty/              # point ledger, tier, QR token, referral
├── promotion/            # voucher, campaign, stacking rule
├── review/               # review, moderation, reply
├── notification/         # template, outbox, channel adapter
├── reporting/            # dashboard, revenue, occupancy, commission
├── branch/               # chi nhánh, giờ mở cửa, timezone
└── common/               # response, exception, audit, pagination, i18n
```

### 4.2. State machine Appointment (chuẩn production)

```
PENDING_PAYMENT ──pay──> CONFIRMED ──check-in──> CHECKED_IN ──start──> IN_SERVICE ──done──> COMPLETED
       │                    │                        │                     │
       │ timeout            │ cancel                 │ no-show             │ cancel (admin)
       ▼                    ▼                        ▼                     ▼
   EXPIRED              CANCELLED                 NO_SHOW              CANCELLED
```

Quy tắc:
- `COMPLETED`, `CANCELLED`, `NO_SHOW`, `EXPIRED` là **terminal** — bất biến kế toán.
- Mọi transition ghi `appointment_status_history` (from, to, actor, reason, timestamp).
- Reschedule = tạo `appointment_version` mới + giữ lịch sử, không sửa đè.

### 4.3. ERD mức cao (các bảng mới)

```
branch(id, name, timezone, open_time, close_time, phone, address)
staff(id, user_id?, branch_id, display_name, color, active, commission_rate)
staff_skill(staff_id, service_id, level)
shift(id, staff_id, work_date, start_time, end_time)
time_off(id, staff_id, start_at, end_at, reason, status)
service(id, category_id, name, duration, buffer_minutes, price, currency, active, image_url)
service_addon(id, service_id, name, duration, price)
service_combo(id, name, price, active)
combo_item(combo_id, service_id)
booking(id, branch_id, customer_id?, staff_id?, status, start_at, end_at, buffer_end_at,
        subtotal, discount, tip, total, net_amount, vat_amount, gross_amount, currency,
        deposit_amount, price_snapshot_json, policy_snapshot_json,
        idempotency_key, confirmation_code, created_at, updated_at)
booking_item(id, booking_id, service_id, staff_id?, start_at, end_at, addon_json,
             duration, price, net, vat_rate, vat_amount, gross)
booking_status_history(id, booking_id, from_status, to_status, actor_id, reason, created_at)
booking_version(id, booking_id, version_no, snapshot_json, created_at, created_by)
cancellation_policy(id, branch_id, service_id?, free_cancel_hours, late_cancel_action,
                    late_cancel_fee_type, late_cancel_fee_value, no_show_fee_type,
                    no_show_fee_value, valid_from, valid_to)
deposit_policy(id, branch_id, service_id?, required, deposit_type, deposit_value,
               min_service_duration, valid_from, valid_to)
cancellation_record(id, booking_id, requested_by, reason, policy_snapshot,
                    fee_amount, refund_amount, created_at)
waitlist(id, branch_id, service_id, desired_date, customer_id, phone, status, notified_at)
payment(id, booking_id, amount, currency, method, provider, provider_txn_id,
        status, idempotency_key, paid_at)
payment_split(id, payment_id, method, amount)
payment_event(id, payment_id, from_status, to_status, payload_json, created_at)
refund(id, payment_id, amount, reason, approved_by, created_at)
tip_allocation(id, booking_id, staff_id, amount, tech_share, shop_share, payout_status)
commission_report(id, staff_id, period, revenue_base, rate, amount, status)
invoice(id, legal_entity_id, invoice_no, invoice_year, booking_id, customer_id, issued_at, service_date,
        seller_name, seller_tax_id, customer_name, customer_address,
        net_amount, vat_rate, vat_amount, gross_amount, currency, tax_mode,
        payment_method, status, storno_of, pdf_url)
invoice_sequence(legal_entity_id, year, next_no)
invoice_line(id, invoice_id, description, quantity, unit_price, net, vat_rate, vat, gross)
tax_config(id, legal_entity_id, branch_id?, mode, default_rate, valid_from, valid_to)
cash_drawer_session(id, branch_id, opened_at, closed_at, opening_cash,
                    closing_cash, expected_cash, difference)
datev_export(id, period, file_url, created_at, created_by)
loyalty_account(id, customer_id, points_balance, tier)
loyalty_program_config(id, earn_rate, point_value, max_redeem_percent,
                       tier_thresholds_json, valid_from, valid_to)
loyalty_transaction(id, account_id, booking_id, type, points, balance_after, created_at,
                    UNIQUE(booking_id, account_id, type))
qr_token(id, customer_id, token_hash, expires_at, used_at)
voucher(id, code, type, value, min_spend, max_uses, used_count, per_customer_limit,
        start_at, end_at, stacking_allowed, active)
voucher_redemption(id, voucher_id, customer_id, booking_id, amount, created_at)
review(id, booking_id UNIQUE, customer_id, staff_id, rating, content, images_json,
       status, reply, created_at)
notification_outbox(id, channel, template, recipient, payload_json, status, retry_count,
                    scheduled_at, sent_at)
audit_log(id, actor_id, action, entity_type, entity_id, before_json, after_json, ip, created_at)
idempotency_key(key, request_hash, response_json, status, created_at, expires_at)
```

---

## 5. Thiết kế lõi cần làm đúng

### 5.1. Availability Engine v2

**Input:** `branchId`, `serviceIds[]` (hoặc `comboId`), `date`, `staffId?`, `timezone`.

**Các bước:**
1. Xác định tổng duration = `Σ service.duration + Σ addon.duration + buffer 10' giữa các service` (nếu multi-service).
2. Lấy giờ mở cửa của branch theo thứ trong tuần + ngày lễ.
3. Với mỗi staff có **đủ skill** cho các service được phân công:
   - Lấy `shift` trong ngày.
   - Trừ `time_off`.
   - Trừ appointment không terminal: điều kiện overlap
     `existing.start_at < candidate.end AND existing.buffer_end_at > candidate.start`.
   - Trừ buffer trước/sau theo cấu hình.
4. Sinh slot theo bước `slot_step` (15/30 phút) từ `shift.start` đến `shift.end - duration`.
5. Nếu `staffId == null` ("any available"): trả union các slot, kèm danh sách staff rảnh cho từng slot (hoặc gán tự động khi tạo booking).
6. Ẩn slot quá khứ theo timezone branch + cutoff tối thiểu (ví dụ phải đặt trước 30 phút).

**Multi-service tối ưu (D9):**

Với tối đa **3 dịch vụ**, engine sinh các **phương án xếp lịch** rồi chấm điểm:

| Phương án | Mô tả | Khi nào dùng |
|---|---|---|
| **A. Một thợ làm liên tiếp** (ưu tiên) | Cùng 1 staff làm cả 3 service, nghỉ buffer 10' giữa các service | Mặc định, trải nghiệm tốt nhất |
| **B. Nhiều thợ + handoff** | Thợ 1 làm service 1, thợ 2 làm service 2…; có thể chờ giữa các service | Khi không thợ nào đủ skill hoặc ca không đủ dài |
| **C. Song song có kiểm soát** | Ví dụ manicure + pedicure cùng lúc bởi 2 thợ | Chỉ khi khách chọn rõ và tiệm có đủ 2 thợ; Phase 3 |

**Công thức chấm điểm (tham khảo):**
```
score = 100
      - 30 * (khác thợ giữa các service)
      - 1  * (tổng phút chờ giữa các service)
      - 2  * (số phút lệch tải so với thợ ít việc nhất)
      + 10 * (khớp thợ khách yêu cầu)
      - 5  * (số lần handoff)
```
- Trả về **top 3 phương án** cho frontend nếu khách chọn "any"; nếu khách chọn thợ cụ thể chỉ trả phương án khả thi.
- Mỗi `booking_item` lưu `staff_id`, `start_at`, `end_at` riêng → hỗ trợ handoff và báo cáo commission chính xác.
- Ràng buộc cứng: không vượt giờ đóng cửa, không trùng lịch, đủ skill, tối đa 3 service, buffer 10'.

**Response contract đề xuất:**

```json
{
  "date": "2026-11-20",
  "timezone": "Europe/Berlin",
  "slotStepMinutes": 30,
  "totalDurationMinutes": 105,
  "staff": [
    {
      "staffId": 7,
      "name": "Minh",
      "slots": [
        { "start": "09:00", "end": "10:45" },
        { "start": "10:30", "end": "12:15" }
      ]
    }
  ],
  "anyAvailable": ["09:00", "10:30", "13:00"]
}
```

**Chống double-booking (bắt buộc):**
```sql
BEGIN;
SELECT id FROM staff WHERE id = ? FOR UPDATE;         -- khóa tuần tự theo thợ
-- check overlap của appointment active
-- nếu OK: INSERT booking + booking_item
COMMIT;
```
- Thêm unique index `(staff_id, start_at)` cho trường hợp trùng chính xác.
- Với "any available": khóa lần lượt từng staff candidate theo thứ tự `id ASC` để tránh deadlock.
- Test bắt buộc: 20 thread cùng đặt 1 slot → đúng **1** thành công, còn lại nhận `409 SLOT_TAKEN`.

### 5.2. Pricing & tiền tệ

- Dùng `BigDecimal`, không dùng `double`.
- Mọi bảng tiền có `currency`; MVP chỉ **EUR**.
- Lưu **snapshot** giá tại thời điểm đặt: `booking_item.price`, `booking.subtotal`, `booking.total`.
- Làm tròn: `HALF_UP`, 2 chữ số thập phân với EUR.
- Thứ tự tính: `subtotal → discount (voucher/tier) → loyalty redeem → tip → deposit đã trả → amount_due`.
- Tách `net_amount`, `vat_amount`, `gross_amount`; VAT snapshot theo `tax_config`.
- Điểm tích lũy tính trên **doanh thu dịch vụ sau giảm giá, trước tip và trước VAT** (đề xuất; chốt với Steuerberater).

### 5.3. Payment / POS (theo D6)

**Phạm vi MVP (đã chốt):**
- Thanh toán **tại quầy sau khi hoàn thành dịch vụ**.
- Phương thức: **tiền mặt**, **thẻ (EC/Kreditkarte qua máy POS hiện có)**, **chuyển khoản ngân hàng** (lễ tân xác nhận thủ công).
- **Không tích hợp gateway online trong MVP**; không lưu số thẻ/CVV (giảm phạm vi PCI DSS).
- Stripe để **Phase 3**: Stripe Checkout/Payment Element cho cọc online, Stripe Terminal cho thẻ nếu muốn hợp nhất.

**Thiết kế:**
- **Checkout idempotent:** `POST /pos/checkout` yêu cầu header `Idempotency-Key`; unique constraint trên key.
- **Split payment:** 1 bill nhiều `payment_split` (ví dụ 30 EUR tiền mặt + 50 EUR thẻ); tổng phải bằng `amount_due` (sai → 400).
- **Tip 80/20 (D8, D15):** lưu `tip_amount`, `tip_tech_share = 80%`, `tip_shop_share = 20%`; tip **không chịu VAT**, **không** cộng vào doanh thu dịch vụ và **không** tính điểm loyalty.
- **Deposit:** feature flag, mặc định tắt; payment type `DEPOSIT`, khi checkout trừ vào `amount_due`.
- **Refund/void:** chỉ admin/manager; ghi `refund` + `audit_log`; nếu đã cộng điểm → tạo `loyalty_transaction` âm.
- **Receipt/invoice:** sinh PDF/HTML, lưu `invoice_no` tuần tự; gửi email nếu khách có email.
- **Provider adapter:** interface `PaymentGateway` với implementation `CashGateway`, `CardTerminalGateway`, `BankTransferGateway`; Phase 3 thêm `StripeGateway` (webhook verify signature + idempotent).
- **Đối soát quỹ:** mỗi ngày có Z-report: tổng theo phương thức, tip, refund, chênh lệch tiền mặt; lưu `cash_drawer_session`.

**State machine Payment:**
```
PENDING → AUTHORIZED → PAID → PARTIALLY_REFUNDED → REFUNDED
                    ↘ VOID / FAILED
```
- Mọi thay đổi trạng thái ghi `payment_event` (append-only).
- Số tiền luôn `BigDecimal`, lưu `currency=EUR`.

### 5.4. Loyalty & hạng khách hàng — **đã chốt Phương án 3**

> **Quyết định D7/D12:** triển khai **Phương án 3 ngay trong MVP**: điểm cố định 0,05 EUR + hạng Silver/Gold/Platinum + hệ số tích điểm + quyền lợi hạng. Toàn bộ rule lưu DB, có hiệu lực theo ngày để chủ tiệm đổi mà không cần deploy.

#### 5.4.1. Phân tích quy tắc ban đầu của chủ tiệm

Chủ tiệm đưa ra: **10 EUR = 1 điểm; 1 điểm = 0,5% hoá đơn; tối đa 10%; chỉ áp dụng hoá đơn < 100 EUR**.

Có 2 cách hiểu, dẫn tới chi phí rất khác nhau:

| Cách hiểu | Công thức | Ví dụ hoá đơn 80 EUR | Vấn đề |
|---|---|---|---|
| **A. 1 điểm = 0,5% của hoá đơn hiện tại** | Giảm giá = điểm × 0,5% × bill | 12 điểm → 6% → 4,80 EUR | Giá trị 1 điểm **tăng theo bill**; khách có thể gom điểm rồi tiêu vào bill lớn để tối ưu; chi phí khó dự báo |
| **B. 1 điểm = 0,05 EUR cố định** (khuyến nghị) | 10 EUR chi = 1 điểm = 0,05 EUR = 0,5% của 10 EUR | 8 điểm → 0,40 EUR | Đơn giản, công bằng, chi phí cố định 0,5% doanh thu; dễ giải thích |

> **Khuyến nghị:** chọn **cách hiểu B** — “10 EUR = 1 điểm, 1 điểm = 0,05 EUR”. Đây đúng là **0,5% cashback**, không phụ thuộc bill, không bị lạm dụng. Bỏ điều kiện “chỉ bill < 100 EUR” vì tạo hiệu ứng bậc thang bất lợi cho khách chi tiêu cao.

#### 5.4.2. Ba phương án và phương án đã chọn

**Phương án 1 — Giữ nguyên quy tắc ban đầu (❌ không chọn)**
- 10 EUR = 1 điểm.
- 1 điểm = 0,5% hoá đơn, tối đa 10%.
- Chỉ bill < 100 EUR.
- Ưu: dễ nói với khách. Nhược: giá trị điểm thay đổi, bill ≥ 100 EUR không được dùng điểm, dễ mất khách chi tiêu cao.

**Phương án 2 — Điểm có giá trị cố định (nền tảng của Phương án 3)**
- 10 EUR chi tiêu (sau giảm giá, trước tip) = 1 điểm.
- 1 điểm = **0,05 EUR**.
- Đổi tối thiểu 10 điểm = 0,50 EUR; tối đa **10% hoá đơn**.
- **Bỏ giới hạn bill < 100 EUR**.
- Không tính điểm cho tip, voucher, gift card, thuế.
- Ưu: rõ ràng, chi phí 0,5% doanh thu, dễ đối soát. Nhược: ít hấp dẫn về mặt marketing.

**Phương án 3 — Loyalty + hạng thành viên (✅ phương án CHỐT FINAL)**
- Nền tảng Phương án 2 + hệ thống hạng 12 tháng:

| Hạng | Chi tiêu 12 tháng | Hệ số tích điểm | Giảm tối đa khi đổi điểm | Quyền lợi thêm |
|---|---|---|---|---|
| **Silver** | 0–299 EUR | 1,0x | 10% bill | Sinh nhật +20 điểm |
| **Gold** | 300–799 EUR | 1,25x | 15% bill | Sinh nhật +50 điểm; ưu tiên đặt lịch; 1 lần đổi lịch muộn/tháng |
| **Platinum** | ≥ 800 EUR | 1,5x | 20% bill | Sinh nhật +100 điểm; ưu tiên + giữ slot; 1 free add-on/quý; 2 lần đổi muộn/tháng |

> **D18 — ngưỡng đã chốt final:** 300 EUR (Gold) và 800 EUR (Platinum) tính trên doanh thu dịch vụ 12 tháng gần nhất. Chỉ đổi khi chủ tiệm yêu cầu; thay đổi thực hiện bằng bản ghi `loyalty_program_config` mới, không sửa lịch sử.

- **Cách tính hạng:** rolling 12 tháng trên doanh thu dịch vụ đã hoàn thành (không gồm tip, voucher, refund); xét lại mỗi tháng; **soft-landing 1 tháng** khi tụt hạng.
- **Welcome bonus:** 20 điểm (1 EUR) cho khách đăng ký mới.
- **Referral:** người giới thiệu + người được giới thiệu mỗi người 50 điểm (2,50 EUR) sau khi người mới hoàn thành dịch vụ đầu tiên.
- **Happy hour:** x2 điểm vào Thứ 3/Thứ 4 (ngày ít khách).
- **Hết hạn:** điểm hết hạn sau 12 tháng kể từ ngày phát sinh, trừ FIFO; nhắc trước 30 ngày.
- **Đổi điểm:** theo thứ tự voucher → điểm → cọc; không cho đổi quá số dư; hoàn điểm khi refund.
- **Không tích điểm:** tip, thuế VAT, voucher/gift card, phí huỷ, dịch vụ đã hoàn tiền.
- **Ví dụ:**
  - Bill 80 EUR → +8 điểm (0,40 EUR).
  - Bill 120 EUR → +12 điểm (0,60 EUR) — **không bị chặn** vì bỏ điều kiện <100 EUR.
  - Gold, bill 200 EUR → +25 điểm (1,25 EUR), đổi tối đa 15% bill.
  - Khách chi 300 EUR trong 12 tháng → lên Gold.

> **Đã chốt FINAL (D7, D12, D18):** triển khai **đầy đủ Phương án 3 trong S4** (không chờ Phase 3). Toàn bộ tỷ lệ/hạng lưu trong bảng cấu hình, không hardcode — chủ tiệm đổi rule không cần deploy.

#### 5.4.3. Chi phí chương trình (số liệu để chủ tiệm duyệt ngân sách)

| Doanh thu dịch vụ/tháng | Điểm phát sinh (10 EUR = 1) | Chi phí tối đa nếu đổi hết (0,05 EUR/điểm) | % doanh thu |
|---|---|---|---|
| 5.000 EUR | 500 | 25 EUR | 0,5% |
| 10.000 EUR | 1.000 | 50 EUR | 0,5% |
| 20.000 EUR | 2.000 | 100 EUR | 0,5% |

- Hạng Gold/Platinum tăng hệ số tích điểm (1,25x/1,5x) → chi phí thực tế ~0,6–0,75% doanh thu.
- Nên đặt **ngân sách loyalty tối đa 1% doanh thu/tháng** và báo cáo `point_liability` để kiểm soát.
- Điểm chưa dùng là **nợ phải trả** (liability), cần theo dõi cùng kế toán.

#### 5.4.4. Kỹ thuật

- Mỗi customer có `loyalty_account` + `qr_token` (UUID ngẫu nhiên, lưu **hash**, hết hạn 5 phút, dùng 1 lần).
- Flow: khách mở QR → lễ tân scan → `POST /loyalty/scan {qrToken, bookingId}` → backend verify → cộng điểm → trả `pointsEarned`, `balance`, `tier`.
- **Idempotent:** unique `(booking_id, account_id, type)`; retry trả kết quả cũ, không cộng 2 lần.
- Refund → trừ điểm tương ứng, không cho balance âm (hoặc ghi nợ tùy policy).
- Bảng `loyalty_program_config` (earn rate, point value, caps, tier thresholds) có hiệu lực theo ngày; booking lưu snapshot rule đã áp dụng.
- **Tier engine:** job rolling 12 tháng, xét lại hạng mỗi tháng, soft-landing 1 tháng; lưu `tier_history`.
- **Referral:** bảng `referral` unique theo cặp người giới thiệu/người được giới thiệu; thưởng sau khi booking đầu tiên completed; chống self-referral.
- **Birthday/happy hour:** job sinh nhật + cấu hình multiplier theo weekday; idempotent theo `(customer, event_date, type)`.
- **Point expiry:** job FIFO trừ điểm hết hạn, ghi `loyalty_transaction` type `EXPIRE`.
- Báo cáo nợ điểm (point liability) để kế toán theo dõi.

### 5.5. Notification

- Dùng **Outbox pattern**: business transaction ghi `notification_outbox`, worker gửi async; retry exponential backoff.
- Template versioned, hỗ trợ đa ngôn ngữ.
- Sự kiện: booking created, confirmed, reminder 24h, reminder 2h, cancelled, rescheduled, points earned, review request.
- Kênh MVP: email (SMTP/SES) + SMS (Twilio/Zalo SMS); Phase 3: Zalo OA.

### 5.6. Tax & Accounting — “dễ khai thuế” (D11, D14, D15, D16)

> Mục tiêu: **mọi giao dịch tiền phát sinh dữ liệu đủ để Steuerberater khai thuế, không phải nhập tay lại**. Các nội dung dưới đây cần Steuerberater xác nhận trước khi go-live.

#### 5.6.1. Cấu hình thuế

- `tax.mode` **theo branch + legal entity**, có hiệu lực theo ngày:
  - `SMALL_BUSINESS` (§19 UStG — Kleinunternehmer): **trạng thái hiện tại của tiệm**; không thu VAT, hoá đơn ghi chú theo luật; không khấu trừ VAT đầu vào.
  - `STANDARD_19`: áp dụng VAT 19% cho dịch vụ nail (mức phổ thông; xác nhận nếu có dịch vụ đặc biệt).
- **Thiết kế switchable:** tương lai mở thêm tiệm/vượt ngưỡng §19 → chỉ thêm bản ghi `tax_config` mới với `valid_from`, không sửa code, không sửa hoá đơn cũ.
- Giá niêm yết **đã gồm VAT** (`price_includes_vat=true`); hệ thống tách `net_amount`, `vat_amount`, `gross_amount` (khi `SMALL_BUSINESS`: VAT = 0).
- Mỗi `service` có `tax_rate`, `tax_code`, `revenue_account` (DATEV SKR03/04), `cost_center`.
- Booking/invoice **snapshot** tax rate + `tax_mode` tại thời điểm phát hành — đổi luật sau này không làm sai hoá đơn cũ.
- Nếu §19: hoá đơn ghi **“Keine Umsatzsteuer gemäß §19 UStG”**.
- **Tip không bao giờ nằm trong VAT base** (D15), kể cả khi chuyển sang `STANDARD_19`.

#### 5.6.2. Hoá đơn theo §14 UStG

Bắt buộc có:
- Tên/địa chỉ người bán + **Steuernummer/USt-IdNr**.
- Tên/địa chỉ người mua (B2C: simplified invoice nếu gross < 250 EUR; trên ngưỡng cần đầy đủ).
- Ngày phát hành + **số hoá đơn tuần tự, không trống**.
- Mô tả dịch vụ, số lượng, **ngày thực hiện dịch vụ**.
- Net, VAT rate, VAT amount, gross; chiết khấu/voucher thể hiện trước VAT.
- Phương thức thanh toán.
- Ghi chú §19 nếu áp dụng.
- **Storno invoice** tham chiếu hoá đơn gốc; **không sửa/xoá** hoá đơn đã phát hành.
- Lưu **10 năm**, immutable theo GoBD.

**Ví dụ tính (VAT 19%, giá đã gồm thuế):**
```
Giá dịch vụ niêm yết:        80,00 EUR (gross)
Net:                          80,00 / 1,19 = 67,23 EUR
VAT 19%:                      12,77 EUR
Giảm giá voucher 5,00 EUR:   gross còn 75,00 → net 63,03, VAT 11,97
Tip 10,00 EUR:               tách riêng, không tính VAT/điểm
Khách trả:                    75,00 + 10,00 = 85,00 EUR
```
- Điểm loyalty quy đổi được coi là **giảm giá**, làm giảm VAT base — cần Steuerberater xác nhận cách hạch toán.

#### 5.6.3. KassenSichV / TSE — **đã chốt dùng fiskaly (D17)**

- POS ghi nhận tiền mặt là **elektronisches Aufzeichnungssystem** → cần **TSE** theo KassenSichV. Mỗi giao dịch có transaction number, serial, signature, QR.
- **Phương án đã chốt: fiskaly SIGN DE (cloud TSE)** — tích hợp REST API, sandbox miễn phí, không cần phần cứng. Chi tiết tích hợp ở **mục 5.8.3**.
- Dự phòng: hardware TSE (USB) nếu tiệm thường xuyên mất mạng; code giữ interface `TseClient` để đổi provider.
- **Nghĩa vụ kèm theo** (Steuerberater xác nhận trước go-live):
  - **Mitteilungspflicht §146a Abs. 4 AO:** thông báo cho Finanzamt loại hệ thống ghi nhận điện tử trong vòng **1 tháng** sau khi đưa vào sử dụng.
  - **Belegausgabepflicht §146a Abs. 2 AO:** phải xuất hoá đơn/receipt cho khách; nếu bất khả thi có thể xin **Belegausgabeverzicht**.
  - §19 chỉ miễn một số nghĩa vụ kế toán — **không tự động miễn TSE**; phải xác nhận bằng văn bản.
- Cuối ngày: **Z-report**, kiểm quỹ tiền mặt, ghi chênh lệch.
- Export **DSFinV-K** (định dạng chuẩn của Finanzamt) theo tháng/quý cho Steuerberater.
- Nếu Steuerberater kết luận không cần TSE: đặt `tse.enabled=false`, vẫn giữ invoice + dữ liệu sẵn sàng DSFinV-K.

#### 5.6.4. Tip & commission (D8, D15)

- **Theo D15: tip không chịu VAT.** Hệ thống **không** đưa tip vào VAT base, không tính điểm loyalty, không cộng vào doanh thu dịch vụ.
- Tip **tiền mặt** đưa trực tiếp cho thợ: ghi nhận để báo cáo, không phải doanh thu tiệm.
- Tip **qua thẻ/chuyển khoản**: tiền vào tài khoản tiệm → phải chuyển lại thợ; lưu `tip_payment`, `tip_payout`, `tip_tech_share=80%`, `tip_shop_share=20%`.
- Phần **20% tiệm giữ lại**: hạch toán nội bộ theo quy chế tiệm; vẫn tách khỏi doanh thu dịch vụ và VAT base. Nên ghi rõ trong quy chế nội bộ + Steuerberater để tránh bị xem là doanh thu ẩn.
- Commission thợ: bảng `commission_report` theo tháng; thợ là nhân viên → payroll; freelancer → invoice.
- **Không tính tip vào điểm loyalty**; không tính tip vào doanh thu dịch vụ.

#### 5.6.5. Báo cáo & export

- **Z-report hằng ngày:** doanh thu theo phương thức (cash/card/bank), tip, refund, discount, VAT.
- **Báo cáo VAT tháng:** net/VAT/gross theo rate và theo service.
- **DATEV export (CSV)** theo SKR03/04: ngày, số hoá đơn, tài khoản, cost center, tax key, net, VAT, gross.
- **Kassenbuch** + đối soát ngân hàng.
- Doanh thu theo thợ/dịch vụ/phương thức; báo cáo commission.
- **Phí thanh toán** (Stripe/ngân hàng) lưu riêng để kê khai VAT đầu vào và đối soát.
- Export cho Steuerberater: CSV/Excel + PDF hoá đơn.

#### 5.6.6. Data model bổ sung

```
invoice(id, legal_entity_id, invoice_no, invoice_year, booking_id, customer_id, issued_at,
        service_date, seller_name, seller_tax_id, customer_name, customer_address,
        net_amount, vat_rate, vat_amount, gross_amount, currency, tax_mode,
        payment_method, status, storno_of, pdf_url)
invoice_sequence(legal_entity_id, year, next_no)          -- khóa row khi cấp số, chống trùng
invoice_line(id, invoice_id, description, quantity, unit_price, net, vat_rate, vat, gross)
tax_config(id, legal_entity_id, branch_id?, mode, default_rate, valid_from, valid_to)
tip_allocation(id, booking_id, staff_id, amount, tech_share, shop_share, payout_status)
commission_report(id, staff_id, period, revenue_base, rate, amount, status)
cash_drawer_session(id, branch_id, opened_at, closed_at, opening_cash,
                    closing_cash, expected_cash, difference)
datev_export(id, period, file_url, created_at, created_by)
```

#### 5.6.7. Acceptance criteria “dễ khai thuế”

- [ ] 100% payment tạo invoice; không có payment mồ côi.
- [ ] Số hoá đơn tuần tự, không trống/trùng; có test tạo đồng thời.
- [ ] Tổng invoice = tổng payment theo ngày/tháng.
- [ ] Tách đúng net/VAT/gross; test cả `SMALL_BUSINESS` và `STANDARD_19`.
- [ ] **Tip không xuất hiện trong VAT base / invoice service lines**; báo cáo tip riêng.
- [ ] Chuyển `SMALL_BUSINESS → STANDARD_19` chỉ bằng cấu hình, hoá đơn cũ không đổi.
- [ ] Cảnh báo khi doanh thu tiến gần ngưỡng §19.
- [ ] Tip/commission tách khỏi doanh thu dịch vụ.
- [ ] Discount/voucher làm giảm đúng VAT base.
- [ ] Z-report khớp tiền mặt thực tế.
- [ ] DATEV export mở được bằng Excel/DATEV, đối chiếu mẫu Steuerberater.
- [ ] PDF hoá đơn đủ trường §14 UStG.
- [ ] Lưu trữ 10 năm, không sửa/xoá; audit log đầy đủ.
- [ ] TSE (fiskaly) có transaction signature + QR trên receipt; DSFinV-K export đúng chuẩn.
- [ ] Giao dịch `TSE_PENDING`/`FAILED` được xử lý trước Z-report cuối ngày.
- [ ] Đã gửi thông báo Finanzamt theo §146a Abs. 4 AO (nếu áp dụng).
- [ ] Steuerberater review + sign-off.

#### 5.6.8. Lưu ý pháp lý

- Đây không phải tư vấn thuế; mức thuế/ngưỡng §19 và cách xử lý tip cần **Steuerberater** xác nhận.
- Nếu vượt ngưỡng Kleinunternehmer, phải chuyển sang standard VAT và cập nhật hoá đơn.
- Hoá đơn B2C simplified chỉ áp dụng dưới 250 EUR gross.
- Tip/commission có thể ảnh hưởng payroll/income tax của thợ.

#### 5.6.9. Lộ trình chuyển §19 → VAT 19% (khi mở thêm tiệm/vượt ngưỡng)

Vì D16 nói **hiện tại §19 nhưng tương lai có thể mở rộng**, hệ thống phải sẵn sàng chuyển đổi:

1. **Xác định phạm vi pháp lý:** nếu tiệm mới **cùng pháp nhân** → cùng `tax.mode`; nếu **pháp nhân mới** → tách `legal_entity_id`/tenant và cấu hình riêng. Không để mỗi branch tự chọn mode trái luật.
2. **Steuerberater chốt ngày hiệu lực** (thường đầu năm hoặc theo quy định §19) và nghĩa vụ hoá đơn.
3. **Thêm `tax_config` mới** với `valid_from`; không sửa config cũ; booking/invoice snapshot mode.
4. **Cập nhật mẫu hoá đơn:** bỏ ghi chú §19, thêm VAT 19%, USt-IdNr/Steuernummer, tax key DATEV.
5. **Chính sách giá:** quyết định giữ nguyên giá gross (net giảm) hay tăng giá; truyền thông cho khách trước.
6. **Cập nhật DATEV account/tax key**, báo cáo VAT, Kassenbuch.
7. **Test:** tạo hoá đơn trước/sau ngày hiệu lực; đảm bảo hoá đơn cũ bất biến, báo cáo tách đúng.
8. **Theo dõi ngưỡng §19:** job báo cáo doanh thu lũy kế, alert ở 80%/90%/100% ngưỡng hiện hành.
9. **Đào tạo lễ tân:** hoá đơn mới, cách xử lý câu hỏi VAT, Storno.

> Khi mở tiệm mới: nếu cùng pháp nhân, **không tạo mode thuế riêng cho từng branch**; nếu muốn tách, phải tách pháp nhân/tenant ngay từ đầu.

### 5.7. Chính sách cọc & hủy — config-driven (D4)

**Nguyên tắc:** hiện tiệm chưa có chính sách, nhưng **không hardcode**. Mọi rule lưu DB, có hiệu lực theo ngày và **snapshot vào booking** để đổi policy sau không ảnh hưởng lịch cũ.

```
cancellation_policy(id, branch_id, service_id?, free_cancel_hours,
                    late_cancel_action, late_cancel_fee_type, late_cancel_fee_value,
                    no_show_fee_type, no_show_fee_value, valid_from, valid_to)
deposit_policy(id, branch_id, service_id?, required, deposit_type, deposit_value,
               min_service_duration, valid_from, valid_to)
booking.policy_snapshot_json
cancellation_record(id, booking_id, requested_by, reason, policy_snapshot,
                    fee_amount, refund_amount, created_at)
```

**Mặc định MVP (phương án tối ưu đã chọn):**
- `deposit_required = false` (không cọc).
- `free_cancel_hours = 24`.
- `late_cancel_action = WARN` — khách vẫn hủy được, hệ thống cảnh báo và ghi nhận late cancel; **không chặn** (khác code hiện tại đang block).
- `late_cancel_fee = 0`, `no_show_fee = 0`; admin có thể thu phí thủ công.
- Chủ tiệm có thể đổi sang `FEE` hoặc `BLOCK` bất cứ lúc nào, không cần deploy.

> **Vì sao chọn phương án này:** tiệm chưa có dữ liệu no-show/late-cancel, nên chưa nên áp phí ngay (dễ mất khách). `WARN + tracking` vừa giữ trải nghiệm tốt, vừa tạo dữ liệu để sau 2–3 tháng quyết định:
> - late-cancel rate > 10% → bật `FEE` (ví dụ 20% giá dịch vụ).
> - no-show rate > 5% → yêu cầu cọc 20% cho dịch vụ > 60 phút.
> - Khách quen/no-show nhiều → flag nội bộ, yêu cầu cọc trước.

**Luồng hủy:**
1. Load policy snapshot của booking.
2. Tính fee/refund theo policy.
3. Tạo `cancellation_record` + audit log.
4. Giải phóng slot → notify waitlist.
5. Nếu có cọc → tạo refund (Stripe/manual) và trừ điểm loyalty nếu đã cộng.

**Luồng đổi lịch:**
- Revalidate availability (không double-book).
- Áp dụng cutoff policy; nếu quá muộn → yêu cầu gọi tiệm hoặc admin override.
- Giữ nguyên `booking_id`, tạo `booking_version` + status history; không sửa đè dữ liệu cũ.
- Admin override bắt buộc nhập lý do + audit log.

### 5.8. Hướng dẫn thực hiện thuế & TSE — “chọn dễ nhất, làm từng bước”

> **Nguyên tắc chọn:** làm **đúng luật tối thiểu** nhưng **không tự khoá mình**. Hiện tại chạy `SMALL_BUSINESS` (§19) vì đúng thực tế tiệm; TSE chọn **cloud** để không phải mua/quản lý phần cứng. Mọi thứ còn lại (invoice, DATEV, DSFinV-K, tax mode) thiết kế sẵn để bật VAT 19% sau này.

#### 5.8.1. So sánh phương án TSE và lý do chọn fiskaly

| Phương án | Cách làm | Ưu điểm | Nhược điểm | Kết luận |
|---|---|---|---|---|
| **fiskaly SIGN DE (cloud TSE)** | REST API, không phần cứng | Sandbox miễn phí; BSI-certified; hợp Spring Boot; mở nhiều quầy dễ; không quản lý thiết bị | Phụ thuộc internet; phí thuê bao + theo giao dịch | ✅ **CHỌN** |
| Hardware TSE (USB Swissbit/Epson) | Cắm vào máy POS | Không phụ thuộc mạng; phí theo giao dịch thấp | Mỗi quầy 1 thiết bị; driver/USB; khó scale | Dự phòng |
| POS bundle certified (SumUp, orderbird…) | Dùng phần mềm POS có TSE sẵn | Nhanh nhất, không code | Không gắn booking/CRM; nhập tay 2 nơi; mất mục tiêu “dễ khai thuế” | ❌ Loại |
| Không TSE | Chỉ xuất hoá đơn | Đơn giản | Không đáp ứng KassenSichV nếu POS ghi tiền mặt | ❌ Loại |

- Chi phí: lấy báo giá trực tiếp (thuê bao TSE + phí giao dịch); sandbox dùng để phát triển không mất phí.
- Trước khi ký hợp đồng: gửi Steuerberater xác nhận **có bắt buộc TSE với §19 + mô hình tiền mặt của tiệm hay không**.

#### 5.8.2. Thứ tự thực hiện (0 → 10)

| # | Việc | Kết quả bàn giao | Ai làm | Khi nào |
|---|---|---|---|---|
| 0 | Chốt pháp lý: §19, nghĩa vụ TSE, mẫu DATEV, Belegausgabe | Email/biên bản xác nhận của Steuerberater | PO + Steuerberater | Trước S3 |
| 1 | Seed `tax_config` = `SMALL_BUSINESS`, `price_includes_vat=true`, `tax_code`/`revenue_account` cho từng service | Migration + seed | Backend | S3 đầu |
| 2 | Mẫu hoá đơn §14 UStG + ghi chú §19 | Template PDF/HTML duyệt bởi Steuerberater | Backend + PO | S3 |
| 3 | Tax engine: net/VAT/gross, VAT = 0 khi §19, snapshot | Unit test 100% case tiền tệ | Backend | S3 |
| 4 | Cấp số hoá đơn tuần tự (row lock theo legal entity + năm) | Test 20 thread không trùng/không trống | Backend | S3 |
| 5 | Tip tách riêng, không VAT, không điểm | Bảng `tip_allocation` + báo cáo | Backend | S3 |
| 6 | Z-report + `cash_drawer_session` + đối soát quỹ | Báo cáo ngày khớp tiền mặt | Backend | S3 |
| 7 | DATEV CSV (SKR03/04) + báo cáo doanh thu tháng | File mẫu Steuerberater chấp nhận | Backend | S3 |
| 8 | Tích hợp fiskaly + DSFinV-K export | Giao dịch có signature + QR | Backend | S3 cuối |
| 9 | Test toàn bộ + UAT tại quầy | Checklist 5.6.7 pass | QA + chủ tiệm | S3 cuối |
| 10 | Go-live: gửi Mitteilung §146a Abs. 4 AO, đào tạo lễ tân | Xác nhận đã thông báo Finanzamt | PO | Trước go-live |

#### 5.8.3. Tích hợp fiskaly — các bước kỹ thuật

> **Trạng thái 2026-11-20:** đã có khung `TseClient` + `NoopTseClient` +
> `GET /tse/status` + tài liệu thiết kế; **chưa đăng ký key fiskaly** nên chưa gọi
> API thật, chưa tạo bảng `tse_transaction`. Xem `docs/tse/tse-design.md`.

**Chuẩn bị (S0/S3 đầu):**
1. Tạo tài khoản fiskaly, organisation; lấy `api-key`/`api-secret`; dùng **sandbox** trước.
2. Tạo TSE (`POST /tse`) → lưu `tse_id`.
3. Tạo client (`POST /tse/{tse_id}/client`) → lưu `client_id`, `serial_number`.
4. Lưu tất cả vào secret manager, không commit.

**Cấu hình đề xuất:**
```properties
tse.enabled=true
tse.provider=fiskaly
tse.base-url=${FISKALY_BASE_URL}        # sandbox trước, production sau
tse.api-key=${FISKALY_API_KEY}
tse.api-secret=${FISKALY_API_SECRET}
tse.tse-id=${FISKALY_TSE_ID}
tse.client-id=${FISKALY_CLIENT_ID}
tse.timeout-ms=3000
```

**Luồng một giao dịch:**
```
POS checkout
  → tạo payment + invoice (transaction DB)
  → TseClient.start(txId)          // txId = UUID, idempotent
  → TseClient.finish(txId, receiptJson)
  → nhận signature + QR payload
  → lưu tse_transaction (tx_id, tx_number, signature, qr, receipt_json)
  → in QR lên receipt
  → nếu timeout/mất mạng: retry CÙNG txId; đánh dấu TSE_PENDING, không chặn thu tiền
  → job đối soát: mọi giao dịch phải có signature cuối ngày
```

**Bảng dữ liệu:**
```
tse_transaction(id, legal_entity_id, invoice_id, tx_id, tx_number, tse_id, client_id,
                serial_number, signature, qr_payload, receipt_json,
                started_at, finished_at, status, error_message)
```

**Quy tắc:**
- `tx_id` sinh 1 lần và **không đổi khi retry** (idempotency của fiskaly).
- Không xoá `tse_transaction`; là bằng chứng pháp lý.
- Nếu `tse.enabled=false` → toàn bộ checkout vẫn chạy, chỉ bỏ bước ký.
- Job cuối ngày: liệt kê giao dịch `TSE_PENDING`/`FAILED` để xử lý trước Z-report.
- DSFinV-K export đọc từ `invoice` + `payment` + `tse_transaction`, không tính lại từ dữ liệu hiện tại.

#### 5.8.4. Checklist S3 theo thứ tự (dùng làm task board)

- [ ] `S3-T01` Seed `tax_config` §19 + `tax_code`/`revenue_account` cho mọi service.
- [ ] `S3-T02` Entity `invoice` + `invoice_sequence` + cấp số có row lock.
- [ ] `S3-T03` Mẫu PDF §14 UStG + ghi chú “Keine Umsatzsteuer gemäß §19 UStG”.
- [ ] `S3-T04` Tax engine net/VAT/gross + snapshot + test 0% và 19%.
- [ ] `S3-T05` Storno (không sửa/xoá) + audit log.
- [ ] `S3-T06` Payment/split/refund + `Idempotency-Key`.
- [ ] `S3-T07` Tip 80/20 tách khỏi hoá đơn dịch vụ + payout status.
- [ ] `S3-T08` Z-report + `cash_drawer_session`.
- [ ] `S3-T09` DATEV CSV + báo cáo doanh thu tháng.
- [ ] `S3-T10` fiskaly sandbox + `tse_transaction` + QR trên receipt.
- [ ] `S3-T11` DSFinV-K export.
- [ ] `S3-T12` Test concurrency cấp số + retry TSE + mất mạng.
- [ ] `S3-T13` Steuerberater review hoá đơn mẫu + file DATEV.
- [ ] `S3-T14` Gửi Mitteilung §146a Abs. 4 AO trước go-live.

#### 5.8.5. Ba việc chỉ Steuerberater quyết được (không phải việc code)

1. **§19 & ngưỡng:** tiệm còn trong ngưỡng Kleinunternehmer không, ngày nào phải chuyển, có được giữ §19 khi mở tiệm thứ 2 cùng pháp nhân không.
2. **Nghĩa vụ TSE & Belegausgabe:** có bắt buộc TSE với mô hình hiện tại không; có xin Belegausgabeverzicht được không; mẫu thông báo Finanzamt.
3. **Hạch toán tip/commission:** phần 20% tiệm giữ lại ghi vào tài khoản nào; tip qua thẻ hạch toán thế nào; tax key/revenue account DATEV cho từng loại dịch vụ.

> Khi có xác nhận, chỉ cần cập nhật `tax_config`, mẫu hoá đơn và mapping DATEV — **không sửa logic lõi**.

---

## 6. Lộ trình triển khai (Roadmap)

> Ước lượng cho **1 backend + 1 frontend** (hoặc 1 fullstack + 1 part-time). Nếu chỉ 1 người, nhân đôi thời gian backend; frontend có thể chạy song song nhờ OpenAPI.

### Sprint 0 — Hardening & Contract Freeze (1 tuần)

**Mục tiêu:** chốt hợp đồng API, dọn nợ kỹ thuật P0/P1 rẻ, dựng CI/CD và môi trường staging.

| Task | Nội dung | Owner | Acceptance Criteria |
|---|---|---|---|
| S0-01 | Tạo `openapi.yaml` (OpenAPI 3.1) cho toàn bộ endpoint hiện có + endpoint dự kiến; bật springdoc | Backend | `/v3/api-docs` trả JSON; Swagger UI chạy; frontend generate TS client được |
| S0-02 | Chuẩn hóa error response: `{code, errorCode, message, details, traceId, timestamp}`; đổi `SLOT_FULL` → 409 | Backend | Test contract pass; frontend map được lỗi |
| S0-03 | Bỏ legacy `/users` trả entity → `UserDTO` + phân trang; đánh dấu deprecated `/booking/**` | Backend | Không còn vòng lặp JSON; test 403/401 |
| S0-04 | Flyway baseline: viết migration cho schema hiện tại; tắt `ddl-auto=update` ở staging/prod | Backend | `flyway:migrate` chạy sạch trên DB trống; rollback document |
| S0-05 | Docker multi-stage + non-root + healthcheck; docker-compose có healthcheck MySQL | DevOps | `docker compose up --build` healthy; image < 300MB |
| S0-06 | GitHub Actions: build → test → coverage → dependency scan → build/push image | DevOps | PR đỏ khi test fail/coverage < ngưỡng |
| S0-07 | Testcontainers MySQL + test concurrency cơ bản cho booking hiện tại | QA/Backend | Test 20 thread chứng minh lỗi B-03 và sau đó pass khi sửa |
| S0-08 | Sửa `application.properties.example` khớp biến thực tế; thêm `.env.example` đầy đủ | Backend | Deploy theo example không lỗi |
| S0-09 | Ghi biên bản **D1–D18** đã chốt (mục 15.1); gửi Steuerberater bộ câu hỏi 5.8.5 | PO | Biên bản + trả lời bằng văn bản trước S3 |
| S0-10 | **Đăng ký fiskaly sandbox (D17)**: tạo TSE/client test, lưu `FISKALY_*` vào secret manager; lấy mẫu DATEV + mẫu hoá đơn Steuerberater đang dùng | DevOps/PO | Sandbox ký được 1 giao dịch thử; có file DATEV mẫu |

**Deliverable:** OpenAPI v1, CI xanh, staging deploy được, backlog sprint 1–5 đã estimate.

---

### Sprint 1 — Staff & Availability Engine (2 tuần) — **ưu tiên số 1**

| Task | Nội dung | Acceptance Criteria |
|---|---|---|
| S1-01 | Entity `staff`, `staff_skill`, `shift`, `time_off`; migration + seed 3 thợ | CRUD admin; thợ có skill mapping |
| S1-02 | API `GET/POST/PUT /staff`, `/staff/{id}/shifts`, `/staff/{id}/time-off` | Admin tạo ca/nghỉ; thợ xem lịch mình |
| S1-03 | `AvailabilityEngine` theo duration + skill + shift + time-off + buffer | Unit test 30+ case; không trả slot ngoài ca/nghỉ |
| S1-04 | API `GET /availability?date=&serviceIds=&staffId=` mới (giữ endpoint cũ deprecated) | Response đúng contract 5.1; p95 < 300ms với 10k booking |
| S1-05 | Khóa staff row + overlap check + unique index; sửa `createBooking` dùng engine | Test 20 thread: 1 thành công; test multi-service liền nhau |
| S1-06 | Booking lưu `staff_id`, `start_at`, `end_at`, `buffer_end_at`, snapshot giá | Không còn phụ thuộc `employee` string |
| S1-07 | RBAC mới: `NAIL_TECH`, `RECEPTIONIST`, `MANAGER`, `ADMIN`; thợ chỉ thấy lịch mình | Ma trận quyền test đầy đủ |
| S1-08 | Frontend: trang chọn thợ + slot theo thợ; mock bằng OpenAPI | Ghép API thật trên staging |

**Demo:** đặt 2 dịch vụ liên tiếp (60' + 45') với thợ cụ thể; hệ thống chỉ trả slot đủ chỗ; 2 người đặt đồng thời chỉ 1 thành công.

---

### Sprint 2 — Booking v2: policy, waitlist, no-show, audit (2 tuần)

| Task | Nội dung | Acceptance Criteria |
|---|---|---|
| S2-01 | State machine mới + `booking_status_history` | Mọi transition ghi history; terminal bất biến |
| S2-02 | Reschedule API có policy cutoff; giữ history | Khách đổi trước 24h OK; sát giờ → 400/409; admin override có audit |
| S2-03 | Cancel policy theo dịch vụ/cọc; tính phí; hoàn cọc | Test ma trận: trước 24h, trong 24h, đã cọc, đã completed |
| S2-04 | Waitlist: đăng ký khi hết slot; giải phóng slot → notify | Worker gửi notification; unique chống spam |
| S2-05 | No-show: đánh dấu, đếm, chặn đặt lịch nếu vượt ngưỡng | Rule cấu hình; admin override |
| S2-06 | Audit log AOP cho admin actions | Truy vấn được ai làm gì, trước/sau |
| S2-07 | Idempotency-Key cho POST booking | Retry cùng key trả cùng booking, không tạo mới |
| S2-08 | Guest lookup bằng confirmation code + SĐT (rate limit + OTP) | Không lộ dữ liệu người khác |
| S2-09 | Pagination/filter/sort cho admin lists | Chuẩn `PageResponse` |
| S2-10 | Frontend: flow hủy/đổi lịch, hiển thị phí, waitlist | UAT với chủ tiệm |

---

### Sprint 3 — POS, Payment, Invoice & Tax (2,5 tuần) — **ưu tiên số 2**

| Task | Nội dung | Acceptance Criteria |
|---|---|---|
| S3-01 | Entity payment/split/refund/invoice/tax/tip + migration | Schema pass review |
| S3-02 | API checkout: ghi nhận dịch vụ thực tế, add-on, discount, tip, split payment (cash/card/bank transfer) | Tổng split = amount_due; sai → 400 |
| S3-03 | Deposit config + ghi nhận cọc thủ công (feature flag); interface `PaymentGateway` sẵn cho Stripe | Bật/tắt cọc không cần deploy; test refund cọc |
| S3-04 | Tax engine: VAT 19% / §19, tách net–VAT–gross, snapshot tax rate | Test 19% + §19; tổng invoice khớp payment |
| S3-05 | Invoice: cấp số tuần tự an toàn (khóa row), PDF đủ §14 UStG, Storno | Test 20 thread cấp số không trùng/không trống |
| S3-06 | Tip 80/20 + payout status + commission report | Báo cáo tip/commission khớp payment |
| S3-07 | Z-report cuối ngày + Kassenbuch + đối soát tiền mặt | Chênh lệch quỹ hiển thị rõ |
| S3-08 | DATEV/CSV export + báo cáo VAT tháng | File mở được bằng Excel/DATEV; Steuerberater review mẫu |
| S3-09 | Tích hợp **fiskaly SIGN DE** (sandbox → production) theo hướng dẫn 5.8.3 | Giao dịch có signature + QR; retry cùng `tx_id` không nhân đôi |
| S3-10 | POS UI cho lễ tân + in hoá đơn/receipt có QR TSE | UAT tại quầy |
| S3-11 | DSFinV-K export + gửi thông báo Finanzamt (§146a Abs. 4 AO) | File mở đúng chuẩn; có xác nhận đã thông báo |
| S3-12 | Test idempotency, split payment, refund, concurrent checkout, invoice numbering, TSE timeout | 100% case tiền tệ/thuế/TSE pass |

> **Không còn phụ thuộc:** provider TSE đã chốt (D17 – fiskaly) và sandbox lấy ở S0-10; mẫu DATEV lấy ở S0-10.

---

### Sprint 4 — Loyalty (Phương án 3), QR, Voucher, Review (2,5 tuần)

| Task | Nội dung | Acceptance Criteria |
|---|---|---|
| S4-01 | Loyalty account + transaction ledger + cấu hình `loyalty_program_config` (earn rate, point value, cap, tier thresholds) | Balance luôn = tổng ledger; đổi rule không cần deploy |
| S4-02 | QR token + scan API + idempotent | Scan 2 lần cùng bill → cộng 1 lần |
| S4-03 | Redeem điểm khi checkout (1 điểm = 0,05 EUR, cap theo hạng 10/15/20%) | Không cho redeem quá balance; hoàn điểm khi refund |
| S4-04 | Tier engine Silver/Gold/Platinum rolling 12 tháng + soft-landing + `tier_history` | Tính hạng đúng; test lên/xuống hạng |
| S4-05 | Quyền lợi hạng: sinh nhật, ưu tiên đặt lịch, đổi lịch muộn, free add-on | Rule cấu hình; test từng quyền lợi |
| S4-06 | Voucher: CRUD, validate, usage limit, expiry, stacking rule | Test hết hạn giữa checkout → 409 |
| S4-07 | Review: chỉ booking completed, 1 review/booking, moderation | Test verify bằng booking id; admin duyệt |
| S4-08 | Referral + welcome bonus + happy hour x2 điểm | Cả 2 nhận điểm; chống self-referral; idempotent |
| S4-09 | Frontend: ví điểm, QR, hạng, voucher, review | UAT |

---

### Sprint 5 — Notification, Reporting, Multi-branch readiness (2 tuần)

| Task | Nội dung | Acceptance Criteria |
|---|---|---|
| S5-01 | Outbox + worker + template + retry | Không mất notification khi service down |
| S5-02 | Email/SMS adapter + reminder 24h/2h | Test clock; log delivery status |
| S5-03 | Reporting: doanh thu theo ngày/thợ/dịch vụ, occupancy, no-show, retention, commission, **VAT/tax report** | Query aggregate < 500ms; export CSV/DATEV |
| S5-04 | Dashboard admin mới + filter ngày/chi nhánh + widget thuế | Frontend ghép |
| S5-05 | Chuẩn bị multi-branch: `branch_id` xuyên suốt + timezone | Test 2 branch khác timezone |
| S5-06 | Load test k6: availability 100 RPS, checkout 30 RPS | p95 < 300ms / 500ms; error < 0.1% |
| S5-07 | UAT cuối + pentest nhẹ + go-live checklist | Sign-off |

---

### Phase 3 (sau MVP, 6–8 tuần)

- Waitlist nâng cao + auto-fill.
- Loyalty nâng cao: tier benefits mở rộng, campaign engine, A/B test.
- Multi-branch thật (báo cáo hợp nhất, phân quyền theo branch).
- Commission nâng cao (theo service, theo revenue, tip share).
- **Stripe production**: Checkout/Payment Element cho cọc online, Stripe Terminal cho thẻ; webhook + reconciliation.
- Tích hợp Zalo OA / Momo / VNPAY (nếu mở thị trường VN).
- Mobile push, loyalty app.
- BI/warehouse (nếu chuỗi lớn).
- Tự động hoá export DATEV hằng tháng + đối soát ngân hàng.

---

## 7. Kế hoạch kiểm thử (Test Strategy)

### 7.1. Test pyramid

| Tầng | Công cụ | Phạm vi | Mục tiêu |
|---|---|---|---|
| Unit | JUnit 5 + Mockito | AvailabilityEngine, PolicyService, PricingEngine, LoyaltyCalculator, StateMachine | ≥ 80% service layer |
| Integration | `@SpringBootTest` + **Testcontainers MySQL 8** | Repository, transaction, lock, migration | Mọi query quan trọng |
| Contract | MockMvc + OpenAPI validator; Pact (nếu frontend đồng ý) | Toàn bộ endpoint | 100% endpoint có test |
| Concurrency | ExecutorService + Testcontainers | Đặt lịch, checkout, scan QR | Không double booking/credit |
| Security | Auth matrix + OWASP ZAP baseline + dependency-check | IDOR, role, JWT, rate limit | 0 critical/high |
| Performance | k6 | Availability, booking, checkout | p95 đạt SLO |
| E2E | Playwright/Newman trên staging | Flow A–E | Smoke trước release |
| UAT | Checklist với chủ tiệm | Nghiệp vụ thực tế | Sign-off |

### 7.2. Test case bắt buộc (trích)

**Availability:**
- [ ] Dịch vụ 90' không có slot nếu `start + 90 + buffer > closing`.
- [ ] Thợ nghỉ phép → không trả slot.
- [ ] Thợ không có skill → không trả slot.
- [ ] Booking 10:00–11:30 chặn slot 10:30, 11:00; không chặn 11:30 nếu buffer 0.
- [ ] Buffer 10' chặn slot sát giờ.
- [ ] Multi-service 45'+60' trả slot đủ 105'+buffer.
- [ ] "Any available" gán đúng thợ ít việc nhất.
- [ ] Timezone branch khác server.
- [ ] Ngày lễ/đóng cửa.
- [ ] Slot quá khứ + cutoff đặt trước.

**Booking:**
- [ ] 20 thread cùng slot → 1 success, 19 nhận 409.
- [ ] Idempotency-Key retry → cùng booking id.
- [ ] Cancel trước 24h OK; trong 24h 400; đã completed 409; đã cancelled 409.
- [ ] Reschedule sát giờ bị chặn; admin override có audit.
- [ ] Guest không xem được booking người khác.
- [ ] Staff chỉ thấy lịch mình.

**Payment/Loyalty:**
- [ ] Split payment tổng khớp.
- [ ] Thanh toán 2 lần cùng idempotency key → 1 payment.
- [ ] Refund trừ điểm đúng; không âm.
- [ ] QR scan 2 lần → 1 lần cộng.
- [ ] Voucher hết hạn giữa checkout → 409, không trừ tiền.
- [ ] Webhook replay → idempotent.

### 7.3. Coverage & quality gate

- JaCoCo: line ≥ 70%, branch ≥ 60% toàn project; service layer ≥ 80%.
- CI fail nếu coverage giảm > 2%.
- Không merge PR nếu thiếu test cho bug fix.
- Mỗi sprint có 1 buổi **bug bash** trên staging.

---

## 8. Kế hoạch khớp Frontend (Frontend Alignment)

### 8.1. Nguyên tắc

1. **OpenAPI là nguồn sự thật duy nhất** — markdown chỉ để đọc, không để code.
2. Backend publish `/v3/api-docs` + Swagger UI ở mọi môi trường (staging public nội bộ).
3. Frontend generate client bằng `openapi-typescript` + `openapi-fetch` (hoặc Orval).
4. Mock server Prism chạy từ `openapi.yaml` để frontend không chờ backend.
5. Thay đổi contract theo **expand → migrate → contract**; không breaking trong sprint.
6. Weekly 30' contract sync: backend + frontend + PO.

### 8.2. Checklist bàn giao mỗi sprint

- [ ] OpenAPI diff được review và merge.
- [ ] Postman/Newman collection cập nhật.
- [ ] Swagger UI staging chạy.
- [ ] Mock server cập nhật.
- [ ] Danh sách error code + HTTP status + message mapping.
- [ ] Ví dụ request/response cho từng flow.
- [ ] Frontend xác nhận đã ghép trên staging (không chỉ local).
- [ ] Ghi chú breaking change + kế hoạch deprecate.

### 8.3. Quy ước response & lỗi

```json
// Success
{
  "code": 200,
  "message": "Success",
  "data": { },
  "traceId": "a1b2c3",
  "timestamp": "2026-11-14T10:30:00Z"
}

// Error
{
  "code": 409,
  "errorCode": "SLOT_TAKEN",
  "message": "Khung giờ này vừa được đặt. Vui lòng chọn giờ khác.",
  "details": [{ "field": "time", "reason": "overlap" }],
  "traceId": "a1b2c3",
  "timestamp": "2026-11-14T10:30:00Z"
}
```

- Backend trả `errorCode` ổn định; frontend map sang tiếng Đức/Việt.
- Không trả message nghiệp vụ tiếng Đức hardcode trong `ErrorCode` nữa (hoặc dùng i18n `messages_de.properties`).

### 8.4. Phân công frontend theo sprint

| Sprint | Frontend làm gì | Phụ thuộc backend |
|---|---|---|
| S0 | Generate client, dựng mock server, chuẩn hóa error handling, refresh token | OpenAPI v1 |
| S1 | Chọn thợ + slot theo thợ, hiển thị duration/buffer | Availability v2 |
| S2 | Hủy/đổi lịch, phí, waitlist, tra cứu guest | Booking v2 |
| S3 | Màn hình POS, split payment (cash/card/bank), tip 80/20, in hoá đơn, xem VAT/Z-report | Checkout + Invoice + Tax API |
| S4 | Ví điểm, QR, voucher, review, hiển thị hạng + quyền lợi | Loyalty (Phương án 3)/Voucher/Review |
| S5 | Dashboard mới, filter, export, báo cáo VAT | Reporting |

---

## 9. Kế hoạch Deploy & Vận hành

### 9.1. Môi trường

| Môi trường | Mục đích | DB | Dữ liệu | Ai deploy |
|---|---|---|---|---|
| Local | Dev | Docker MySQL / H2 test | Seed | Dev |
| Dev | Tích hợp sớm | MySQL container | Seed + test | CI auto |
| Staging | UAT, demo chủ tiệm | MySQL managed nhỏ | Dữ liệu giả lập | CI auto |
| Production | Khách thật | MySQL managed + backup | Thật | Manual approve |

### 9.2. Cấu hình & bí mật

- `application-{env}.yml` cho từng môi trường; **không commit secret**.
- Secret qua GitHub Secrets / AWS Secrets Manager / Docker secrets.
- Biến bắt buộc: `SPRING_DATASOURCE_*`, `JWT_SECRET_KEY` (≥ 64 bytes), `JWT_VALID_DURATION`, `JWT_REFRESH_DURATION`, `ADMIN_PASSWORD`, `APP_CORS_ALLOWED_ORIGINS`, `TZ`.
- Tắt `spring.jpa.show-sql` ở production; bật structured logging.
- `ddl-auto=validate` ở production; Flyway quản lý schema.

### 9.3. Docker & CI/CD

```dockerfile
# Multi-stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
```

**Pipeline GitHub Actions:**
```
PR:      checkout → setup-java 21 → mvnw verify (test + jacoco) → dependency-check → openapi-diff → docker build (no push)
main:    PR checks → build & push image (GHCR, tag sha) → flyway migrate staging → deploy staging → smoke test
release: manual approve → flyway migrate prod (backward-compatible) → rolling deploy → smoke → notify
rollback: redeploy previous image tag; migration rollback chỉ khi có script down đã test
```

### 9.4. Observability

- **Actuator:** `/actuator/health` (liveness/readiness), `/actuator/metrics`, `/actuator/prometheus`.
- **Micrometer + Prometheus + Grafana**: HTTP latency, error rate, DB pool, booking success/fail, payment success/fail.
- **Logging:** JSON structured, `traceId` từ request header hoặc sinh mới; không log PII/password/token.
- **Error tracking:** Sentry (backend + frontend).
- **Alert:** 5xx > 1% trong 5', p95 > 1s, DB connection > 80%, payment fail > 2%, outbox backlog > 100.
- **Uptime:** healthcheck bên ngoài (UptimeRobot/Better Stack).

### 9.5. Backup & DR

- MySQL: backup tự động hằng ngày + binlog/PITR; giữ 30 ngày.
- **Restore drill mỗi quý** — ghi biên bản.
- RPO ≤ 15 phút, RTO ≤ 2 giờ (chốt với chủ tiệm).
- Export dữ liệu khách hàng theo yêu cầu (GDPR nếu thị trường Đức).

### 9.6. Release checklist

- [ ] Tất cả test xanh + coverage đạt.
- [ ] OpenAPI diff đã review.
- [ ] Migration chạy thử trên bản copy production.
- [ ] Backup trước deploy.
- [ ] Feature flag cho tính năng rủi ro (payment/loyalty).
- [ ] Smoke test staging pass.
- [ ] Rollback plan + người trực.
- [ ] Thông báo frontend/CSKH.
- [ ] Theo dõi 30' sau deploy.

---

## 10. Kế hoạch Maintain

### 10.1. Định kỳ

| Tần suất | Việc |
|---|---|
| Hằng ngày | Kiểm tra alert, log lỗi, payment fail, outbox backlog |
| Hằng tuần | Review dependency alert, backup status, dung lượng DB |
| Hằng tháng | Update dependency (Renovate PR), review chậm query, rotate secret nếu cần |
| Hằng quý | Restore drill, pentest nhẹ, review quyền truy cập, DR test |
| Hằng năm | Nâng cấp major framework, review kiến trúc, archive dữ liệu cũ |

### 10.2. Runbook tối thiểu

- App không start → check env, DB reachable, Flyway, port.
- 5xx tăng → xem traceId, rollback image nếu do release.
- DB connection pool cạn → kill query chậm, scale connection, tối ưu query.
- Payment webhook fail → replay từ provider dashboard, kiểm tra idempotency.
- Token cleanup job không chạy → check scheduler, log.
- Mất dữ liệu → restore backup gần nhất + replay binlog; thông báo PO.

### 10.3. Chính sách dữ liệu

- PII: mã hóa at-rest (managed DB), mask trong log.
- Retention: **invoice/hoá đơn 10 năm** (GoBD); booking/payment 7–10 năm theo yêu cầu kế toán; log 30–90 ngày; notification 90 ngày.
- GDPR: API export/xóa dữ liệu khách (nếu Đức); audit việc xóa.

---

## 11. Timeline tổng hợp

| Tuần | Sprint | Milestone |
|---|---|---|
| W1 | S0 | OpenAPI v1, CI/CD, staging, biên bản chốt D1–D18, fiskaly sandbox + mẫu DATEV |
| W2–W3 | S1 | Staff + Availability v2 + chống double-booking |
| W4–W5 | S2 | Booking v2: policy, waitlist, no-show, audit |
| W6–W8 | S3 (2,5 tuần) | POS + payment (cash/card/bank transfer) + tip 80/20 + invoice + tax/DATEV |
| W9–W11 | S4 (2,5 tuần) | Loyalty Phương án 3 + tier + QR + voucher + review + referral |
| W12–W13 | S5 | Notification + reporting + VAT report + load test |
| W14 | Hardening | UAT, pentest, Steuerberater sign-off, go-live |
| W15–W22 | Phase 3 | Multi-branch, Stripe production, TSE nâng cao, BI |

> Nếu chỉ 1 người làm backend: dự kiến **21–25 tuần** cho MVP (do thêm thuế/TSE + tier). Nếu có 2 backend: **10–12 tuần**.

---

## 12. Risk register

| Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu |
|---|---|---|---|
| Availability engine sai → double booking | Cao | Rất cao | Test concurrency + khóa staff row + unique index |
| Scope creep (multi-branch/BI khi chưa xong MVP) | Cao | Cao | Bám sprint, feature flag, MVP trước |
| Frontend lệch contract | Trung bình | Cao | OpenAPI + mock + sync hằng tuần |
| Payment provider thay đổi/không có sandbox | Trung bình | Cao | Adapter pattern, test sandbox sớm |
| Dữ liệu production bị mất | Thấp | Rất cao | Backup + PITR + restore drill |
| Thợ/lễ tân không dùng được UI | Trung bình | Cao | UAT tại quầy từ S3 |
| Timezone/multi-branch làm lại schema | Trung bình | Cao | Thêm `branch_id` + timezone ngay từ S1 |
| Thiếu người | Cao | Cao | Ưu tiên vertical slice, cắt scope Phase 3 |
| **Sai thuế/VAT hoặc hoá đơn không hợp lệ** | Trung bình | Rất cao | Steuerberater review sớm; test §14 UStG; DATEV export mẫu |
| **TSE/KassenSichV không đáp ứng** | Trung bình | Rất cao | Đã chốt fiskaly (D17); sandbox ở S0-10; `tse_transaction` + DSFinV-K; Steuerberater xác nhận trước go-live |
| **TSE mất mạng / provider down** | Trung bình | Trung bình | Retry cùng `tx_id`; đánh dấu `TSE_PENDING` + job đối soát cuối ngày; không chặn thu tiền |
| **Không gửi thông báo Finanzamt (§146a Abs. 4 AO)** | Thấp | Cao | Task S3-11 + mục checklist go-live |
| **Quy chế tip 80/20 không rõ ràng** | Trung bình | Cao | Tip không chịu VAT theo D15; tách khỏi revenue; ghi rõ quy chế nội bộ + Steuerberater |
| **Vượt ngưỡng §19 khi mở thêm tiệm** | Trung bình | Rất cao | Job theo dõi ngưỡng + alert 80/90/100%; runbook chuyển `STANDARD_19` (5.6.9) |
| **Cấp số hoá đơn trùng khi nhiều quầy** | Thấp | Rất cao | Khóa row `invoice_sequence`; test đồng thời |

---

## 13. Definition of Done (áp dụng cho mọi task)

- [ ] Code build + test pass local và CI.
- [ ] Có unit/integration test cho logic mới; bug fix có regression test.
- [ ] OpenAPI cập nhật + ví dụ request/response.
- [ ] Không log PII/secret; không hardcode config.
- [ ] Migration có thể chạy trên DB có dữ liệu (backward-compatible).
- [ ] Error code + message được frontend biết.
- [ ] Đã test trên staging với dữ liệu giả lập.
- [ ] Tài liệu runbook/README cập nhật nếu có thay đổi vận hành.
- [ ] PO/Frontend sign-off nếu là feature người dùng thấy.

---

## 14. Việc cần làm ngay tuần này (Next Actions)

1. **Ký biên bản chốt D1–D18** (mục 15.1) — nội bộ đã thống nhất ngày 2026-11-14.
2. **Gửi Steuerberater 3 câu hỏi ở 5.8.5** (§19/ngưỡng, nghĩa vụ TSE & Belegausgabe, hạch toán tip 80/20) — cần trả lời bằng văn bản trước S3.
3. **Đăng ký fiskaly sandbox (D17, S0-10)**, lưu `FISKALY_*` vào secret manager, ký thử 1 giao dịch.
4. **Backend freeze contract**: tạo `openapi.yaml`, bật springdoc, chuẩn hóa error response.
5. **Sửa nhanh các P1 rẻ**: `application.properties.example`, `GET /users` DTO, `SLOT_FULL` 409, Dockerfile multi-stage.
6. **Dựng CI + staging** trước khi bắt đầu S1.
7. **Viết test concurrency hiện tại** để chứng minh lỗi B-03 (làm bằng chứng trước/sau).
8. **Frontend generate client từ OpenAPI** và dựng mock server.
9. **Chuẩn bị tax trước S3**: seed `tax_config` §19 + mẫu hoá đơn §14 UStG gửi Steuerberater duyệt; lấy file DATEV mẫu.
10. **Lên lịch demo S1** với chủ tiệm: chọn thợ → chọn slot → đặt 2 dịch vụ liên tiếp.

---

## 15. Sign-off

| Vai trò | Tên | Xác nhận | Ngày |
|---|---|---|---|
| Product Owner / Chủ tiệm | | [ ] | |
| Backend Lead | | [ ] | |
| Frontend Lead | | [ ] | |
| QA Lead | | [ ] | |
| DevOps | | [ ] | |
| Steuerberater (thuế/kế toán) | | [ ] | |

### 15.1. Biên bản chốt quyết định — 2026-11-14

**Người chốt:** Chủ tiệm / Product Owner.  
**Phạm vi:** D1–D18 (mục 0.2).

**Kết luận:**

- **D1–D3:** Đức — EUR — tiếng Đức; 1 chi nhánh MVP (schema có `branch_id`); entity `staff` riêng.
- **D4–D5:** không cọc; hủy ≥24h miễn phí; trong 24h `WARN` + ghi nhận (không chặn); T2–T7 09:00–18:00, buffer 10', Chủ nhật nghỉ.
- **D6–D8:** tiền mặt + thẻ + chuyển khoản tại quầy; Stripe Phase 3; tip 80% thợ / 20% tiệm.
- **D7/D12/D18:** loyalty Phương án 3; Silver 0–299 / Gold 300–799 / Platinum ≥800 EUR; hệ số 1,0/1,25/1,5; cap 10/15/20%.
- **D9–D10:** multi-service tối đa 3, ưu tiên cùng thợ; email + SMS.
- **D11/D14–D17:** hoá đơn §14 UStG, GoBD, DATEV, DSFinV-K; §19 hiện tại + switchable VAT 19%; tip không VAT; TSE fiskaly SIGN DE.
- **D13:** Stripe (Checkout/Terminal) Phase 3.

**Điều kiện thay đổi:** chỉ khi có thay đổi pháp lý, yêu cầu của Finanzamt/Steuerberater hoặc chủ tiệm đổi mô hình kinh doanh; ghi version mới của Plan kèm ngày hiệu lực; **không sửa hoá đơn/booking cũ**.

**Còn chờ trước go-live:** chữ ký các vai trò ở bảng trên + trả lời bằng văn bản của Steuerberater cho 3 câu hỏi ở mục 5.8.5.

---

## Phụ lục A — API contract dự kiến (tóm tắt)

| Nhóm | Endpoint | Method | Quyền | Sprint |
|---|---|---|---|---|
| Auth | `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`, `/auth/me`, `/auth/password` | * | Public/User | Đã có |
| Auth | `/auth/forgot-password`, `/auth/verify-otp` | POST | Public | S2 |
| Catalog | `/services`, `/services/{id}`, `/services/{id}/addons`, `/combos` | GET/POST/PUT/DELETE | Public/Admin | S1–S2 |
| Staff | `/staff`, `/staff/{id}/shifts`, `/staff/{id}/time-off`, `/staff/{id}/skills` | * | Admin/Manager | S1 |
| Availability | `/availability?date&serviceIds&staffId` | GET | Public | S1 |
| Booking | `/bookings`, `/bookings/{id}`, `/bookings/my`, `/bookings/lookup` | * | Guest/User/Staff | S2 |
| Booking | `/bookings/{id}/cancel`, `/bookings/{id}/reschedule`, `/bookings/{id}/status` | PATCH | Owner/Staff/Admin | S2 |
| Waitlist | `/waitlist` | POST/GET/DELETE | User/Receptionist | S2 |
| POS | `/pos/checkout`, `/pos/bookings/{id}/bill`, `/pos/payments/{id}/refund` | POST/GET | Receptionist/Manager | S3 |
| POS | `/pos/tips/{id}/payout`, `/pos/z-report?date=` | POST/GET | Manager | S3 |
| Invoice | `/invoices/{id}`, `/invoices/{id}/pdf`, `/invoices/{id}/storno` | GET/POST | Owner/Receptionist | S3 |
| Tax | `/tax/config`, `/reports/vat?period=`, `/reports/datev?period=` | GET/PUT | Owner/Manager | S3 |
| TSE | `/tse/status`, `/reports/dsfinvk?period=` | GET | Manager/Admin | S3 |
| Commission | `/reports/commission?period=&staffId=` | GET | Manager/Admin | S5 |
| Loyalty | `/loyalty/account`, `/loyalty/qr`, `/loyalty/scan`, `/loyalty/redeem`, `/loyalty/config` | * | User/Staff/Admin | S4 |
| Voucher | `/vouchers`, `/vouchers/validate` | * | Admin/User | S4 |
| Review | `/reviews`, `/reviews/{id}/moderate`, `/reviews/{id}/reply` | * | User/Admin | S4 |
| Notification | `/notifications`, `/notifications/{id}/read` | GET/PATCH | User | S5 |
| Reporting | `/reports/revenue`, `/reports/occupancy`, `/reports/no-show`, `/reports/commission`, `/reports/vat` | GET | Manager/Admin | S5 |

## Phụ lục B — Quy ước kỹ thuật

- **Tiền:** `BigDecimal`, `currency` ISO-4217; không `double`. EUR mặc định.
- **Thuế:** mọi invoice lưu `net`, `vat_rate`, `vat_amount`, `gross`, `tax_mode`; không tính lại từ giá hiện tại.
- **Hoá đơn:** số tuần tự không trống, cấp trong transaction có khóa row; Storno thay vì sửa/xoá; lưu 10 năm.
- **Tip:** tách khỏi doanh thu dịch vụ; lưu tỷ lệ 80/20 và trạng thái payout.
- **TSE:** mọi giao dịch tiền có `tx_id` + signature + QR; `tse_transaction` append-only; không xoá kể cả khi lỗi.
- **Thời gian:** lưu UTC (`Instant`/`OffsetDateTime`), trả kèm `timezone` của branch; FE hiển thị local.
- **ID:** UUID cho user/customer; BIGINT cho booking/payment (tuần tự dễ đọc); public code riêng cho guest (`confirmation_code`).
- **Soft delete:** `active`/`deleted_at` cho service, staff, voucher; không xóa cứng dữ liệu giao dịch.
- **Audit:** mọi thay đổi tiền/điểm/quyền phải có `audit_log`.
- **Idempotency:** bắt buộc với POST booking, checkout, scan QR, webhook.
- **Pagination:** `?page=0&size=20&sort=createdAt,desc` → `{content, page, size, totalElements, totalPages}`.
- **Versioning:** URL `/api/v1/...` (hiện chưa có prefix; thêm ở S1 và giữ alias cũ 1 sprint).
