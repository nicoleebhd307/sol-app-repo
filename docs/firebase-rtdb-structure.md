# Sol An Bang — Firebase Realtime Database Structure (v3 — đã chốt rule)

> Business rules đã chốt (2026-07-04): 2 xe 4 chỗ chỉ dành cho Suite (Deluxe không có quyền truy cập dịch vụ xe); 4 view Deluxe = sea/street/garden/pool; Suite free spa, Deluxe 350.000đ/người/session; tiền tệ VND toàn hệ thống.
> Nguyên tắc: cây JSON phẳng, không JOIN; nhúng line-items vào đơn; index fan-out thay cho WHERE.
> §8 tổng hợp các pattern tham khảo được từ đồ án web đặt phòng cũ (`D:\convert_json\firebase-database.json`).

## 1. Accounts & Customers

```jsonc
{
  // Đăng nhập bằng username/password tự quản (không dùng Firebase Auth).
  // Key KHÔNG được chứa . # $ [ ] / → dùng username, hoặc email đã thay "." bằng ","
  "accounts": {
    "linhpham": {
      "passwordHash": "<sha256(password + salt)>",   // tuyệt đối không lưu plaintext
      "role": "customer",       // customer | system_admin | front_desk | fnb | wellness | transport | store | accountant
      "customerId": "c_3",      // role = customer → trỏ tới customers/
      "adminId": null           // role khác   → trỏ tới admins/
    }
  },

  "customers": {
    "c_3": {
      "fullName": "Linh Pham",
      "email": "linh.pham@example.com",
      "phone": "+84901000003",
      "dob": "1999-04-12",            // NEW
      "language": "vi",               // NEW (vi | en)
      "nationality": "Vietnam",
      "idPassport": "B12345678",
      "memberSince": 1751600000000,   // NEW — epoch millis lúc tạo tài khoản
      "membershipTier": "loyal",      // NEW — new | loyal | vip (xem rule §7)
      "stats": {                      // NEW — counters để tính hạng, cập nhật khi booking confirmed
        "bookingCount": 4,
        "suiteBookingCount": 1
      },
      "status": "in_stay"             // pre_stay | in_stay
    }
  },

  "admins": {
    "a_1": { "fullName": "...", "email": "...", "phone": "..." }
  }
}
```

## 2. Room Types (5 loại — ĐÃ CHỐT) & Rooms

```jsonc
{
  "roomTypes": {
    // 1 Suite + 4 Deluxe theo 4 view: sea, street, garden, pool
    "rt_suite": {
      "typeName": "Sol Suite",
      "category": "suite",
      "viewType": "sea",
      "basePrice": 2800000,           // VND / đêm
      "occupancy": { "baseAdults": 2, "baseChildren": 1, "maxExtraGuests": 1, "extraGuestFee": 500000 },
      "benefits": {
        "freeGym": true,
        "freeBreakfast": true,
        "spa": { "free": true, "pricePerPersonPerSession": 0 },
        "transfer": {                 // Suite: pickup free, drop-off tính phí lúc checkout
          "allowed": true,
          "pickupFree": true,
          "dropoffFee": 350000,
          "dropoffChargedAtCheckout": true
        }
      },
      "description": "...", "sizeSqft": 1200, "bedType": "King Bed",
      "amenities": ["..."], "imageUrl": "https://..."
    },
    "rt_deluxe_sea":    { "typeName": "Deluxe Sea View",    "category": "deluxe", "viewType": "sea",    "basePrice": 950000, "occupancy": { "...": "giống rt_suite" },
      "benefits": {
        "freeGym": true, "freeBreakfast": true,
        "spa": { "free": false, "pricePerPersonPerSession": 350000 },
        "transfer": { "allowed": false }          // Deluxe: KHÔNG được truy cập dịch vụ xe (ẩn hẳn UI, không chỉ là "trả phí")
      }
    },
    "rt_deluxe_street": { "typeName": "Deluxe Street View", "category": "deluxe", "viewType": "street", "basePrice": 950000, "benefits": "giống rt_deluxe_sea" },
    "rt_deluxe_garden": { "typeName": "Deluxe Garden View", "category": "deluxe", "viewType": "garden", "basePrice": 950000, "benefits": "giống rt_deluxe_sea" },
    "rt_deluxe_pool":   { "typeName": "Deluxe Pool View",   "category": "deluxe", "viewType": "pool",   "basePrice": 950000, "benefits": "giống rt_deluxe_sea" }
  },

  "rooms": {
    "r_101": { "roomTypeId": "rt_deluxe_sea", "roomNumber": "D101", "status": "available" }
  }
}
```

Rule chung sức chứa (theo note HoangDieu): **mỗi phòng 2 người lớn + 1 trẻ nhỏ**; thêm tối đa 1 người → tính `extraGuestFee` (cộng vào folio/tổng booking).

## 3. Bookings & Availability

```jsonc
{
  "bookings": {
    "<bookingId>": {                  // push key
      "bookingCode": "BK-2026-1046",
      "customerId": "c_3",
      "roomId": "r_101",
      "roomTypeId": "rt_deluxe_ocean",
      "roomTypeName": "Deluxe Ocean View",   // denormalize
      "roomNumber": "D101",
      "checkInDate": "2026-07-04",
      "checkOutDate": "2026-07-08",
      "numAdults": 2,
      "numChildren": 1,
      "extraGuests": 0,
      "roomTotal": 3800000,
      "extraGuestFee": 0,
      "taxesAndFees": 380000,
      "totalPrice": 4180000,
      "status": "confirmed",          // pending|confirmed|checked_in|checked_out|cancelled
      "createdAt": 1751600000000
    }
  },
  "bookingsByCustomer": { "c_3": { "<bookingId>": true } },
  "roomCalendar": {                   // query phòng trống: tải node này + rooms, lọc overlap client-side
    "r_101": { "<bookingId>": { "checkIn": "2026-07-04", "checkOut": "2026-07-08", "status": "confirmed" } }
  }
}
```

Tạo booking = **multi-path update** (atomic): `bookings` + `bookingsByCustomer` + `roomCalendar` + `payments` + `customers/{id}/stats` + `customers/{id}/membershipTier` + `customers/{id}/status`.

## 4. Payments & Folio (hóa đơn phòng — trả lúc checkout)

```jsonc
{
  "payments": {
    "<paymentId>": {
      "bookingId": "...", "customerId": "...",
      "amount": 1254000,
      "paymentMethod": "e_wallet",    // e_wallet | bank_card | cash | room_bill
      "paymentType": "deposit",       // deposit | full_room | service | folio_settlement
      "status": "success",            // pending | success | failed
      "paidAt": 0, "createdAt": 0
    }
  },
  "paymentsByBooking": { "<bookingId>": { "<paymentId>": true } },

  // Các khoản "pay thêm lúc checkout" (drop-off của Suite, extra guest, room_bill orders...)
  "folios": {
    "<bookingId>": {
      "<chargeId>": {
        "type": "transfer_dropoff",   // transfer_dropoff | extra_guest | room_service | store | spa
        "refId": "<transferBookingId>",
        "amount": 350000,
        "status": "pending",          // pending → settled khi checkout
        "createdAt": 0
      }
    }
  }
}
```

## 5. Spa / Wellness (theo ca & session 40 phút — ĐÃ CHỐT giá)

```jsonc
{
  "spaConfig": {
    "sessionMinutes": 40,
    "shifts": {
      "morning":   { "start": "08:00", "end": "15:00" },
      "afternoon": { "start": "14:00", "end": "21:00" }
    }
  },
  "spaStaff": {                       // 10 nhân viên
    "st_01": { "name": "...", "shift": "morning" },
    "st_06": { "name": "...", "shift": "afternoon" }
  },
  // Sức chứa mỗi session = số staff đang trong ca tại giờ đó (giờ giao ca 14:00–15:00 = cả 2 ca)
  "spaCalendar": {
    "2026-07-05": { "08:00": { "bookedCount": 3 }, "08:40": { "bookedCount": 1 } }
  },
  "wellnessServices": {
    "w_gym":  { "serviceName": "Gym Access", "serviceType": "gym" },
    "w_spa":  { "serviceName": "Spa Treatment", "serviceType": "spa" },
    "w_yoga": { "serviceName": "Yoga Class", "serviceType": "yoga" }
  },
  "wellnessBookings": {
    "<id>": {
      "bookingId": "...", "customerId": "...",
      "serviceType": "spa",
      "sessions": {                   // khách đặt được NHIỀU session trong 1 lần → tiền nhân theo session × người
        "2026-07-05_08:00": { "numGuests": 2 },
        "2026-07-05_08:40": { "numGuests": 2 }
      },
      "pricePerPersonPerSession": 350000,   // Suite = 0 (free spa); Deluxe = 350.000đ/người/session
      "totalPrice": 1400000,
      "status": "confirmed"           // pending|confirmed|completed|cancelled
    }
  }
}
```

## 6. Transfer / Dining / Store

```jsonc
{
  "transferConfig": {
    "carSeats": 4, "maxAdults": 4, "maxChildren": 2,
    "fleetSize": 2,                   // ĐÃ CHỐT: 2 xe 4 chỗ
    "restrictedToCategory": "suite"   // ĐÃ CHỐT: Deluxe hoàn toàn không truy cập được dịch vụ này (client ẩn hẳn mục Transfer, server-side reject nếu cố gọi)
  },
  "transferBookings": {
    "<id>": {
      "bookingId": "...", "transferType": "airport_pickup",  // airport_pickup | airport_dropoff | resort_tour
      "pickupLocation": "...", "dropoffLocation": "...",
      "scheduledDatetime": "2026-07-05 13:00",
      "numAdults": 2, "numChildren": 1,      // > 4 NL hoặc > 2 TE → app chặn, hiện "liên hệ CSKH"
      "price": 0,                            // pickup Suite = 0; dropoff → ghi charge vào folios
      "status": "confirmed"
    }
  },
  "transferCalendar": { "2026-07-05": { "<transferBookingId>": true } },  // đếm ≤ fleetSize (2)

  "diningReservations": {
    "<id>": { "bookingId": "...", "venueType": "restaurant", "reservationDate": "...", "reservationTime": "...", "numGuests": 4, "status": "pending", "note": "" }
  },

  "storeProducts": { "p_1": { "productName": "...", "category": "souvenir", "price": 250000, "stockQuantity": 20, "isAvailable": true } },
  "storeOrders": {
    "<id>": {
      "bookingId": "...",
      "items": { "p_1": { "productName": "...", "quantity": 1, "unitPrice": 250000 } },
      "totalAmount": 250000,
      "deliveryType": "room_delivery",       // rule: quà lưu niệm luôn giao tại phòng
      "status": "pending", "orderedAt": 0
    }
  }
}
```

## 7. Room Service, Notifications, Index dịch vụ

```jsonc
{
  "menuItems": { "m_1": { "itemName": "...", "category": "main", "price": 240000, "description": "...", "imageUrl": "...", "isAvailable": true } },
  "roomServiceOrders": {
    "<orderId>": {
      "orderCode": "RS-2026-1045", "bookingId": "...", "customerId": "...",
      "items": { "m_1": { "itemName": "...", "quantity": 2, "unitPrice": 240000 } },
      "kitchenNote": "", "subtotal": 0, "deliveryFee": 0, "serviceCharge": 0, "totalAmount": 0,
      "paymentMethod": "room_bill",
      "status": "preparing",          // confirmed|preparing|on_the_way|delivered|cancelled
      "orderedAt": 0
    }
  },
  "ordersByBooking": { "<bookingId>": { "<orderId>": true } },
  "servicesByBooking": {              // Guest Dashboard "My Services" đọc 1 node duy nhất
    "<bookingId>": { "dining_<id>": { "type": "dining", "refId": "<id>" } }
  },
  "notifications": { "<customerId>": { "<notifId>": { "title": "...", "body": "...", "type": "booking", "isRead": false, "sentAt": 0 } } },
  "homeRecommendations": { "rec_1": { "title": "...", "description": "...", "type": "dining" } },
  "meta": { "counters": { "booking": 1046, "roomServiceOrder": 1045 } }   // runTransaction() để sinh mã BK-/RS-
}
```

## Business rules cài đặt ở tầng logic

**Hạng thành viên** (tính lại mỗi khi booking chuyển `confirmed`, ưu tiên từ trên xuống):

| Điều kiện | membershipTier |
|---|---|
| `suiteBookingCount` > 10 | `vip` |
| `bookingCount` > 3 | `loyal` |
| mặc định (mới tạo tài khoản) | `new` |

**Quyền lợi theo loại phòng (ĐÃ CHỐT):**

| | Suite | Deluxe |
|---|---|---|
| Gym | Free | Free |
| Bữa sáng | Free | Free |
| Spa | Free | 350.000đ / người / session |
| Đặt xe | Có quyền — pickup free; drop-off trả phí lúc checkout (ghi vào `folios`) | **Không có quyền truy cập dịch vụ** (client ẩn mục Transfer khỏi menu, không phải chỉ "mất phí") |

**Spa:** session 40 phút; ca sáng 08:00–15:00, ca chiều 14:00–21:00; capacity mỗi session = số nhân viên đang trong ca (`spaStaff`); một khách đặt được nhiều session trong 1 lần, tiền = số session × số người × đơn giá (0đ nếu Suite).

**Xe:** đội xe cố định **2 xe 4 chỗ**, mặc định phục vụ Suite; tối đa 4 người lớn + 2 trẻ em/xe; vượt số người → UI hiện "liên hệ CSKH"; vượt 2 xe cùng khung giờ → hiện hết chỗ. Deluxe không thấy mục này trong app.

**Phòng:** 2 người lớn + 1 trẻ nhỏ; thêm tối đa 1 người, tính `extraGuestFee` vào tổng booking.

**Tiền tệ:** VND toàn hệ thống (đã chốt). Mock SQLite trong app Android hiện đang demo bằng USD theo design Figma ban đầu — cần một đợt migrate riêng: đổi cột giá, format hiển thị (`$%,.0f` → `%,.0fđ`), và style input tiền trên các màn checkout/payment.

## Còn lại cần nhóm quyết định (không chặn tiến độ)

1. Mức `extraGuestFee` và `dropoffFee` cụ thể (đang để tạm 500.000đ / 350.000đ — tham khảo giá minibar/spa của đồ án cũ).
2. Có tách hiển thị `serviceCharge` (5%) và `vat` (10%) thành 2 dòng riêng trên hóa đơn (như đồ án web cũ), hay gộp chung 1 dòng "Thuế & phí" (như UI Figma hiện tại của mobile app)? — xem §8 bên dưới.
3. Có hỗ trợ đặt **nhiều phòng trong 1 booking** không, hay giữ nguyên 1 phòng/1 booking như luồng mobile hiện tại? — xem §8.

## 8. Tham khảo từ đồ án web cũ (`firebase-database.json`)

Đây là DB thật của một đồ án hotel booking trước đó (chỉ có luồng đặt phòng, không có room service/wellness/transfer riêng). Một số pattern đáng lấy sang, và một số chỉ để hiểu context chứ không áp dụng:

**Nên áp dụng:**
- **Booking hold & tự hủy**: booking ở trạng thái chờ thanh toán có field `holdExpiresAt` (dữ liệu mẫu cho thấy hold ~20–30 phút kể từ `createdAt`); một job định kỳ (Cloud Function `onSchedule` hoặc client check khi mở app) quét các booking `status = pending` quá hạn → tự chuyển `cancelled` với note `"Auto-cancelled: payment hold expired"`. Nên thêm field này vào `bookings/{id}` trong cấu trúc mới — hiện đang thiếu, và luồng `BookingCheckoutActivity` hiện tại confirm ngay nên chưa cần, nhưng nếu sau này tách bước "giữ chỗ" trước khi thanh toán thì bắt buộc phải có.
- **`refund_status` tách riêng khỏi `status`**: đồ án cũ dùng `status: cancelled` + `refund_status: none|pending|awaiting_refund|refunded` như 2 vòng đời độc lập (hủy xong không có nghĩa là đã hoàn tiền). Cấu trúc mới nên thêm `refundStatus` vào `bookings/{id}` với cùng 4 giá trị này.
- **Tách `accounts` khỏi profile** (`accounts` + `staffs` liên kết qua `account_id`): đúng tinh thần node `accounts` mới thêm ở §1 — xác nhận hướng tách bảng đăng nhập khỏi bảng thông tin là đúng.
- **Thêm `momo`** vào enum `paymentMethod`: đồ án cũ có ví dụ thanh toán qua Momo (kể cả case `failed`) — nếu khách sạn thật hỗ trợ Momo, thêm vào cạnh `e_wallet | bank_card | cash | room_bill`.
- **`serviceCatalog` cho trang chủ**: node `services` của đồ án cũ (title + imageUrl: Laundry, Spa & Massage, Swimming Pool, Fitness Center, Conference Room...) là danh mục giới thiệu (marketing), không phải bảng nghiệp vụ có booking riêng. Có thể thêm 1 node tương tự `serviceCatalog` để hiển thị mục "Khám phá tiện ích khách sạn" ở Trang chủ — độc lập với các bảng nghiệp vụ (`wellnessServices`, `menuItems`...).

**Chỉ để hiểu context, KHÔNG áp dụng nguyên bản:**
- **`extraServices` nhúng dạng mảng tự do** (`{ name, amount, _id }` gắn thẳng vào booking) — đơn giản vì đồ án cũ không có luồng dịch vụ riêng biệt. App mobile hiện tại đã có model chi tiết hơn (wellness/transfer/room-service với slot, trạng thái, giá riêng) nên **giữ nguyên cấu trúc bảng nghiệp vụ tách riêng** đã thiết kế ở §5–§7, không lùi về mảng tự do.
- **`rooms` là mảng trong booking** (hỗ trợ đặt nhiều phòng/1 lần): là quyết định phạm vi, không phải lỗi kỹ thuật. Luồng mobile hiện tại (RoomBookingActivity → BookingCheckoutActivity) mặc định 1 phòng/booking. Nếu nhóm muốn hỗ trợ đặt nhiều phòng cùng lúc, cần đổi `bookings/{id}/roomId` (string đơn) thành `bookings/{id}/rooms/{roomId}: { roomTypeId, price }` (map) — ảnh hưởng dây chuyền tới `roomCalendar`, `folios`, tính tiền. Đề xuất **giữ đơn giản 1 phòng/booking** cho đồ án, trừ khi đề bài yêu cầu rõ.
- **`service_charge` (5%) + `vat` (10%) tách riêng theo từng room_type**: đồ án cũ hiển thị 2 dòng phí riêng trên hóa đơn. UI Figma hiện tại của mobile app chỉ có 1 dòng "Taxes & Fees" gộp 10%. Nếu nhóm muốn hóa đơn chi tiết hơn (đúng chuẩn khách sạn thật), có thể tách `bookings/{id}` thành `serviceCharge` + `vat` thay vì `taxesAndFees` gộp — nhưng đây là thay đổi UI, cần quyết định trước khi code.
- **Giá phòng tham khảo** (1.1M–2.8M cho 5 hạng, giống thang giá) chỉ mang tính tham khảo thị trường — giá chính thức của Sol An Bang là **950.000đ (Deluxe) / 2.800.000đ (Suite)** theo note của nhóm, giữ nguyên không đổi theo đồ án cũ.

## Security Rules (phác thảo)

```jsonc
{
  "rules": {
    "accounts":  { "$username": { ".read": true, ".write": false } },   // chỉ đọc để login; tạo tài khoản qua admin/CF
    "customers": { "$cid": { ".read": true, ".write": true } },          // đơn giản cho đồ án; siết sau bằng auth thật
    "roomTypes": { ".read": true, ".write": false },
    "bookings":  { ".read": true, ".write": true },
    "menuItems": { ".read": true, ".write": false }
  }
}
```
> Lưu ý: vì login tự quản (không Firebase Auth) nên rules không thể phân quyền theo `auth.uid`. Chấp nhận mở cho đồ án; nếu cần bảo mật thật, chuyển sang Firebase Auth custom token hoặc Cloud Functions làm API trung gian.
