package nl.milton.budgetapp.importers;

import nl.milton.budgetapp.data.TransactionEntity;
import nl.milton.budgetapp.domain.Money;
import nl.milton.budgetapp.domain.Normalizer;

import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BankStatementParser {
    private static final Pattern DATE = Pattern.compile("^(\\d{1,2}[-/.]\\d{1,2}(?:[-/.]\\d{2,4})?)\\s*(\\d{1,2}:\\d{2})?\\s+(.+)$");
    private static final Pattern MONEY = Pattern.compile("([+-]?\\s*(?:€\\s*)?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|[+-]?\\s*(?:€\\s*)?\\d+,\\d{2})(?:\\s*([+-]))?");
    private static final Pattern CARD = Pattern.compile("(?i)(?:pas|card)\\s*[:#-]?\\s*([A-Z0-9*]{3,})");
    private static final Pattern REFERENCE = Pattern.compile("(?i)(?:kenmerk|referentie|reference)\\s*[:#-]?\\s*([A-Z0-9-]{3,})");

    private BankStatementParser() {}

    public static ParsedStatement parse(String rawText) {
        ParsedStatement out = new ParsedStatement();
        if (rawText == null) return out;

        String[] lines = rawText.replace('\u00A0', ' ').split("\\r?\\n");
        for (String sourceLine : lines) {
            String line = sourceLine.trim().replaceAll("\\s+", " ");
            if (line.isEmpty()) continue;

            String key = Normalizer.key(line);
            if (key.contains("beginsaldo") || key.contains("begin saldo") || key.contains("vorig saldo")) {
                Long amount = findLastBalance(line);
                if (amount != null) out.openingBalanceCents = amount;
                continue;
            }
            if (key.contains("eindsaldo") || key.contains("eind saldo") || key.contains("nieuw saldo")) {
                Long amount = findLastBalance(line);
                if (amount != null) out.closingBalanceCents = amount;
                continue;
            }

            Matcher dm = DATE.matcher(line);
            if (!dm.matches()) continue;

            String dateText = normalizeDate(dm.group(1));
            String timeText = dm.group(2) == null ? "" : dm.group(2);
            String remainder = dm.group(3);

            Matcher mm = MONEY.matcher(remainder);
            int amountStart = -1;
            int amountEnd = -1;
            long amountCents = 0L;
            while (mm.find()) {
                amountStart = mm.start();
                amountEnd = mm.end();
                amountCents = parseSignedAmount(mm.group(1), mm.group(2));
            }
            if (amountStart < 0) continue;

            String description = remainder.substring(0, amountStart).trim();
            String merchant = cleanMerchant(description);
            String descriptionKey = Normalizer.key(description);
            boolean explicitSign = remainder.substring(amountStart, amountEnd).contains("+")
                    || remainder.substring(amountStart, amountEnd).contains("-");
            if (!explicitSign && amountCents < 0 && (
                    descriptionKey.contains("bijschrijving")
                            || descriptionKey.contains("ontvangen")
                            || descriptionKey.contains("salaris")
                            || descriptionKey.contains("terugbetaling")
                            || descriptionKey.contains("credit"))) {
                amountCents = Math.abs(amountCents);
            }
            String cardRef = extract(CARD, line);
            String bankRef = extract(REFERENCE, line);

            TransactionEntity tx = new TransactionEntity();
            tx.source = "PDF";
            tx.importedAt = System.currentTimeMillis();
            tx.occurredAt = parseDateTime(dateText, timeText);
            tx.amountCents = amountCents;
            tx.merchant = merchant;
            tx.description = description;
            tx.dateText = dateText;
            tx.timeText = timeText;
            tx.cardReference = cardRef;
            tx.bankReference = bankRef;
            tx.excludeFromPots = isAnnualLevy(description);
            if (tx.excludeFromPots) tx.category = "Jaarlijkse heffing";
            tx.dedupeKey = hash("PDF|" + dateText + "|" + timeText + "|" + amountCents + "|" + Normalizer.key(merchant) + "|" + Normalizer.key(cardRef) + "|" + Normalizer.key(bankRef));
            out.transactions.add(tx);
        }

        long sum = 0L;
        for (TransactionEntity tx : out.transactions) sum += tx.amountCents;
        out.statementDeltaCents = sum;
        if (out.openingBalanceCents != null && out.closingBalanceCents != null) {
            out.expectedClosingBalanceCents = out.openingBalanceCents + sum;
            out.balanceValid = out.expectedClosingBalanceCents.longValue() == out.closingBalanceCents.longValue();
        }
        return out;
    }

    private static boolean isAnnualLevy(String text) {
        String k = Normalizer.key(text);
        return k.contains("waterheffing") || k.contains("waterschap") || k.contains("gemeentelijke belasting") || k.contains("gemeentebelasting") || k.contains("jaarlijkse heffing");
    }

    private static String cleanMerchant(String description) {
        String value = description.replaceAll("(?i)^(betaalautomaat|incasso|ideal|overschrijving|betaling)\\s+", "").trim();
        if (value.length() > 80) value = value.substring(0, 80).trim();
        return value;
    }

    private static String extract(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static Long findLastBalance(String text) {
        Matcher m = MONEY.matcher(text);
        Long result = null;
        while (m.find()) {
            String raw = m.group(1).trim();
            long cents = Math.abs(Money.parseCents(raw.replace("+", "").replace("-", "")));
            boolean negative = raw.startsWith("-") || "-".equals(m.group(2));
            result = negative ? -cents : cents;
        }
        return result;
    }

    private static long parseSignedAmount(String value, String trailingSign) {
        String s = value.trim();
        boolean negative = s.startsWith("-");
        boolean positive = s.startsWith("+");
        s = s.replace("+", "").replace("-", "");
        long cents = Math.abs(Money.parseCents(s));
        if ("-".equals(trailingSign)) negative = true;
        if ("+".equals(trailingSign)) positive = true;
        if (negative) return -cents;
        if (positive) return cents;
        return -cents;
    }

    private static String normalizeDate(String raw) {
        String date = raw.replace('/', '-').replace('.', '-');
        String[] p = date.split("-");
        int day = Integer.parseInt(p[0]);
        int month = Integer.parseInt(p[1]);
        int year;
        if (p.length >= 3) {
            year = Integer.parseInt(p[2]);
            if (year < 100) year += 2000;
        } else {
            year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        }
        return String.format(Locale.ROOT, "%02d-%02d-%04d", day, month, year);
    }

    private static long parseDateTime(String date, String time) {
        String text = date + " " + (time == null || time.isEmpty() ? "12:00" : time);
        try {
            Date parsed = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ROOT).parse(text);
            return parsed == null ? System.currentTimeMillis() : parsed.getTime();
        } catch (ParseException e) {
            return System.currentTimeMillis();
        }
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static final class ParsedStatement {
        public final List<TransactionEntity> transactions = new ArrayList<>();
        public Long openingBalanceCents;
        public Long closingBalanceCents;
        public Long expectedClosingBalanceCents;
        public long statementDeltaCents;
        public boolean balanceValid;
    }
}
