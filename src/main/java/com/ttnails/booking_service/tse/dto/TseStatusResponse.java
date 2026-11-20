package com.ttnails.booking_service.tse.dto;

/**
 * Response của {@code GET /tse/status} — chỉ trả thông tin vận hành, không bao giờ
 * trả API key/secret. TSS/client id được mask.
 */
public record TseStatusResponse(boolean enabled,
                                String provider,
                                String environment,
                                String implementation,
                                boolean credentialsPresent,
                                String tssId,
                                String clientId,
                                int timeoutMs) {
}
