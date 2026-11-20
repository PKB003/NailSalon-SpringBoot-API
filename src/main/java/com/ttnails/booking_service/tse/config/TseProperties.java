package com.ttnails.booking_service.tse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình TSE/fiskaly SIGN DE (D17, S3-09/S3-T10).
 *
 * <p>Chưa có credential thật (2026-11-20). Mặc định {@code enabled=false} để mọi luồng
 * checkout vẫn chạy bình thường, chỉ bỏ bước ký. Khi đăng ký sandbox xong, điền
 * {@code FISKALY_*} qua secret manager — xem {@code docs/tse/fiskaly-setup.md}.</p>
 *
 * <pre>
 * fiskaly:
 *   enabled: false                  # true chỉ khi FiskalyTseClient đã được triển khai
 *   provider: fiskaly
 *   environment: SANDBOX            # SANDBOX | PRODUCTION
 *   base-url: https://kassensichv.io/api/v2
 *   api-key: ${FISKALY_API_KEY:}
 *   api-secret: ${FISKALY_API_SECRET:}
 *   tss-id: ${FISKALY_TSS_ID:}
 *   client-id: ${FISKALY_CLIENT_ID:}
 *   timeout-ms: 3000
 *   retry:
 *     max-attempts: 3
 *     initial-backoff-ms: 500
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "fiskaly")
@Data
public class TseProperties {

    /** Bật ký TSE. Giữ false cho tới khi S3-T10 hoàn tất và có credential sandbox. */
    private boolean enabled = false;

    /** Provider TSE. Hiện chỉ hỗ trợ fiskaly; giữ field để đổi provider sau này. */
    private String provider = "fiskaly";

    /** SANDBOX | PRODUCTION — dùng để log/status, không tự đổi base-url. */
    private String environment = "SANDBOX";

    private String baseUrl = "https://kassensichv.io/api/v2";

    private String apiKey = "";

    private String apiSecret = "";

    /** TSS (Technical Security System) id do fiskaly cấp. */
    private String tssId = "";

    /** Client id đại diện cho quầy/POS. */
    private String clientId = "";

    /** Timeout HTTP cho mỗi lần gọi fiskaly (ms). */
    private int timeoutMs = 3000;

    private Retry retry = new Retry();

    @Data
    public static class Retry {
        /** Số lần thử tối đa cho một tx_id (bao gồm lần đầu). */
        private int maxAttempts = 3;
        /** Backoff khởi điểm (ms), nhân đôi sau mỗi lần lỗi. */
        private long initialBackoffMs = 500;
    }

    /** Đủ credential để gọi fiskaly hay chưa (không phụ thuộc {@code enabled}). */
    public boolean hasCredentials() {
        return notBlank(baseUrl) && notBlank(apiKey) && notBlank(apiSecret)
                && notBlank(tssId) && notBlank(clientId);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Che bớt id/secret khi trả về API hoặc log.
     * {@code tss_1234567890abcdef} → {@code tss_***ef}.
     */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return "***";
        }
        return trimmed.substring(0, 4) + "***" + trimmed.substring(trimmed.length() - 2);
    }
}
