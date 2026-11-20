package com.ttnails.booking_service.tse.client;

import com.ttnails.booking_service.tse.config.TseProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementation mặc định khi {@code fiskaly.enabled=false} (dev/test hoặc chưa có key).
 *
 * <p>Không gọi mạng, không tạo dữ liệu TSE. Checkout vẫn chạy và trả
 * {@link TseTransactionStatus#SKIPPED}; receipt không có QR TSE.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "fiskaly", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopTseClient implements TseClient {

    private final TseProperties properties;

    @PostConstruct
    void logState() {
        log.info("TSE disabled (fiskaly.enabled=false) - checkout chạy không ký TSE. "
                + "Xem docs/tse/fiskaly-setup.md khi có credential.");
    }

    @Override
    public void start(UUID txId, TseReceipt receipt) {
        log.debug("TSE skip start txId={} receiptType={}", txId,
                receipt == null ? null : receipt.receiptType());
    }

    @Override
    public TseSignature finish(UUID txId) {
        return TseSignature.skipped(txId);
    }

    @Override
    public TseProviderStatus status() {
        return new TseProviderStatus(false, properties.hasCredentials(), "NOOP");
    }
}
