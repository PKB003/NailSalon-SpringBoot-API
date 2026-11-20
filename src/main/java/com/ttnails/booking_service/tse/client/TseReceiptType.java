package com.ttnails.booking_service.tse.client;

/**
 * Loại chứng từ gửi TSE. Mapping sang {@code receipt_type} của fiskaly được chốt
 * khi triển khai S3-T10 (xem docs/tse/tse-design.md §7).
 */
public enum TseReceiptType {
    /** Hoá đơn/receipt bán hàng. */
    RECEIPT,
    /** Storno — tham chiếu hoá đơn gốc. */
    CANCELLATION
}
