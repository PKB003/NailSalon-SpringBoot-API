package com.ttnails.booking_service.tse.client;

import java.time.Instant;
import java.util.UUID;

/**
 * Kết quả ký một giao dịch TSE.
 *
 * <p>{@code txId} là idempotency key phía fiskaly: sinh đúng một lần cho mỗi lần
 * checkout và <b>không đổi</b> khi retry. Các field còn lại null khi chưa ký xong.</p>
 *
 * @param txId               UUID ổn định của giao dịch
 * @param status             trạng thái ký
 * @param txNumber           transaction number do TSE cấp
 * @param serialNumber       serial của TSS/client
 * @param signatureAlgorithm ví dụ {@code ecdsa-plain-SHA256}
 * @param signatureValue     chữ ký base64
 * @param signatureCounter   counter của TSE
 * @param qrPayload          nội dung QR in lên receipt
 * @param signedAt           thời điểm ký xong
 * @param errorCode          mã lỗi nội bộ khi PENDING/FAILED
 * @param errorMessage       mô tả lỗi (không chứa secret/PII)
 */
public record TseSignature(UUID txId,
                           TseTransactionStatus status,
                           String txNumber,
                           String serialNumber,
                           String signatureAlgorithm,
                           String signatureValue,
                           Long signatureCounter,
                           String qrPayload,
                           Instant signedAt,
                           String errorCode,
                           String errorMessage) {

    /** TSE đang tắt — checkout vẫn thành công, receipt không có QR TSE. */
    public static TseSignature skipped(UUID txId) {
        return new TseSignature(txId, TseTransactionStatus.SKIPPED, null, null, null, null,
                null, null, null, null, null);
    }

    /** Gọi TSE thất bại tạm thời — phải retry với cùng {@code txId}. */
    public static TseSignature pending(UUID txId, String errorCode, String errorMessage) {
        return new TseSignature(txId, TseTransactionStatus.PENDING, null, null, null, null,
                null, null, null, errorCode, errorMessage);
    }

    public boolean signed() {
        return status == TseTransactionStatus.SIGNED;
    }
}
