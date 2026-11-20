package com.ttnails.booking_service.tse.client;

/**
 * Trạng thái ký TSE của một giao dịch.
 *
 * <p>Ánh xạ sang cột {@code tse_transaction.status} (xem docs/tse/tse-design.md §5):</p>
 * <ul>
 *   <li>{@link #SIGNED} — đã có signature + QR hợp lệ.</li>
 *   <li>{@link #PENDING} — timeout/mất mạng; retry cùng {@code tx_id}.</li>
 *   <li>{@link #FAILED} — lỗi không thể retry tự động (cần can thiệp).</li>
 *   <li>{@link #SKIPPED} — {@code fiskaly.enabled=false} (dev/test).</li>
 * </ul>
 */
public enum TseTransactionStatus {
    SIGNED,
    PENDING,
    FAILED,
    SKIPPED
}
