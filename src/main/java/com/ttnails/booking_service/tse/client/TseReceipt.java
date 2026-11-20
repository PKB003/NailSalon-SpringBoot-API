package com.ttnails.booking_service.tse.client;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Dữ liệu tối thiểu của một giao dịch gửi TSE (chưa phải payload fiskaly cuối cùng).
 *
 * <p>Implement fiskaly (S3-T10) chịu trách nhiệm map record này sang JSON
 * {@code standard_v1} của fiskaly. Không đưa PII (tên/SĐT khách) vào payload TSE
 * nếu không cần thiết — receipt_json lưu lâu dài theo GoBD.</p>
 *
 * @param receiptType            loại chứng từ
 * @param currency               ISO 4217, hiện tại luôn "EUR"
 * @param amountsPerVatRate      key = VAT rate phần trăm (0 cho §19, 19 cho standard)
 * @param amountsPerPaymentType  tổng gross theo nhóm thanh toán
 */
public record TseReceipt(TseReceiptType receiptType,
                         String currency,
                         Map<Integer, TseVatAmount> amountsPerVatRate,
                         Map<TsePaymentType, BigDecimal> amountsPerPaymentType) {
}
