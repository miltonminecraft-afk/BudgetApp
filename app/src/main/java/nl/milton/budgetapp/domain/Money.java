package nl.milton.budgetapp.domain;

import java.text.NumberFormat;
import java.util.Locale;

public final class Money {
    private static final NumberFormat EURO = NumberFormat.getCurrencyInstance(new Locale("nl", "NL"));
    private Money() {}

    public static String format(long cents) {
        return EURO.format(cents / 100.0d);
    }

    public static long parseCents(String text) {
        if (text == null) return 0L;
        String cleaned = text.trim().replace("€", "").replace("EUR", "").replace(" ", "").replace(".", "").replace(",", ".");
        if (cleaned.isEmpty() || "-".equals(cleaned)) return 0L;
        return Math.round(Double.parseDouble(cleaned) * 100.0d);
    }
}
