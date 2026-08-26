package nl.milton.budgetapp.importers;

import android.content.Context;
import android.content.SharedPreferences;

import nl.milton.budgetapp.data.AppDatabase;
import nl.milton.budgetapp.data.BudgetDao;
import nl.milton.budgetapp.data.TransactionEntity;
import nl.milton.budgetapp.domain.Normalizer;

import java.util.Calendar;
import java.util.List;

public final class ImportCoordinator {
    private ImportCoordinator() {}

    public static ImportSummary importStatement(Context context, BankStatementParser.ParsedStatement statement) {
        BudgetDao dao = AppDatabase.get(context).budgetDao();
        ImportSummary summary = new ImportSummary();

        for (TransactionEntity tx : statement.transactions) {
            TransactionEntity existing = dao.findByDedupeKey(tx.dedupeKey);
            if (existing != null) {
                summary.skipped++;
                continue;
            }

            TransactionEntity strictNotification = null;
            if (!tx.timeText.isEmpty() && !tx.merchant.isEmpty() && !tx.cardReference.isEmpty() && !tx.bankReference.isEmpty()) {
                strictNotification = dao.findStrictNotificationMatch(tx.amountCents, tx.dateText, tx.timeText, tx.merchant, tx.cardReference, tx.bankReference);
            }

            if (strictNotification != null) {
                strictNotification.merchant = tx.merchant;
                strictNotification.description = tx.description;
                strictNotification.category = tx.category;
                strictNotification.excludeFromPots = tx.excludeFromPots;
                strictNotification.dedupeKey = tx.dedupeKey;
                dao.updateTransaction(strictNotification);
                summary.matchedNotifications++;
                continue;
            }

            long newId = dao.insertTransaction(tx);
            if (newId <= 0) {
                summary.skipped++;
                continue;
            }
            tx.id = newId;
            summary.added++;

            if (tx.amountCents < 0) {
                long[] day = dayBounds(tx.occurredAt);
                List<TransactionEntity> candidates = dao.findReceiptCandidates(tx.amountCents, day[0], day[1], tx.occurredAt);
                for (TransactionEntity receipt : candidates) {
                    if (merchantCompatible(receipt.merchant, tx.merchant)) {
                        dao.moveReceiptLines(receipt.id, newId);
                        receipt.affectsBalance = false;
                        receipt.matchedBankTransactionId = newId;
                        dao.updateTransaction(receipt);
                        summary.matchedReceipts++;
                        break;
                    }
                }
            }
        }

        summary.balanceChecked = statement.openingBalanceCents != null && statement.closingBalanceCents != null;
        summary.balanceValid = statement.balanceValid;

        if (summary.balanceChecked && summary.balanceValid) {
            SharedPreferences prefs = context.getSharedPreferences("budgetapp", Context.MODE_PRIVATE);
            prefs.edit().putLong("known_balance_cents", statement.closingBalanceCents).putBoolean("has_known_balance", true).apply();
        }
        return summary;
    }

    private static boolean merchantCompatible(String a, String b) {
        String na = Normalizer.key(a);
        String nb = Normalizer.key(b);
        if (na.isEmpty() || nb.isEmpty()) return true;
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    private static long[] dayBounds(long timeMs) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.DAY_OF_MONTH, 1);
        return new long[]{start, c.getTimeInMillis() - 1};
    }

    public static final class ImportSummary {
        public int added;
        public int skipped;
        public int matchedNotifications;
        public int matchedReceipts;
        public boolean balanceChecked;
        public boolean balanceValid;
    }
}
