package com.ttnails.booking_service.tse.client;

import com.ttnails.booking_service.tse.config.TseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Chỗ đặt implementation fiskaly SIGN DE (S3-T10) — <b>chưa triển khai</b>.
 *
 * <p>Class này cố tình fail-fast khi {@code fiskaly.enabled=true} để tránh trường hợp
 * production tưởng đã ký TSE nhưng thực tế không. Khi có credential sandbox + xác nhận
 * payload từ fiskaly, thay constructor bằng {@code RestClient} và implement
 * {@link #start}/{@link #finish} theo docs/tse/tse-design.md.</p>
 *
 * <p><b>Không bật</b> {@code FISKALY_ENABLED=true} trên bất kỳ môi trường nào trước khi
 * hoàn thành S3-T10.</p>
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "fiskaly", name = "enabled", havingValue = "true")
public class FiskalyTseClient implements TseClient {

    public FiskalyTseClient(TseProperties properties) {
        if (!properties.hasCredentials()) {
            throw new IllegalStateException(
                    "fiskaly.enabled=true nhưng thiếu FISKALY_API_KEY/API_SECRET/TSS_ID/CLIENT_ID. "
                            + "Xem docs/tse/fiskaly-setup.md.");
        }
        log.error("fiskaly.enabled=true nhưng FiskalyTseClient chưa được triển khai (S3-T10). "
                + "Đổi fiskaly.enabled=false hoặc hoàn thành docs/tse/tse-design.md §12.");
        throw new IllegalStateException(
                "FiskalyTseClient chưa được triển khai. Không bật TSE trước khi hoàn thành S3-T10 "
                        + "(docs/tse/tse-design.md).");
    }

    @Override
    public void start(UUID txId, TseReceipt receipt) {
        throw new UnsupportedOperationException("S3-T10 chua trien khai");
    }

    @Override
    public TseSignature finish(UUID txId) {
        throw new UnsupportedOperationException("S3-T10 chua trien khai");
    }

    @Override
    public TseProviderStatus status() {
        return new TseProviderStatus(true, true, "FISKALY");
    }
}
