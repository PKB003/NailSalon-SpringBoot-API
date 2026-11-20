package com.ttnails.booking_service.tse.client;

/**
 * Nhóm phương thức thanh toán theo KassenSichV.
 *
 * <p>TSE chỉ phân biệt tiền mặt và phi tiền mặt; chi tiết CARD/BANK_TRANSFER vẫn lưu
 * ở module payment để báo cáo/Z-report. Mapping:</p>
 * <ul>
 *   <li>{@code CASH} → {@code CASH}</li>
 *   <li>{@code CARD}, {@code BANK_TRANSFER} → {@code NON_CASH}</li>
 * </ul>
 */
public enum TsePaymentType {
    CASH,
    NON_CASH
}
