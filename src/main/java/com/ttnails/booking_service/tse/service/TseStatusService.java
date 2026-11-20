package com.ttnails.booking_service.tse.service;

import com.ttnails.booking_service.tse.client.TseClient;
import com.ttnails.booking_service.tse.client.TseProviderStatus;
import com.ttnails.booking_service.tse.config.TseProperties;
import com.ttnails.booking_service.tse.dto.TseStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Tổng hợp trạng thái TSE cho admin/manager. Không gọi mạng fiskaly — chỉ đọc
 * cấu hình + implementation đang active, nên an toàn để gọi thường xuyên.
 */
@Service
@RequiredArgsConstructor
public class TseStatusService {

    private final TseProperties properties;
    private final TseClient client;

    public TseStatusResponse getStatus() {
        TseProviderStatus provider = client.status();
        return new TseStatusResponse(
                provider.enabled(),
                properties.getProvider(),
                properties.getEnvironment(),
                provider.implementation(),
                provider.credentialsPresent(),
                TseProperties.mask(properties.getTssId()),
                TseProperties.mask(properties.getClientId()),
                properties.getTimeoutMs());
    }
}
