package com.ttnails.booking_service.tse.client;

/**
 * Trạng thái implementation TSE đang active trong context.
 *
 * @param enabled            có bật ký TSE không
 * @param credentialsPresent đủ credential trong cấu hình không
 * @param implementation     {@code NOOP} | {@code FISKALY}
 */
public record TseProviderStatus(boolean enabled, boolean credentialsPresent, String implementation) {
}
