package com.example.sol_repo.dals;

import com.example.sol_repo.models.BookingCreationResult;
import com.example.sol_repo.models.BookingSummary;
import com.example.sol_repo.models.Customer;
import com.example.sol_repo.models.DiningTable;
import com.example.sol_repo.models.HomeServiceItem;
import com.example.sol_repo.models.MenuItem;
import com.example.sol_repo.models.OrderCreationResult;
import com.example.sol_repo.models.OrderLine;
import com.example.sol_repo.models.RecommendationItem;
import com.example.sol_repo.models.RoomServiceOrder;
import com.example.sol_repo.models.RoomType;
import com.example.sol_repo.models.StoreProduct;
import com.example.sol_repo.utils.RoomServiceCart;
import com.example.sol_repo.utils.StoreCart;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Realtime Database backed replacement for the retired MockDatabaseDal (SQLite). */
public class FirebaseDatabaseDal {
    private static final long BOOKING_CODE_SEED = 1045L;
    private static final long ORDER_CODE_SEED = 1045L;
    private static final long CUSTOMER_ID_SEED = 3L;
    private static final long STORE_ORDER_SEED = 122L;
    private static final long DINING_RES_SEED = 1200L;
    private static final long SPA_BOOKING_SEED = 1000L;
    private static final long TRANSFER_BOOKING_SEED = 1000L;

    /** Number of spa specialists on staff — the per-session slot capacity. */
    public static final int SPA_SESSION_CAPACITY = 10;

    private final DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();

    // ===================== Auth / Customer =====================

    public void loginCustomer(String email, String password, FirebaseCallback<Customer> callback) {
        String accountKey = sanitizeEmailKey(email);
        readOnce(rootRef.child("accounts").child(accountKey), accountSnapshot -> {
            if (!accountSnapshot.exists()
                    || !password.equals(accountSnapshot.child("password").getValue(String.class))) {
                callback.onSuccess(null);
                return;
            }
            String customerId = accountSnapshot.child("customerId").getValue(String.class);
            if (customerId == null) {
                callback.onSuccess(null);
                return;
            }
            fetchCustomer(customerId, callback::onSuccess, callback::onError);
        }, callback::onError);
    }

    public void getCustomer(String customerId, FirebaseCallback<Customer> callback) {
        fetchCustomer(customerId, callback::onSuccess, callback::onError);
    }

    private void fetchCustomer(String customerId, ValueListener<Customer> onValue, FailureListener onFailure) {
        readOnce(rootRef.child("customers").child(customerId), snapshot ->
                onValue.onValue(snapshot.exists() ? readCustomer(customerId, snapshot) : null), onFailure);
    }

    public void emailExists(String email, FirebaseCallback<Boolean> callback) {
        readOnce(rootRef.child("accounts").child(sanitizeEmailKey(email)), snapshot ->
                callback.onSuccess(snapshot.exists()), callback::onError);
    }

    /**
     * Creates a brand-new customer account. Writes /accounts and /customers atomically.
     * New sign-ups always start as membershipTier "new" and status "pre_stay".
     * Returns the created Customer (with generated customerId) on success, or null if the
     * email was taken between the availability check and the write.
     */
    public void createCustomerAccount(String fullName, String email, String password, String phone,
                                      String dob, String nationality, String language, String idPassport,
                                      FirebaseCallback<Customer> callback) {
        String accountKey = sanitizeEmailKey(email);
        readOnce(rootRef.child("accounts").child(accountKey), existing -> {
            if (existing.exists()) {
                callback.onSuccess(null);
                return;
            }

            nextSequence("customer", CUSTOMER_ID_SEED, sequence -> {
                String customerId = "c_" + sequence;
                String now = currentTimestamp();
                long memberSince = System.currentTimeMillis();

                Map<String, Object> account = new HashMap<>();
                account.put("email", email);
                account.put("password", password);
                account.put("role", "customer");
                account.put("customerId", customerId);

                Map<String, Object> stats = new HashMap<>();
                stats.put("bookingCount", 0);
                stats.put("suiteBookingCount", 0);

                Map<String, Object> customer = new HashMap<>();
                customer.put("fullName", fullName);
                customer.put("email", email);
                customer.put("phone", phone);
                customer.put("dob", dob);
                customer.put("nationality", nationality);
                customer.put("language", language);
                customer.put("idPassport", idPassport);
                customer.put("membershipTier", "new");
                customer.put("status", "pre_stay");
                customer.put("stats", stats);
                customer.put("memberSince", memberSince);
                customer.put("createdAt", now);

                Map<String, Object> updates = new HashMap<>();
                updates.put("/accounts/" + accountKey, account);
                updates.put("/customers/" + customerId, customer);

                rootRef.updateChildren(updates, (error, ref) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    Customer created = new Customer(customerId, fullName, email, phone, "pre_stay", now);
                    created.setDob(dob);
                    created.setNationality(nationality);
                    created.setLanguage(language);
                    created.setMembershipTier("new");
                    callback.onSuccess(created);
                });
            }, callback::onError);
        }, callback::onError);
    }

    // ===================== Bookings =====================

    public void getCurrentBooking(String customerId, FirebaseCallback<BookingSummary> callback) {
        fetchBookingsForCustomer(customerId, bookings -> {
            for (BookingSummary booking : bookings) {
                if ("checked_in".equals(booking.getStatus())) {
                    callback.onSuccess(booking);
                    return;
                }
            }
            for (BookingSummary booking : bookings) {
                if ("confirmed".equals(booking.getStatus())) {
                    callback.onSuccess(booking);
                    return;
                }
            }
            callback.onSuccess(null);
        }, callback::onError);
    }

    public void getBookingForCustomer(String customerId, String bookingId, FirebaseCallback<BookingSummary> callback) {
        readOnce(rootRef.child("bookings").child(bookingId), snapshot -> {
            if (!snapshot.exists() || !customerId.equals(snapshot.child("customerId").getValue(String.class))) {
                callback.onSuccess(null);
                return;
            }
            callback.onSuccess(readBooking(bookingId, snapshot));
        }, callback::onError);
    }

    public void getBookingsForCustomer(String customerId, FirebaseCallback<List<BookingSummary>> callback) {
        fetchBookingsForCustomer(customerId, callback::onSuccess, callback::onError);
    }

    private void fetchBookingsForCustomer(String customerId, ValueListener<List<BookingSummary>> onValue,
                                          FailureListener onFailure) {
        readOnce(rootRef.child("bookingsByCustomer").child(customerId), indexSnapshot -> {
            List<String> bookingIds = childKeys(indexSnapshot);
            if (bookingIds.isEmpty()) {
                onValue.onValue(new ArrayList<>());
                return;
            }

            List<BookingSummary> bookings = new ArrayList<>();
            AtomicCountdown countdown = new AtomicCountdown(bookingIds.size(), () -> {
                bookings.sort(Comparator.comparing(BookingSummary::getCheckInDate));
                onValue.onValue(bookings);
            });

            for (String bookingId : bookingIds) {
                readOnce(rootRef.child("bookings").child(bookingId), snapshot -> {
                    if (snapshot.exists()) {
                        bookings.add(readBooking(bookingId, snapshot));
                    }
                    countdown.tick();
                }, error -> countdown.tick());
            }
        }, onFailure);
    }

    public void getRoomNumberForBooking(String bookingId, FirebaseCallback<String> callback) {
        readOnce(rootRef.child("bookings").child(bookingId).child("roomNumber"), snapshot -> {
            String roomNumber = snapshot.getValue(String.class);
            callback.onSuccess(roomNumber == null ? "" : roomNumber);
        }, callback::onError);
    }

    public void getRoomTypeImageUrl(String roomTypeId, FirebaseCallback<String> callback) {
        if (roomTypeId == null) {
            callback.onSuccess(null);
            return;
        }
        readOnce(rootRef.child("roomTypes").child(roomTypeId).child("imageUrl"), snapshot ->
                callback.onSuccess(snapshot.getValue(String.class)), callback::onError);
    }

    // ===================== Home dashboard =====================

    /**
     * Lists every service tied to a booking for the home "My Services" section: indexed
     * reservations (dining / transfer / wellness), room service orders and souvenir orders.
     * No cap is applied — the full set for {@code bookingId} is returned.
     */
    public void getHomeServices(String bookingId, FirebaseCallback<List<HomeServiceItem>> callback) {
        readOnce(rootRef.child("servicesByBooking").child(bookingId), indexSnapshot -> {
            List<DataSnapshot> entries = new ArrayList<>();
            for (DataSnapshot entry : indexSnapshot.getChildren()) {
                entries.add(entry);
            }
            resolveHomeServicesSequentially(entries, 0, new ArrayList<>(), indexedServices ->
                    appendRoomServiceOrders(bookingId, indexedServices, withRoomService ->
                            appendStoreOrders(bookingId, withRoomService, callback::onSuccess)));
        }, callback::onError);
    }

    /** Appends each room service order for the booking as a "Room Service" entry. */
    private void appendRoomServiceOrders(String bookingId, List<HomeServiceItem> accumulated,
                                         ValueListener<List<HomeServiceItem>> onDone) {
        readOnce(rootRef.child("ordersByBooking").child(bookingId), indexSnapshot -> {
            List<String> orderIds = childKeys(indexSnapshot);
            if (orderIds.isEmpty()) {
                onDone.onValue(accumulated);
                return;
            }
            AtomicCountdown countdown = new AtomicCountdown(orderIds.size(),
                    () -> onDone.onValue(accumulated));
            for (String orderId : orderIds) {
                readOnce(rootRef.child("roomServiceOrders").child(orderId), snapshot -> {
                    if (snapshot.exists()) {
                        accumulated.add(new HomeServiceItem(
                                "Room Service",
                                snapshot.child("orderCode").getValue(String.class),
                                snapshot.child("status").getValue(String.class),
                                "roomservice"));
                    }
                    countdown.tick();
                }, error -> countdown.tick());
            }
        }, error -> onDone.onValue(accumulated));
    }

    /** Appends each souvenir store order for the booking as a "Souvenirs" entry. */
    private void appendStoreOrders(String bookingId, List<HomeServiceItem> accumulated,
                                   ValueListener<List<HomeServiceItem>> onDone) {
        readOnce(rootRef.child("storeOrdersByBooking").child(bookingId), indexSnapshot -> {
            List<String> orderIds = childKeys(indexSnapshot);
            if (orderIds.isEmpty()) {
                onDone.onValue(accumulated);
                return;
            }
            AtomicCountdown countdown = new AtomicCountdown(orderIds.size(),
                    () -> onDone.onValue(accumulated));
            for (String orderId : orderIds) {
                readOnce(rootRef.child("storeOrders").child(orderId), snapshot -> {
                    if (snapshot.exists()) {
                        accumulated.add(new HomeServiceItem(
                                "Souvenirs",
                                snapshot.child("orderCode").getValue(String.class),
                                snapshot.child("status").getValue(String.class),
                                "souvenir"));
                    }
                    countdown.tick();
                }, error -> countdown.tick());
            }
        }, error -> onDone.onValue(accumulated));
    }

    private void resolveHomeServicesSequentially(List<DataSnapshot> entries, int index,
                                                  List<HomeServiceItem> accumulated,
                                                  ValueListener<List<HomeServiceItem>> onDone) {
        if (index >= entries.size()) {
            onDone.onValue(accumulated);
            return;
        }

        DataSnapshot entry = entries.get(index);
        String type = entry.child("type").getValue(String.class);
        String refId = entry.child("refId").getValue(String.class);
        Runnable next = () -> resolveHomeServicesSequentially(entries, index + 1, accumulated, onDone);

        if ("transfer".equals(type)) {
            readOnce(rootRef.child("transferBookings").child(refId), snapshot -> {
                if (snapshot.exists()) {
                    String transferType = snapshot.child("transferType").getValue(String.class);
                    accumulated.add(new HomeServiceItem(
                            "dropoff".equals(transferType) ? "Airport Drop-off" : "Airport Pickup",
                            snapshot.child("scheduledDatetime").getValue(String.class),
                            snapshot.child("status").getValue(String.class),
                            "transfer"));
                }
                next.run();
            }, error -> next.run());
        } else if ("dining".equals(type)) {
            readOnce(rootRef.child("diningReservations").child(refId), snapshot -> {
                if (snapshot.exists()) {
                    String venueType = snapshot.child("venueType").getValue(String.class);
                    accumulated.add(new HomeServiceItem(
                            formatVenueTitle(venueType),
                            snapshot.child("reservationDate").getValue(String.class) + " "
                                    + snapshot.child("reservationTime").getValue(String.class),
                            snapshot.child("status").getValue(String.class),
                            "restaurant"));
                }
                next.run();
            }, error -> next.run());
        } else if ("wellness".equals(type)) {
            readOnce(rootRef.child("wellnessBookings").child(refId), snapshot -> {
                if (!snapshot.exists()) {
                    next.run();
                    return;
                }
                String serviceId = snapshot.child("wellnessServiceId").getValue(String.class);
                readOnce(rootRef.child("wellnessServices").child(serviceId), serviceSnapshot -> {
                    accumulated.add(new HomeServiceItem(
                            serviceSnapshot.child("serviceName").getValue(String.class),
                            snapshot.child("scheduledDate").getValue(String.class) + " "
                                    + snapshot.child("scheduledTime").getValue(String.class),
                            snapshot.child("status").getValue(String.class),
                            "wellness"));
                    next.run();
                }, error -> next.run());
            }, error -> next.run());
        } else if ("spa".equals(type)) {
            readOnce(rootRef.child("spaBookings").child(refId), snapshot -> {
                if (snapshot.exists()) {
                    accumulated.add(new HomeServiceItem(
                            snapshot.child("serviceName").getValue(String.class),
                            snapshot.child("reservationDate").getValue(String.class) + " "
                                    + firstSpaSession(snapshot),
                            snapshot.child("status").getValue(String.class),
                            "wellness"));
                }
                next.run();
            }, error -> next.run());
        } else {
            next.run();
        }
    }

    /** Reads the first (earliest) session label from a spa booking snapshot, or "" if none. */
    private String firstSpaSession(DataSnapshot spaSnapshot) {
        for (DataSnapshot session : spaSnapshot.child("sessions").getChildren()) {
            String label = session.getValue(String.class);
            return label == null ? "" : label;
        }
        return "";
    }

    public void getRecommendations(FirebaseCallback<List<RecommendationItem>> callback) {
        readOnce(rootRef.child("homeRecommendations"), snapshot -> {
            List<RecommendationItem> recommendations = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                recommendations.add(new RecommendationItem(
                        child.child("title").getValue(String.class),
                        child.child("description").getValue(String.class),
                        child.child("type").getValue(String.class)));
                if (recommendations.size() >= 2) {
                    break;
                }
            }
            callback.onSuccess(recommendations);
        }, callback::onError);
    }

    // ===================== Room booking flow =====================

    public void getAvailableRoomTypes(String checkInDate, String checkOutDate,
                                      FirebaseCallback<List<RoomType>> callback) {
        fetchAvailableRoomTypes(checkInDate, checkOutDate, callback::onSuccess, callback::onError);
    }

    private void fetchAvailableRoomTypes(String checkInDate, String checkOutDate,
                                         ValueListener<List<RoomType>> onValue, FailureListener onFailure) {
        readOnce(rootRef.child("roomTypes"), roomTypesSnapshot ->
                readOnce(rootRef.child("rooms"), roomsSnapshot ->
                        readOnce(rootRef.child("roomCalendar"), calendarSnapshot -> {
                            Map<String, Integer> availableCountByType = new HashMap<>();

                            for (DataSnapshot roomSnapshot : roomsSnapshot.getChildren()) {
                                String status = roomSnapshot.child("status").getValue(String.class);
                                if ("maintenance".equals(status)) {
                                    continue;
                                }
                                String roomTypeId = roomSnapshot.child("roomTypeId").getValue(String.class);
                                String roomId = roomSnapshot.getKey();
                                if (isRoomFree(calendarSnapshot.child(roomId), checkInDate, checkOutDate)) {
                                    availableCountByType.merge(roomTypeId, 1, Integer::sum);
                                }
                            }

                            List<RoomType> roomTypes = new ArrayList<>();
                            for (DataSnapshot typeSnapshot : roomTypesSnapshot.getChildren()) {
                                int availableRooms = availableCountByType.getOrDefault(typeSnapshot.getKey(), 0);
                                if (availableRooms > 0) {
                                    roomTypes.add(readRoomType(typeSnapshot, availableRooms));
                                }
                            }
                            roomTypes.sort(Comparator.comparingDouble(RoomType::getBasePrice));
                            onValue.onValue(roomTypes);
                        }, onFailure), onFailure), onFailure);
    }

    public void getRoomType(String roomTypeId, String checkInDate, String checkOutDate,
                            FirebaseCallback<RoomType> callback) {
        fetchAvailableRoomTypes(checkInDate, checkOutDate, roomTypes -> {
            for (RoomType roomType : roomTypes) {
                if (roomType.getRoomTypeId().equals(roomTypeId)) {
                    callback.onSuccess(roomType);
                    return;
                }
            }
            callback.onSuccess(null);
        }, callback::onError);
    }

    public void createBooking(String customerId, String roomTypeId, String checkInDate, String checkOutDate,
                              int numGuests, double totalPrice, double depositAmount, String paymentMethod,
                              String fullName, String email, String phone, boolean updateProfile,
                              FirebaseCallback<BookingCreationResult> callback) {
        readOnce(rootRef.child("roomTypes").child(roomTypeId).child("typeName"), typeNameSnapshot -> {
            String roomTypeName = typeNameSnapshot.getValue(String.class);
            resolveAvailableRoom(roomTypeId, checkInDate, checkOutDate, room -> {
                if (room == null) {
                    callback.onSuccess(null);
                    return;
                }

                nextSequence("booking", BOOKING_CODE_SEED, sequence -> {
                    String bookingId = rootRef.child("bookings").push().getKey();
                    String paymentId = rootRef.child("payments").push().getKey();
                    String bookingCode = String.format(Locale.US, "BK-2026-%04d", sequence);
                    String now = currentTimestamp();

                    Map<String, Object> booking = new HashMap<>();
                    booking.put("bookingCode", bookingCode);
                    booking.put("customerId", customerId);
                    booking.put("roomId", room.roomId);
                    booking.put("roomTypeId", roomTypeId);
                    booking.put("roomTypeName", roomTypeName);
                    booking.put("roomNumber", room.roomNumber);
                    booking.put("checkInDate", checkInDate);
                    booking.put("checkOutDate", checkOutDate);
                    booking.put("numGuests", numGuests);
                    booking.put("totalPrice", totalPrice);
                    booking.put("status", "confirmed");
                    booking.put("createdAt", now);

                    Map<String, Object> payment = new HashMap<>();
                    payment.put("bookingId", bookingId);
                    payment.put("customerId", customerId);
                    payment.put("amount", depositAmount);
                    payment.put("paymentMethod", paymentMethod);
                    payment.put("paymentType", "deposit");
                    payment.put("status", "success");
                    payment.put("paidAt", now);
                    payment.put("createdAt", now);

                    Map<String, Object> calendarEntry = new HashMap<>();
                    calendarEntry.put("checkIn", checkInDate);
                    calendarEntry.put("checkOut", checkOutDate);
                    calendarEntry.put("status", "confirmed");

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("/bookings/" + bookingId, booking);
                    updates.put("/bookingsByCustomer/" + customerId + "/" + bookingId, true);
                    updates.put("/roomCalendar/" + room.roomId + "/" + bookingId, calendarEntry);
                    updates.put("/payments/" + paymentId, payment);
                    updates.put("/paymentsByBooking/" + bookingId + "/" + paymentId, true);
                    updates.put("/customers/" + customerId + "/status", "in_stay");
                    if (updateProfile) {
                        updates.put("/customers/" + customerId + "/fullName", fullName);
                        updates.put("/customers/" + customerId + "/email", email);
                        updates.put("/customers/" + customerId + "/phone", phone);
                    }

                    rootRef.updateChildren(updates, (error, ref) -> {
                        if (error != null) {
                            callback.onError(error.getMessage());
                        } else {
                            callback.onSuccess(new BookingCreationResult(bookingId, bookingCode));
                        }
                    });
                }, callback::onError);
            }, callback::onError);
        }, callback::onError);
    }

    // ===================== Room service =====================

    public void getMenuItems(FirebaseCallback<List<MenuItem>> callback) {
        readOnce(rootRef.child("menuItems"), snapshot -> {
            List<MenuItem> items = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                Boolean available = child.child("isAvailable").getValue(Boolean.class);
                if (available == null || !available) {
                    continue;
                }
                items.add(new MenuItem(
                        child.getKey(),
                        child.child("itemName").getValue(String.class),
                        child.child("category").getValue(String.class),
                        valueOrZero(child.child("price")),
                        child.child("description").getValue(String.class),
                        child.child("imageUrl").getValue(String.class)));
            }
            callback.onSuccess(items);
        }, callback::onError);
    }

    public void getActiveRoomServiceOrder(String bookingId, FirebaseCallback<RoomServiceOrder> callback) {
        readOnce(rootRef.child("ordersByBooking").child(bookingId), indexSnapshot -> {
            List<String> orderIds = childKeys(indexSnapshot);
            if (orderIds.isEmpty()) {
                callback.onSuccess(null);
                return;
            }

            List<RoomServiceOrder> candidates = new ArrayList<>();
            AtomicCountdown countdown = new AtomicCountdown(orderIds.size(), () -> {
                RoomServiceOrder latest = null;
                for (RoomServiceOrder order : candidates) {
                    if ("delivered".equals(order.getStatus()) || "cancelled".equals(order.getStatus())) {
                        continue;
                    }
                    if (latest == null || order.getOrderedAt().compareTo(latest.getOrderedAt()) > 0) {
                        latest = order;
                    }
                }
                callback.onSuccess(latest);
            });

            for (String orderId : orderIds) {
                readOnce(rootRef.child("roomServiceOrders").child(orderId), snapshot -> {
                    if (snapshot.exists()) {
                        candidates.add(readRoomServiceOrder(orderId, snapshot));
                    }
                    countdown.tick();
                }, error -> countdown.tick());
            }
        }, callback::onError);
    }

    /** Returns every room service order for a booking, newest first. */
    public void getRoomServiceOrders(String bookingId, FirebaseCallback<List<RoomServiceOrder>> callback) {
        readOnce(rootRef.child("ordersByBooking").child(bookingId), indexSnapshot -> {
            List<String> orderIds = childKeys(indexSnapshot);
            if (orderIds.isEmpty()) {
                callback.onSuccess(new ArrayList<>());
                return;
            }

            List<RoomServiceOrder> orders = new ArrayList<>();
            AtomicCountdown countdown = new AtomicCountdown(orderIds.size(), () -> {
                orders.sort((a, b) -> b.getOrderedAt().compareTo(a.getOrderedAt()));
                callback.onSuccess(orders);
            });

            for (String orderId : orderIds) {
                readOnce(rootRef.child("roomServiceOrders").child(orderId), snapshot -> {
                    if (snapshot.exists()) {
                        orders.add(readRoomServiceOrder(orderId, snapshot));
                    }
                    countdown.tick();
                }, error -> countdown.tick());
            }
        }, callback::onError);
    }

    public void getRoomServiceOrder(String orderId, FirebaseCallback<RoomServiceOrder> callback) {
        readOnce(rootRef.child("roomServiceOrders").child(orderId), snapshot -> {
            callback.onSuccess(snapshot.exists() ? readRoomServiceOrder(orderId, snapshot) : null);
        }, callback::onError);
    }

    /** Advances a single order to a new lifecycle status (preparing → on_the_way → delivered). */
    public void updateRoomServiceOrderStatus(String orderId, String status,
                                             FirebaseCallback<Boolean> callback) {
        rootRef.child("roomServiceOrders").child(orderId).child("status").setValue(status,
                (error, ref) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                    } else {
                        callback.onSuccess(true);
                    }
                });
    }

    public void getRoomServiceOrderLines(String orderId, FirebaseCallback<List<OrderLine>> callback) {
        readOnce(rootRef.child("roomServiceOrders").child(orderId).child("items"), snapshot -> {
            List<OrderLine> lines = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                lines.add(new OrderLine(
                        child.child("itemName").getValue(String.class),
                        child.child("imageUrl").getValue(String.class),
                        valueOrZeroInt(child.child("quantity")),
                        valueOrZero(child.child("unitPrice"))));
            }
            callback.onSuccess(lines);
        }, callback::onError);
    }

    public void createRoomServiceOrder(String bookingId, String customerId, List<RoomServiceCart.Entry> lines,
                                       String kitchenNote, double subtotal,
                                       double serviceCharge, double totalAmount, String paymentMethod,
                                       FirebaseCallback<OrderCreationResult> callback) {
        nextSequence("roomServiceOrder", ORDER_CODE_SEED, sequence -> {
            String orderId = rootRef.child("roomServiceOrders").push().getKey();
            String paymentId = rootRef.child("payments").push().getKey();
            String orderCode = String.format(Locale.US, "RS-2026-%04d", sequence);
            String now = currentTimestamp();

            Map<String, Object> items = new HashMap<>();
            for (RoomServiceCart.Entry entry : lines) {
                Map<String, Object> item = new HashMap<>();
                item.put("itemName", entry.item.getItemName());
                item.put("imageUrl", entry.item.getImageUrl());
                item.put("quantity", entry.quantity);
                item.put("unitPrice", entry.item.getPrice());
                items.put(entry.item.getMenuItemId(), item);
            }

            Map<String, Object> order = new HashMap<>();
            order.put("orderCode", orderCode);
            order.put("bookingId", bookingId);
            order.put("customerId", customerId);
            order.put("items", items);
            order.put("kitchenNote", kitchenNote);
            order.put("subtotal", subtotal);
            order.put("serviceCharge", serviceCharge);
            order.put("totalAmount", totalAmount);
            order.put("paymentMethod", paymentMethod);
            order.put("status", "preparing");
            order.put("orderedAt", now);

            Map<String, Object> payment = new HashMap<>();
            payment.put("bookingId", bookingId);
            payment.put("customerId", customerId);
            payment.put("amount", totalAmount);
            payment.put("paymentMethod", paymentMethod);
            payment.put("paymentType", "service");
            payment.put("status", "success");
            payment.put("paidAt", now);
            payment.put("createdAt", now);

            Map<String, Object> updates = new HashMap<>();
            updates.put("/roomServiceOrders/" + orderId, order);
            updates.put("/ordersByBooking/" + bookingId + "/" + orderId, true);
            updates.put("/payments/" + paymentId, payment);
            updates.put("/paymentsByBooking/" + bookingId + "/" + paymentId, true);

            rootRef.updateChildren(updates, (error, ref) -> {
                if (error != null) {
                    callback.onError(error.getMessage());
                } else {
                    callback.onSuccess(new OrderCreationResult(orderId, orderCode));
                }
            });
        }, callback::onError);
    }

    public void getLatestServicePaymentMethod(String bookingId, FirebaseCallback<String> callback) {
        readOnce(rootRef.child("paymentsByBooking").child(bookingId), indexSnapshot -> {
            List<String> paymentIds = childKeys(indexSnapshot);
            if (paymentIds.isEmpty()) {
                callback.onSuccess("room_bill");
                return;
            }

            List<DataSnapshot> payments = new ArrayList<>();
            AtomicCountdown countdown = new AtomicCountdown(paymentIds.size(), () -> {
                String latestMethod = "room_bill";
                String latestCreatedAt = "";
                for (DataSnapshot payment : payments) {
                    if (!"service".equals(payment.child("paymentType").getValue(String.class))) {
                        continue;
                    }
                    String createdAt = payment.child("createdAt").getValue(String.class);
                    if (createdAt != null && createdAt.compareTo(latestCreatedAt) > 0) {
                        latestCreatedAt = createdAt;
                        latestMethod = payment.child("paymentMethod").getValue(String.class);
                    }
                }
                callback.onSuccess(latestMethod);
            });

            for (String paymentId : paymentIds) {
                readOnce(rootRef.child("payments").child(paymentId), snapshot -> {
                    if (snapshot.exists()) {
                        payments.add(snapshot);
                    }
                    countdown.tick();
                }, error -> countdown.tick());
            }
        }, callback::onError);
    }

    // ===================== Souvenir store =====================

    public void getStoreProducts(FirebaseCallback<List<StoreProduct>> callback) {
        readOnce(rootRef.child("storeProducts"), snapshot -> {
            List<StoreProduct> products = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                Boolean available = child.child("isAvailable").getValue(Boolean.class);
                if (available == null || !available) {
                    continue;
                }
                products.add(new StoreProduct(
                        child.getKey(),
                        child.child("productName").getValue(String.class),
                        child.child("category").getValue(String.class),
                        valueOrZero(child.child("price")),
                        child.child("description").getValue(String.class),
                        child.child("imageUrl").getValue(String.class),
                        valueOrZeroInt(child.child("stockQuantity"))));
            }
            callback.onSuccess(products);
        }, callback::onError);
    }

    public void createStoreOrder(String bookingId, String customerId, List<StoreCart.Entry> lines,
                                 double subtotal, double serviceCharge, double tax, double totalAmount,
                                 String roomNumber, String paymentMethod,
                                 FirebaseCallback<OrderCreationResult> callback) {
        nextSequence("storeOrder", STORE_ORDER_SEED, sequence -> {
            String orderId = rootRef.child("storeOrders").push().getKey();
            String paymentId = rootRef.child("payments").push().getKey();
            String orderCode = String.format(Locale.US, "SB-2026-%05d", sequence);
            String now = currentTimestamp();

            Map<String, Object> items = new HashMap<>();
            for (StoreCart.Entry entry : lines) {
                Map<String, Object> item = new HashMap<>();
                item.put("productName", entry.product.getProductName());
                item.put("imageUrl", entry.product.getImageUrl());
                item.put("quantity", entry.quantity);
                item.put("unitPrice", entry.product.getPrice());
                items.put(entry.product.getProductId(), item);
            }

            Map<String, Object> order = new HashMap<>();
            order.put("orderCode", orderCode);
            order.put("bookingId", bookingId);
            order.put("customerId", customerId);
            order.put("items", items);
            order.put("subtotal", subtotal);
            order.put("serviceCharge", serviceCharge);
            order.put("tax", tax);
            order.put("totalAmount", totalAmount);
            order.put("deliveryType", "room_delivery");
            order.put("roomNumber", roomNumber);
            order.put("paymentMethod", paymentMethod);
            order.put("status", "confirmed");
            order.put("orderedAt", now);

            Map<String, Object> payment = new HashMap<>();
            payment.put("bookingId", bookingId);
            payment.put("customerId", customerId);
            payment.put("amount", totalAmount);
            payment.put("paymentMethod", paymentMethod);
            payment.put("paymentType", "store");
            payment.put("status", "success");
            payment.put("paidAt", now);
            payment.put("createdAt", now);

            Map<String, Object> updates = new HashMap<>();
            updates.put("/storeOrders/" + orderId, order);
            updates.put("/storeOrdersByBooking/" + bookingId + "/" + orderId, true);
            updates.put("/payments/" + paymentId, payment);
            updates.put("/paymentsByBooking/" + bookingId + "/" + paymentId, true);

            rootRef.updateChildren(updates, (error, ref) -> {
                if (error != null) {
                    callback.onError(error.getMessage());
                } else {
                    callback.onSuccess(new OrderCreationResult(orderId, orderCode));
                }
            });
        }, callback::onError);
    }

    // ===================== Restaurant / dining =====================

    public void getDiningTables(FirebaseCallback<List<DiningTable>> callback) {
        readOnce(rootRef.child("diningTables"), snapshot -> {
            List<DiningTable> tables = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                tables.add(new DiningTable(
                        child.getKey(),
                        child.child("code").getValue(String.class),
                        valueOrZeroInt(child.child("capacity")),
                        child.child("shape").getValue(String.class),
                        valueOrZeroInt(child.child("sortOrder"))));
            }
            tables.sort(Comparator.comparingInt(DiningTable::getSortOrder));
            callback.onSuccess(tables);
        }, callback::onError);
    }

    /** Returns the set of table ids already reserved for the given date + time slot. */
    public void getBookedTableIds(String date, String timeSlot, FirebaseCallback<Set<String>> callback) {
        readOnce(rootRef.child("diningReservations"), snapshot -> {
            Set<String> booked = new HashSet<>();
            for (DataSnapshot child : snapshot.getChildren()) {
                String status = child.child("status").getValue(String.class);
                if (!"pending".equals(status) && !"confirmed".equals(status) && !"completed".equals(status)) {
                    continue;
                }
                if (date.equals(child.child("reservationDate").getValue(String.class))
                        && timeSlot.equals(child.child("reservationTime").getValue(String.class))) {
                    String tableId = child.child("tableId").getValue(String.class);
                    if (tableId != null) {
                        booked.add(tableId);
                    }
                }
            }
            callback.onSuccess(booked);
        }, callback::onError);
    }

    public void createDiningReservation(String bookingId, String customerId, String tableId, String tableCode,
                                        int capacity, String date, String timeSlot, String venue,
                                        FirebaseCallback<OrderCreationResult> callback) {
        // Guard against a race: re-check the table is still free for this date + slot.
        getBookedTableIds(date, timeSlot, bookedIds -> {
            if (bookedIds.contains(tableId)) {
                callback.onSuccess(null);
                return;
            }
            nextSequence("diningReservation", DINING_RES_SEED, sequence -> {
                String reservationId = rootRef.child("diningReservations").push().getKey();
                String reservationCode = String.format(Locale.US, "DR-2026-%04d", sequence);
                String now = currentTimestamp();

                Map<String, Object> reservation = new HashMap<>();
                reservation.put("reservationCode", reservationCode);
                reservation.put("bookingId", bookingId);
                reservation.put("customerId", customerId);
                reservation.put("venue", venue);
                reservation.put("venueType", "restaurant");
                reservation.put("tableId", tableId);
                reservation.put("tableCode", tableCode);
                reservation.put("reservationDate", date);
                reservation.put("reservationTime", timeSlot);
                reservation.put("numGuests", capacity);
                reservation.put("status", "confirmed");
                reservation.put("createdAt", now);

                Map<String, Object> serviceIndex = new HashMap<>();
                serviceIndex.put("type", "dining");
                serviceIndex.put("refId", reservationId);

                Map<String, Object> updates = new HashMap<>();
                updates.put("/diningReservations/" + reservationId, reservation);
                updates.put("/servicesByBooking/" + bookingId + "/dining_" + reservationId, serviceIndex);

                rootRef.updateChildren(updates, (error, ref) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                    } else {
                        callback.onSuccess(new OrderCreationResult(reservationId, reservationCode));
                    }
                });
            }, callback::onError);
        });
    }

    // ===================== Spa / wellness =====================

    /**
     * Reads a room type's category ("suite", "deluxe", ...) so the spa flow can tell whether
     * the guest gets free access (Suite) or pays per slot (Deluxe and below).
     */
    public void getRoomCategory(String roomTypeId, FirebaseCallback<String> callback) {
        if (roomTypeId == null) {
            callback.onSuccess("");
            return;
        }
        readOnce(rootRef.child("roomTypes").child(roomTypeId).child("category"), snapshot -> {
            String category = snapshot.getValue(String.class);
            callback.onSuccess(category == null ? "" : category);
        }, callback::onError);
    }

    /**
     * Returns how many spa slots are already taken per session on a given date, hotel-wide.
     * A session's remaining capacity is {@link #SPA_SESSION_CAPACITY} minus its returned count.
     */
    public void getBookedSpaSlots(String date, FirebaseCallback<Map<String, Integer>> callback) {
        readOnce(rootRef.child("spaBookings"), snapshot -> {
            Map<String, Integer> bookedBySession = new HashMap<>();
            for (DataSnapshot booking : snapshot.getChildren()) {
                String status = booking.child("status").getValue(String.class);
                if (!"pending".equals(status) && !"confirmed".equals(status) && !"completed".equals(status)) {
                    continue;
                }
                if (!date.equals(booking.child("reservationDate").getValue(String.class))) {
                    continue;
                }
                int guests = valueOrZeroInt(booking.child("numGuests"));
                for (DataSnapshot session : booking.child("sessions").getChildren()) {
                    String slot = session.getValue(String.class);
                    if (slot != null) {
                        bookedBySession.merge(slot, guests, Integer::sum);
                    }
                }
            }
            callback.onSuccess(bookedBySession);
        }, callback::onError);
    }

    /**
     * Books one or more spa sessions for a booking. Suite guests pay nothing (free); other tiers
     * are charged {@code totalAmount} and a payment record is written. Also indexes the booking
     * under the home "My Services" list.
     */
    public void createSpaBooking(String bookingId, String customerId, String date, List<String> sessions,
                                 int numGuests, int slotsUsed, double pricePerSlot, double totalAmount,
                                 boolean free, String paymentMethod,
                                 FirebaseCallback<OrderCreationResult> callback) {
        nextSequence("spaBooking", SPA_BOOKING_SEED, sequence -> {
            String spaBookingId = rootRef.child("spaBookings").push().getKey();
            String bookingCode = String.format(Locale.US, "SPA-2026-%04d", sequence);
            String now = currentTimestamp();

            Map<String, Object> spaBooking = new HashMap<>();
            spaBooking.put("bookingCode", bookingCode);
            spaBooking.put("bookingId", bookingId);
            spaBooking.put("customerId", customerId);
            spaBooking.put("serviceName", "Spa Session Booking");
            spaBooking.put("reservationDate", date);
            spaBooking.put("sessions", new ArrayList<>(sessions));
            spaBooking.put("numGuests", numGuests);
            spaBooking.put("slotsUsed", slotsUsed);
            spaBooking.put("sessionLengthMinutes", 40);
            spaBooking.put("pricePerSlot", pricePerSlot);
            spaBooking.put("totalAmount", totalAmount);
            spaBooking.put("free", free);
            spaBooking.put("paymentMethod", free ? "complimentary" : paymentMethod);
            spaBooking.put("status", "confirmed");
            spaBooking.put("createdAt", now);

            Map<String, Object> serviceIndex = new HashMap<>();
            serviceIndex.put("type", "spa");
            serviceIndex.put("refId", spaBookingId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("/spaBookings/" + spaBookingId, spaBooking);
            updates.put("/spaBookingsByBooking/" + bookingId + "/" + spaBookingId, true);
            updates.put("/servicesByBooking/" + bookingId + "/spa_" + spaBookingId, serviceIndex);

            if (!free) {
                String paymentId = rootRef.child("payments").push().getKey();
                Map<String, Object> payment = new HashMap<>();
                payment.put("bookingId", bookingId);
                payment.put("customerId", customerId);
                payment.put("amount", totalAmount);
                payment.put("paymentMethod", paymentMethod);
                payment.put("paymentType", "spa");
                payment.put("status", "success");
                payment.put("paidAt", now);
                payment.put("createdAt", now);
                updates.put("/payments/" + paymentId, payment);
                updates.put("/paymentsByBooking/" + bookingId + "/" + paymentId, true);
            }

            rootRef.updateChildren(updates, (error, ref) -> {
                if (error != null) {
                    callback.onError(error.getMessage());
                } else {
                    callback.onSuccess(new OrderCreationResult(spaBookingId, bookingCode));
                }
            });
        }, callback::onError);
    }

    // ===================== Airport transfer =====================

    /**
     * Books an airport transfer (Suite-only, complimentary). {@code transferType} is "pickup"
     * (airport → hotel) or "dropoff" (hotel → airport). Indexed under the home "My Services" list.
     */
    public void createTransferBooking(String bookingId, String customerId, String transferType,
                                      String fromLocation, String toLocation, String date, String time24,
                                      String scheduledDisplay, int numGuests,
                                      FirebaseCallback<OrderCreationResult> callback) {
        nextSequence("transferBooking", TRANSFER_BOOKING_SEED, sequence -> {
            String transferId = rootRef.child("transferBookings").push().getKey();
            String prefix = "dropoff".equals(transferType) ? "DROP" : "PKUP";
            String bookingCode = String.format(Locale.US, "%s-2026-%04d", prefix, sequence);
            String now = currentTimestamp();

            Map<String, Object> transfer = new HashMap<>();
            transfer.put("bookingCode", bookingCode);
            transfer.put("bookingId", bookingId);
            transfer.put("customerId", customerId);
            transfer.put("transferType", transferType);
            transfer.put("fromLocation", fromLocation);
            transfer.put("toLocation", toLocation);
            transfer.put("reservationDate", date);
            transfer.put("reservationTime", time24);
            transfer.put("scheduledDatetime", scheduledDisplay);
            transfer.put("numGuests", numGuests);
            transfer.put("status", "pending");
            transfer.put("createdAt", now);

            Map<String, Object> serviceIndex = new HashMap<>();
            serviceIndex.put("type", "transfer");
            serviceIndex.put("refId", transferId);

            Map<String, Object> updates = new HashMap<>();
            updates.put("/transferBookings/" + transferId, transfer);
            updates.put("/transferBookingsByBooking/" + bookingId + "/" + transferId, true);
            updates.put("/servicesByBooking/" + bookingId + "/transfer_" + transferId, serviceIndex);

            rootRef.updateChildren(updates, (error, ref) -> {
                if (error != null) {
                    callback.onError(error.getMessage());
                } else {
                    callback.onSuccess(new OrderCreationResult(transferId, bookingCode));
                }
            });
        }, callback::onError);
    }

    /** Realtime reference to a MoMo payment's status node, for the app to observe the IPN result. */
    public DatabaseReference getPaymentStatusRef(String orderId) {
        return rootRef.child("payments").child(orderId).child("status");
    }

    // ===================== Internal chaining helpers =====================

    private interface ValueListener<T> {
        void onValue(T value);
    }

    private interface FailureListener {
        void onFailure(String message);
    }

    private static class RoomInfo {
        final String roomId;
        final String roomNumber;

        RoomInfo(String roomId, String roomNumber) {
            this.roomId = roomId;
            this.roomNumber = roomNumber;
        }
    }

    private void resolveAvailableRoom(String roomTypeId, String checkInDate, String checkOutDate,
                                      ValueListener<RoomInfo> onValue, FailureListener onFailure) {
        readOnce(rootRef.child("rooms"), roomsSnapshot ->
                readOnce(rootRef.child("roomCalendar"), calendarSnapshot -> {
                    for (DataSnapshot roomSnapshot : roomsSnapshot.getChildren()) {
                        if (!roomTypeId.equals(roomSnapshot.child("roomTypeId").getValue(String.class))) {
                            continue;
                        }
                        if ("maintenance".equals(roomSnapshot.child("status").getValue(String.class))) {
                            continue;
                        }
                        String roomId = roomSnapshot.getKey();
                        if (isRoomFree(calendarSnapshot.child(roomId), checkInDate, checkOutDate)) {
                            onValue.onValue(new RoomInfo(roomId,
                                    roomSnapshot.child("roomNumber").getValue(String.class)));
                            return;
                        }
                    }
                    onValue.onValue(null);
                }, onFailure), onFailure);
    }

    private boolean isRoomFree(DataSnapshot roomBookingsSnapshot, String checkInDate, String checkOutDate) {
        for (DataSnapshot existingBooking : roomBookingsSnapshot.getChildren()) {
            String status = existingBooking.child("status").getValue(String.class);
            if (!"pending".equals(status) && !"confirmed".equals(status) && !"checked_in".equals(status)) {
                continue;
            }
            String existingCheckIn = existingBooking.child("checkIn").getValue(String.class);
            String existingCheckOut = existingBooking.child("checkOut").getValue(String.class);
            boolean overlaps = existingCheckIn.compareTo(checkOutDate) < 0
                    && existingCheckOut.compareTo(checkInDate) > 0;
            if (overlaps) {
                return false;
            }
        }
        return true;
    }

    private void nextSequence(String counterName, long seed, ValueListener<Long> onValue, FailureListener onFailure) {
        rootRef.child("meta").child("counters").child(counterName)
                .runTransaction(new Transaction.Handler() {
                    @Override
                    public Transaction.Result doTransaction(MutableData currentData) {
                        Long current = currentData.getValue(Long.class);
                        currentData.setValue((current == null ? seed : current) + 1);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
                        if (error != null) {
                            onFailure.onFailure(error.getMessage());
                        } else if (!committed || snapshot == null) {
                            onFailure.onFailure("Could not generate a sequence number");
                        } else {
                            onValue.onValue(snapshot.getValue(Long.class));
                        }
                    }
                });
    }

    private Customer readCustomer(String customerId, DataSnapshot snapshot) {
        Customer customer = new Customer(
                customerId,
                snapshot.child("fullName").getValue(String.class),
                snapshot.child("email").getValue(String.class),
                snapshot.child("phone").getValue(String.class),
                snapshot.child("status").getValue(String.class),
                snapshot.child("createdAt").getValue(String.class));
        customer.setDob(snapshot.child("dob").getValue(String.class));
        customer.setNationality(snapshot.child("nationality").getValue(String.class));
        customer.setLanguage(snapshot.child("language").getValue(String.class));
        customer.setAvatarUrl(snapshot.child("avatarUrl").getValue(String.class));
        customer.setMembershipTier(snapshot.child("membershipTier").getValue(String.class));
        return customer;
    }

    private BookingSummary readBooking(String bookingId, DataSnapshot snapshot) {
        BookingSummary booking = new BookingSummary(
                bookingId,
                snapshot.child("bookingCode").getValue(String.class),
                snapshot.child("roomTypeName").getValue(String.class),
                snapshot.child("checkInDate").getValue(String.class),
                snapshot.child("checkOutDate").getValue(String.class),
                valueOrZeroInt(snapshot.child("numGuests")),
                snapshot.child("status").getValue(String.class));
        booking.setRoomTypeId(snapshot.child("roomTypeId").getValue(String.class));
        booking.setRoomNumber(snapshot.child("roomNumber").getValue(String.class));
        return booking;
    }

    private RoomType readRoomType(DataSnapshot snapshot, int availableRooms) {
        return new RoomType(
                snapshot.getKey(),
                snapshot.child("typeName").getValue(String.class),
                snapshot.child("description").getValue(String.class),
                valueOrZero(snapshot.child("basePrice")),
                valueOrZeroInt(snapshot.child("maxOccupancy")),
                snapshot.child("category").getValue(String.class),
                snapshot.child("viewType").getValue(String.class),
                valueOrZeroInt(snapshot.child("sizeSqft")),
                snapshot.child("bedType").getValue(String.class),
                snapshot.child("amenities").getValue(String.class),
                snapshot.child("imageUrl").getValue(String.class),
                availableRooms);
    }

    private RoomServiceOrder readRoomServiceOrder(String orderId, DataSnapshot snapshot) {
        int itemCount = 0;
        for (DataSnapshot item : snapshot.child("items").getChildren()) {
            itemCount += valueOrZeroInt(item.child("quantity"));
        }
        return new RoomServiceOrder(
                orderId,
                snapshot.child("orderCode").getValue(String.class),
                snapshot.child("bookingId").getValue(String.class),
                valueOrZero(snapshot.child("totalAmount")),
                snapshot.child("status").getValue(String.class),
                snapshot.child("orderedAt").getValue(String.class),
                itemCount);
    }

    private String formatVenueTitle(String venueType) {
        if ("coffee".equals(venueType)) {
            return "Coffee Table";
        }
        if ("bar".equals(venueType)) {
            return "Bar Table";
        }
        return "Restaurant Table";
    }

    private List<String> childKeys(DataSnapshot snapshot) {
        List<String> keys = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            keys.add(child.getKey());
        }
        return keys;
    }

    private double valueOrZero(DataSnapshot snapshot) {
        Double value = snapshot.getValue(Double.class);
        return value == null ? 0 : value;
    }

    private int valueOrZeroInt(DataSnapshot snapshot) {
        Long value = snapshot.getValue(Long.class);
        return value == null ? 0 : value.intValue();
    }

    private String sanitizeEmailKey(String email) {
        return email.trim().toLowerCase(Locale.US).replace(".", ",");
    }

    private String currentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new java.util.Date());
    }

    private void readOnce(DatabaseReference ref, ValueListener<DataSnapshot> onValue, FailureListener onFailure) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                onValue.onValue(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                onFailure.onFailure(error.getMessage());
            }
        });
    }

    /** Fires {@code onDone} exactly once, after {@code count} ticks have been recorded. */
    private static class AtomicCountdown {
        private int remaining;
        private final Runnable onDone;

        AtomicCountdown(int count, Runnable onDone) {
            this.remaining = count;
            this.onDone = onDone;
            if (count <= 0) {
                onDone.run();
            }
        }

        void tick() {
            remaining--;
            if (remaining == 0) {
                onDone.run();
            }
        }
    }
}
