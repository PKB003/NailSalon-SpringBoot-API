package com.ttnails.booking_service.bookings.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Mã xác nhận cho guest (S2-08).
 *
 * <p>Định dạng {@code TT-XXXXXX} với alphabet bỏ các ký tự dễ nhầm (I, O, 0, 1)
 * để khách đọc qua điện thoại không sai.</p>
 */
@Component
public class ConfirmationCodeService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder builder = new StringBuilder("TT-");
        for (int i = 0; i < CODE_LENGTH; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    /**
     * Chuẩn hoá số điện thoại Đức để so khớp: bỏ ký tự không phải số,
     * đổi +49/0049 → 0 đầu.
     */
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0049")) {
            digits = "0" + digits.substring(4);
        } else if (digits.startsWith("49") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }

    /** So khớp SĐT linh hoạt (khoảng trắng, +49 vs 0, thiếu số 0 đầu). */
    public static boolean phoneMatches(String left, String right) {
        String a = normalizePhone(left);
        String b = normalizePhone(right);
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        int tail = Math.min(8, Math.min(a.length(), b.length()));
        if (tail < 6) return false;
        return a.substring(a.length() - tail).equals(b.substring(b.length() - tail));
    }
}
