package com.ttnails.booking_service.tse.client;

import java.util.UUID;

/**
 * Cổng ký giao dịch với TSE (D17 — fiskaly SIGN DE, dự phòng hardware TSE).
 *
 * <p>Hợp đồng bắt buộc:</p>
 * <ul>
 *   <li>{@code txId} sinh một lần cho mỗi checkout và <b>không đổi</b> khi retry.</li>
 *   <li>{@link #start} phải idempotent theo {@code txId} (gọi lặp không tạo giao dịch mới).</li>
 *   <li>{@link #finish} trả {@link TseSignature} thay vì ném lỗi mạng; lỗi tạm thời là
 *       {@link TseTransactionStatus#PENDING} để job đối soát retry sau.</li>
 *   <li>Không được ném exception làm rollback payment/invoice — TSE không chặn thu tiền.</li>
 * </ul>
 *
 * <p>Hiện chỉ có {@link NoopTseClient} vì chưa đăng ký fiskaly key (2026-11-20).
 * {@code FiskalyTseClient} sẽ được triển khai ở S3-T10; xem docs/tse/tse-design.md.</p>
 */
public interface TseClient {

    /** Bắt đầu giao dịch và gửi receipt schema. Idempotent theo {@code txId}. */
    void start(UUID txId, TseReceipt receipt);

    /** Kết thúc giao dịch và lấy signature + QR. Idempotent theo {@code txId}. */
    TseSignature finish(UUID txId);

    /** Trạng thái provider đang dùng — phục vụ {@code GET /tse/status}. */
    TseProviderStatus status();
}
