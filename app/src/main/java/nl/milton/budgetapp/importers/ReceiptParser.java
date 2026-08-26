package nl.milton.budgetapp.importers;

import nl.milton.budgetapp.domain.Money;
import nl.milton.budgetapp.domain.Normalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReceiptParser {
    private static final Pattern LINE_AMOUNT = Pattern.compile("^(.*?)(\\d+[.,]\\d{2})\\s*$");
    private static final Pattern TOTAL = Pattern.compile("(?i)\\b(totaal|total|te betalen|pin)\\b");

    private ReceiptParser() {}

    public static ParsedReceipt parse(String rawText) {
        ParsedReceipt receipt = new ParsedReceipt();
        if (rawText == null) return receipt;

        String[] lines = rawText.replace('\u00A0', ' ').split("\\r?\\n");
        for (String source : lines) {
            String line = source.trim().replaceAll("\\s+", " ");
            if (line.isEmpty()) continue;

            if (receipt.merchant.isEmpty() && !line.matches(".*\\d+[.,]\\d{2}.*")) receipt.merchant = line;

            Matcher matcher = LINE_AMOUNT.matcher(line);
            if (!matcher.matches()) continue;
            String description = matcher.group(1).trim();
            long amount = Math.abs(Money.parseCents(matcher.group(2)));
            if (TOTAL.matcher(description).find()) {
                receipt.totalCents = amount;
                continue;
            }
            if (amount <= 0 || description.isEmpty()) continue;

            ParsedLine parsedLine = new ParsedLine();
            parsedLine.description = description;
            parsedLine.normalizedText = Normalizer.key(description);
            parsedLine.amountCents = amount;
            receipt.lines.add(parsedLine);
        }

        if (receipt.totalCents == 0L) {
            for (ParsedLine line : receipt.lines) receipt.totalCents += line.amountCents;
        }
        return receipt;
    }

    public static final class ParsedReceipt {
        public String merchant = "";
        public long totalCents;
        public final List<ParsedLine> lines = new ArrayList<>();
    }

    public static final class ParsedLine {
        public String description = "";
        public String normalizedText = "";
        public long amountCents;
    }
}
