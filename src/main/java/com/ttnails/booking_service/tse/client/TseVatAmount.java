package com.ttnails.booking_service.tse.client;

import java.math.BigDecimal;

/**
 * Tổng tiền theo một mức VAT trong receipt gửi TSE.
 *
 * @param gross tổng gross (đã gồm VAT)
 * @param net   net
 * @param vat   VAT (0 với §19 UStG)
 */
public record TseVatAmount(BigDecimal gross, BigDecimal net, BigDecimal vat) {
}
