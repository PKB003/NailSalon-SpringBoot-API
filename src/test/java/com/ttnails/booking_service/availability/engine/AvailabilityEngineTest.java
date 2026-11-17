package com.ttnails.booking_service.availability.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S1-03 — unit test thuần cho {@link AvailabilityEngine} (không Spring, không DB).
 *
 * <p>Bao phủ danh sách test bắt buộc ở Plan.md §7.2 (Availability) và các biên
 * quan trọng: buffer, ca gãy, thời gian nghỉ, skill, timezone, cutoff, grid slot.</p>
 */
class AvailabilityEngineTest {

    /** 20/11/2026 là thứ 6 — ngày làm việc bình thường. */
    private static final LocalDate DATE = LocalDate.of(2026, 11, 20);
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final LocalTime OPEN = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(18, 0);

    private final AvailabilityEngine engine = new AvailabilityEngine();

    // ==================== Helpers ====================

    private static ServiceSpec service(long id, int duration, int buffer) {
        return new ServiceSpec(id, "Service " + id, duration, buffer, BigDecimal.TEN);
    }

    private static Interval at(String from, String to) {
        return new Interval(DATE.atTime(LocalTime.parse(from)), DATE.atTime(LocalTime.parse(to)));
    }

    private static LocalDateTime dt(String time) {
        return DATE.atTime(LocalTime.parse(time));
    }

    private static StaffSchedule staff(long id, Set<Long> skills, List<Interval> shifts) {
        return new StaffSchedule(id, "Staff " + id, skills, shifts, List.of(), List.of());
    }

    private static StaffSchedule staff(long id, List<Interval> shifts) {
        return staff(id, Set.of(1L), shifts);
    }

    private AvailabilityQuery query(List<ServiceSpec> services, List<StaffSchedule> staff) {
        return new AvailabilityQuery(DATE, ZONE, DATE.atTime(8, 0), OPEN, CLOSE, 30, 30,
                services, staff, null);
    }

    private AvailabilityQuery query(List<ServiceSpec> services, List<StaffSchedule> staff, Long staffId) {
        return new AvailabilityQuery(DATE, ZONE, DATE.atTime(8, 0), OPEN, CLOSE, 30, 30,
                services, staff, staffId);
    }

    private AvailabilityQuery queryWithWindow(List<ServiceSpec> services, List<StaffSchedule> staff,
                                              LocalTime open, LocalTime close, int step, int lead,
                                              LocalDateTime now) {
        return new AvailabilityQuery(DATE, ZONE, now, open, close, step, lead, services, staff, null);
    }

    private static List<LocalDateTime> startsOf(AvailabilityResult result, long staffId) {
        return AvailabilityEngine.startsOf(result, staffId);
    }

    private static List<LocalDateTime> expected(List<String> times) {
        return times.stream().map(t -> DATE.atTime(LocalTime.parse(t))).toList();
    }

    // ==================== Độ dài dịch vụ & giờ đóng cửa ====================

    @Test
    @DisplayName("Dịch vụ 90' + buffer 10' không có slot nếu vượt giờ đóng cửa")
    void serviceDoesNotFitBeforeClosing() {
        // Ca 16:30–18:00 chỉ còn 90' → thiếu 10' buffer
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 90, 10)),
                List.of(staff(1, List.of(at("16:30", "18:00"))))));

        assertThat(result.anyAvailable()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Slot kết thúc đúng giờ đóng cửa vẫn hợp lệ")
    void slotEndingExactlyAtClosingIsAllowed() {
        // Ca 16:00–18:00, dịch vụ 90' + buffer 10' = 100' → 16:00 + 100' = 17:40 (slot cuối cùng).
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 90, 10)),
                List.of(staff(1, List.of(at("16:00", "18:00"))))));

        assertThat(startsOf(result, 1)).containsExactly(dt("16:00"));
    }

    @Test
    @DisplayName("Slot bị cắt bởi giờ mở cửa của branch dù ca bắt đầu sớm hơn")
    void shiftIsClippedToOpeningHours() {
        AvailabilityResult result = engine.compute(queryWithWindow(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("07:00", "10:00")))),
                OPEN, CLOSE, 30, 0, DATE.atTime(6, 0)));

        assertThat(startsOf(result, 1)).containsExactly(dt("09:00"), dt("09:30"));
    }

    @Test
    @DisplayName("Dịch vụ dài hơn cả ngày làm việc → không có slot")
    void serviceLongerThanDayHasNoSlots() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 600, 0)),
                List.of(staff(1, List.of(at("09:00", "18:00"))))));

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Không có ca làm (ngày nghỉ / ngày lễ) → không trả slot")
    void closedDayHasNoSlots() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of()))));

        assertThat(result.staff()).isEmpty();
        assertThat(result.anyAvailable()).isEmpty();
    }

    @Test
    @DisplayName("Thợ không có ca nhưng thợ khác có ca → chỉ trả slot của thợ có ca")
    void onlyStaffWithShiftIsReturned() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of()), staff(2, List.of(at("09:00", "10:00"))))));

        assertThat(result.staff()).extracting(StaffSlots::staffId).containsExactly(2L);
    }

    // ==================== Nghỉ phép / skill ====================

    @Test
    @DisplayName("Thợ nghỉ phép cả ngày → không có slot")
    void fullDayTimeOffBlocksAllSlots() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "18:00")),
                List.of(at("00:00", "23:59")),
                List.of());

        assertThat(engine.compute(query(List.of(service(1, 30, 0)), List.of(s))).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Nghỉ phép một phần ngày chỉ chặn các slot giao với khoảng nghỉ")
    void partialTimeOffBlocksOnlyOverlappingSlots() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "12:00")),
                List.of(at("10:00", "11:00")),
                List.of());

        assertThat(startsOf(engine.compute(query(List.of(service(1, 30, 0)), List.of(s))), 1))
                .containsExactly(dt("09:00"), dt("09:30"), dt("11:00"), dt("11:30"));
    }

    @Test
    @DisplayName("Thợ thiếu skill của bất kỳ dịch vụ nào trong query → không trả slot")
    void staffWithoutSkillIsSkipped() {
        StaffSchedule s = staff(1, Set.of(2L), List.of(at("09:00", "18:00")));

        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0), service(2, 30, 0)), List.of(s)));

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Thợ có đủ skill của mọi dịch vụ → có slot")
    void staffWithAllSkillsIsEligible() {
        StaffSchedule s = staff(1, Set.of(1L, 2L), List.of(at("09:00", "11:00")));

        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0), service(2, 30, 0)), List.of(s)));

        assertThat(result.anyAvailable()).isNotEmpty();
    }

    @Test
    @DisplayName("Query theo staffId chỉ trả đúng thợ đó")
    void requestedStaffFilter() {
        List<StaffSchedule> staff = List.of(
                staff(1, List.of(at("09:00", "11:00"))),
                staff(2, List.of(at("09:00", "11:00"))));

        AvailabilityResult result = engine.compute(query(List.of(service(1, 30, 0)), staff, 2L));

        assertThat(result.staff()).extracting(StaffSlots::staffId).containsExactly(2L);
    }

    @Test
    @DisplayName("Query staffId không tồn tại → rỗng (không lỗi)")
    void requestedStaffWithoutShiftReturnsEmpty() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "11:00")))), 99L));

        assertThat(result.isEmpty()).isTrue();
    }

    // ==================== Booking đang chặn ====================

    @Test
    @DisplayName("Booking 10:00–11:30 chặn 10:30 và 11:00, không chặn 11:30 (buffer 0)")
    void existingBookingBlocksOverlappingSlots() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "18:00")), List.of(),
                List.of(at("10:00", "11:30")));

        List<LocalDateTime> starts = startsOf(
                engine.compute(query(List.of(service(1, 30, 0)), List.of(s))), 1);

        assertThat(starts).contains(dt("09:00"), dt("09:30"), dt("11:30"), dt("17:30"));
        assertThat(starts).doesNotContain(dt("10:00"), dt("10:30"), dt("11:00"));
    }

    @Test
    @DisplayName("Buffer 10' của booking cũ chặn slot sát giờ (11:30 bị chặn khi busy tới 11:40)")
    void existingBufferBlocksTightSlot() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "18:00")), List.of(),
                List.of(at("10:00", "11:40")));

        List<LocalDateTime> starts = startsOf(
                engine.compute(query(List.of(service(1, 30, 0)), List.of(s))), 1);

        assertThat(starts).doesNotContain(dt("11:30"));
        assertThat(starts).contains(dt("12:00"));
    }

    @Test
    @DisplayName("Buffer của dịch vụ trong booking mới cũng bị tính khi kiểm tra chồng lấn")
    void newServiceSpanIncludesBuffer() {
        // Booking cũ 10:30–11:00. Dịch vụ 30' + buffer 20' = 50' → slot 10:00 (10:00–10:50) OK,
        // slot 09:30 (09:30–10:20) chồng lên 10:30? Không. Slot 10:00 kết thúc 10:50 > 10:30 → bị chặn.
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "18:00")), List.of(),
                List.of(at("10:30", "11:00")));

        List<LocalDateTime> starts = startsOf(
                engine.compute(query(List.of(service(1, 30, 20)), List.of(s))), 1);

        assertThat(starts).doesNotContain(dt("10:00"));
        assertThat(starts).contains(dt("09:00"), dt("11:00"));
    }

    @Test
    @DisplayName("Hai booking liên tiếp không chặn slot nằm đúng sau buffer")
    void adjacentBookingsDoNotBlockNextSlot() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "18:00")), List.of(),
                List.of(at("10:00", "10:30")));

        List<LocalDateTime> starts = startsOf(
                engine.compute(query(List.of(service(1, 30, 0)), List.of(s))), 1);

        assertThat(starts).contains(dt("10:30"));
    }

    // ==================== Multi-service ====================

    @Test
    @DisplayName("Multi-service 45' + 60' cần đủ 105' + 2 buffer, end không gồm buffer")
    void multiServiceRequiresFullSpan() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 45, 10), service(2, 60, 10)),
                List.of(staff(1, Set.of(1L, 2L), List.of(at("09:00", "11:30"))))));

        // span = 45+10+60+10 = 125' → 09:00 + 125 = 11:05 (<= 11:30) OK; 09:30 + 125 = 11:35 > 11:30 loại
        assertThat(startsOf(result, 1)).containsExactly(dt("09:00"));
        assertThat(result.totalDurationMinutes()).isEqualTo(105);
    }

    @Test
    @DisplayName("Multi-service: slot trả end = start + Σduration (không gồm buffer cuối)")
    void multiServiceEndExcludesBuffer() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 45, 10), service(2, 60, 10)),
                List.of(staff(1, Set.of(1L, 2L), List.of(at("09:00", "18:00"))))));

        SlotOption first = result.staff().get(0).slots().get(0);
        assertThat(first.start()).isEqualTo(dt("09:00"));
        assertThat(first.end()).isEqualTo(dt("10:45"));
        assertThat(result.totalDurationMinutes()).isEqualTo(105);
    }

    @Test
    @DisplayName("Multi-service: 3 dịch vụ được chấp nhận")
    void threeServicesAreSupported() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 5), service(2, 30, 5), service(3, 30, 5)),
                List.of(staff(1, Set.of(1L, 2L, 3L), List.of(at("09:00", "18:00"))))));

        assertThat(result.anyAvailable()).isNotEmpty();
        assertThat(result.totalDurationMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("Multi-service: thợ chỉ có skill 1 dịch vụ không đủ điều kiện")
    void multiServiceRequiresAllSkills() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0), service(2, 30, 0)),
                List.of(staff(1, Set.of(1L), List.of(at("09:00", "18:00"))))));

        assertThat(result.isEmpty()).isTrue();
    }

    // ==================== Any available & sắp xếp ====================

    @Test
    @DisplayName("anyAvailable là union giờ bắt đầu, đã sắp xếp và không trùng")
    void anyAvailableIsSortedUnion() {
        List<StaffSchedule> staff = List.of(
                staff(2, List.of(at("09:00", "10:00"))),
                staff(1, List.of(at("09:30", "10:30"))));

        AvailabilityResult result = engine.compute(query(List.of(service(1, 30, 0)), staff));

        assertThat(result.anyAvailable())
                .containsExactly(dt("09:00"), dt("09:30"), dt("10:00"));
        assertThat(result.staff()).extracting(StaffSlots::staffId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("anyAvailable rỗng khi mọi thợ đều bận kín")
    void anyAvailableEmptyWhenAllBusy() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                List.of(at("09:00", "10:00")), List.of(), List.of(at("09:00", "10:00")));

        assertThat(engine.compute(query(List.of(service(1, 30, 0)), List.of(s))).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Danh sách thợ được sắp xếp theo staffId")
    void staffAreSortedById() {
        List<StaffSchedule> staff = List.of(
                staff(9, List.of(at("09:00", "10:00"))),
                staff(3, List.of(at("09:00", "10:00"))),
                staff(7, List.of(at("09:00", "10:00"))));

        AvailabilityResult result = engine.compute(query(List.of(service(1, 30, 0)), staff));

        assertThat(result.staff()).extracting(StaffSlots::staffId).containsExactly(3L, 7L, 9L);
    }

    @Test
    @DisplayName("Không có thợ nào → kết quả rỗng")
    void noStaffReturnsEmptyResult() {
        AvailabilityResult result = engine.compute(query(List.of(service(1, 30, 0)), List.of()));

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.date()).isEqualTo(DATE);
        assertThat(result.zoneId()).isEqualTo(ZONE);
    }

    // ==================== Cutoff / slot quá khứ ====================

    @Test
    @DisplayName("Slot quá khứ bị loại theo now + minLead")
    void pastSlotsAreFiltered() {
        AvailabilityResult result = engine.compute(queryWithWindow(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "18:00")))),
                OPEN, CLOSE, 30, 30, DATE.atTime(10, 0)));

        assertThat(startsOf(result, 1).get(0)).isEqualTo(dt("10:30"));
        assertThat(startsOf(result, 1)).doesNotContain(dt("09:00"), dt("09:30"), dt("10:00"));
    }

    @Test
    @DisplayName("minLead = 0 cho phép slot đúng thời điểm hiện tại")
    void zeroLeadAllowsCurrentSlot() {
        AvailabilityResult result = engine.compute(queryWithWindow(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "11:00")))),
                OPEN, CLOSE, 30, 0, DATE.atTime(9, 0)));

        assertThat(startsOf(result, 1)).contains(dt("09:00"));
    }

    @Test
    @DisplayName("Cutoff lớn hơn giờ đóng cửa → không còn slot")
    void largeLeadRemovesAllSlots() {
        AvailabilityResult result = engine.compute(queryWithWindow(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "18:00")))),
                OPEN, CLOSE, 30, 12 * 60, DATE.atTime(8, 0)));

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Timezone branch khác server: cutoff tính theo now của branch")
    void timezoneOfBranchIsUsedForCutoff() {
        // now ở Berlin 09:00 = 08:00 UTC; server UTC "bây giờ" 07:30 vẫn phải dùng now truyền vào
        AvailabilityQuery q = new AvailabilityQuery(DATE, ZONE, DATE.atTime(9, 0), OPEN, CLOSE, 30, 15,
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "12:00")))), null);

        assertThat(startsOf(engine.compute(q), 1).get(0)).isEqualTo(dt("09:30"));
        assertThat(engine.compute(q).zoneId().getId()).isEqualTo("Europe/Berlin");
    }

    // ==================== Grid / bước slot ====================

    @Test
    @DisplayName("Slot được căn lên lưới giờ mở cửa, không theo phút ca bắt đầu")
    void slotsAlignToOpeningGrid() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:10", "11:00"))))));

        assertThat(startsOf(result, 1)).containsExactly(dt("09:30"), dt("10:00"), dt("10:30"));
    }

    @Test
    @DisplayName("Bước slot 15' sinh nhiều slot hơn bước 30'")
    void slotStepIsConfigurable() {
        AvailabilityQuery q = new AvailabilityQuery(DATE, ZONE, DATE.atTime(8, 0), OPEN, CLOSE, 15, 0,
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "10:00")))), null);

        assertThat(startsOf(engine.compute(q), 1))
                .containsExactly(dt("09:00"), dt("09:15"), dt("09:30"));
    }

    @Test
    @DisplayName("Ca gãy (2 ca/ngày) → union slot của cả 2 ca")
    void splitShiftProducesTwoBlocks() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "10:00"), at("14:00", "15:00"))))));

        assertThat(startsOf(result, 1)).containsExactly(
                dt("09:00"), dt("09:30"), dt("14:00"), dt("14:30"));
    }

    @Test
    @DisplayName("Ca 0 phút hoặc ngược giờ bị bỏ qua (không crash)")
    void emptyShiftIsIgnored() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("10:00", "10:00"))))));

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Slot không vượt quá cuối ca (end + buffer phải nằm trong ca)")
    void slotMustFitInsideShiftEnd() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 10)),
                List.of(staff(1, List.of(at("09:00", "10:30"))))));

        // span 40' → 09:00 (09:40) OK, 09:30 (10:10) OK, 10:00 (10:40) > 10:30 loại
        assertThat(startsOf(result, 1)).containsExactly(dt("09:00"), dt("09:30"));
    }

    // ==================== Contract / validation ====================

    @Test
    @DisplayName("Kết quả giữ nguyên date, timezone, step và tổng thời lượng")
    void resultMetadataIsEchoed() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 45, 10), service(2, 60, 10)),
                List.of(staff(1, Set.of(1L, 2L), List.of(at("09:00", "18:00"))))));

        assertThat(result.date()).isEqualTo(DATE);
        assertThat(result.zoneId()).isEqualTo(ZONE);
        assertThat(result.slotStepMinutes()).isEqualTo(30);
        assertThat(result.totalDurationMinutes()).isEqualTo(105);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Helper startsOf trả rỗng cho staff không có mặt trong kết quả")
    void startsOfUnknownStaffIsEmpty() {
        AvailabilityResult result = engine.compute(query(
                List.of(service(1, 30, 0)),
                List.of(staff(1, List.of(at("09:00", "10:00"))))));

        assertThat(startsOf(result, 42L)).isEmpty();
    }

    @Test
    @DisplayName("ServiceSpec từ chối duration <= 0 và buffer < 0")
    void serviceSpecValidation() {
        assertThatThrownBy(() -> service(1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(1, 30, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AvailabilityQuery từ chối thiếu date/timezone/service")
    void queryValidation() {
        assertThatThrownBy(() -> new AvailabilityQuery(null, ZONE, DATE.atTime(8, 0), OPEN, CLOSE, 30, 0,
                List.of(service(1, 30, 0)), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AvailabilityQuery(DATE, null, DATE.atTime(8, 0), OPEN, CLOSE, 30, 0,
                List.of(service(1, 30, 0)), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AvailabilityQuery(DATE, ZONE, DATE.atTime(8, 0), OPEN, CLOSE, 30, 0,
                List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Interval.overlaps dùng khoảng nửa mở [start, end)")
    void intervalOverlapSemantics() {
        Interval interval = at("10:00", "11:00");
        assertThat(interval.overlaps(dt("09:30"), dt("10:00"))).isFalse();
        assertThat(interval.overlaps(dt("11:00"), dt("11:30"))).isFalse();
        assertThat(interval.overlaps(dt("10:30"), dt("11:30"))).isTrue();
        assertThat(interval.minutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("StaffSchedule tính tổng phút bận và kiểm tra skill")
    void staffScheduleHelpers() {
        StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L, 2L),
                List.of(at("09:00", "18:00")), List.of(), List.of(at("10:00", "11:30")));

        assertThat(s.bookedMinutes()).isEqualTo(90);
        assertThat(s.hasSkillsFor(List.of(service(1, 30, 0)))).isTrue();
        assertThat(s.hasSkillsFor(List.of(service(1, 30, 0), service(9, 30, 0)))).isFalse();
    }

    @Nested
    @DisplayName("Biên thời gian")
    class TimeBoundaries {

        @Test
        @DisplayName("Slot bắt đầu đúng lúc ca bắt đầu vẫn hợp lệ")
        void slotAtShiftStart() {
            AvailabilityResult result = engine.compute(query(
                    List.of(service(1, 30, 0)),
                    List.of(staff(1, List.of(at("09:00", "11:00"))))));

            assertThat(startsOf(result, 1)).contains(dt("09:00"));
        }

        @Test
        @DisplayName("Slot bắt đầu trước ca bị loại")
        void slotBeforeShiftIsRejected() {
            AvailabilityResult result = engine.compute(query(
                    List.of(service(1, 30, 0)),
                    List.of(staff(1, List.of(at("10:00", "12:00"))))));

            assertThat(startsOf(result, 1)).doesNotContain(dt("09:00"), dt("09:30"));
        }

        @Test
        @DisplayName("Nghỉ phép bắt đầu đúng lúc slot kết thúc không chặn slot")
        void timeOffStartingAtSlotEndDoesNotBlock() {
            StaffSchedule s = new StaffSchedule(1L, "Staff 1", Set.of(1L),
                    List.of(at("09:00", "12:00")),
                    List.of(at("10:00", "11:00")),
                    List.of());

            assertThat(startsOf(engine.compute(query(List.of(service(1, 30, 0)), List.of(s))), 1))
                    .contains(dt("09:30"));
        }
    }
}
