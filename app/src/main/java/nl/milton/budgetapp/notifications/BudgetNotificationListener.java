package nl.milton.budgetapp.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import nl.milton.budgetapp.MainActivity;
import nl.milton.budgetapp.data.AppDatabase;
import nl.milton.budgetapp.data.BudgetDao;
import nl.milton.budgetapp.data.MerchantRuleEntity;
import nl.milton.budgetapp.data.TransactionEntity;
import nl.milton.budgetapp.data.UnknownItemEntity;
import nl.milton.budgetapp.domain.Money;
import nl.milton.budgetapp.domain.Normalizer;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BudgetNotificationListener extends NotificationListenerService {
    private static final Pattern AMOUNT = Pattern.compile("(?i)(?:€|EUR)\\s*([+-]?\\d{1,3}(?:\\.\\d{3})*,\\d{2}|[+-]?\\d+,\\d{2})");
    private static final Pattern CARD = Pattern.compile("(?i)(?:pas|card)\\s*[:#-]?\\s*([A-Z0-9*]{3,})");
    private static final Pattern REF = Pattern.compile("(?i)(?:kenmerk|referentie|reference)\\s*[:#-]?\\s*([A-Z0-9-]{3,})");
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName();
        String lower = pkg.toLowerCase(Locale.ROOT);
        if (!lower.contains("rabo") && !"com.google.android.apps.walletnfcrel".equals(pkg)) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        String title = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE, ""));
        String text = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT, ""));
        String big = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT, ""));
        String combined = (title + " " + text + " " + big).trim();

        Matcher amountMatcher = AMOUNT.matcher(combined);
        if (!amountMatcher.find()) return;

        long cents;
        try {
            cents = Math.abs(Money.parseCents(amountMatcher.group(1)));
        } catch (Exception e) {
            return;
        }

        String normalized = Normalizer.key(combined);
        boolean income = normalized.contains("ontvangen") || normalized.contains("bijgeschreven") || normalized.contains("gestort") || amountMatcher.group(1).trim().startsWith("+");
        long signedAmount = income ? cents : -cents;

        long postTime = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
        String dateText = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date(postTime));
        String timeText = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(postTime));
        String merchant = extractMerchant(title, text, big);

        TransactionEntity tx = new TransactionEntity();
        tx.source = "NOTIFICATION";
        tx.importedAt = System.currentTimeMillis();
        tx.occurredAt = postTime;
        tx.amountCents = signedAmount;
        tx.merchant = merchant;
        tx.description = combined;
        tx.dateText = dateText;
        tx.timeText = timeText;
        tx.cardReference = extract(CARD, combined);
        tx.bankReference = extract(REF, combined);
        tx.dedupeKey = hash("NOTIFICATION|" + sbn.getKey());
        tx.affectsBalance = true;

        io.execute(() -> {
            BudgetDao dao = AppDatabase.get(getApplicationContext()).budgetDao();
            MerchantRuleEntity rule = dao.findRule("MERCHANT", Normalizer.key(merchant));
            if (rule != null) {
                tx.potId = rule.potId;
                tx.category = rule.category;
            }
            long id = dao.insertTransaction(tx);
            if (id > 0 && rule == null && signedAmount < 0) {
                UnknownItemEntity unknown = new UnknownItemEntity();
                unknown.receiptLineId = 0L;
                unknown.normalizedText = Normalizer.key(merchant);
                unknown.displayText = merchant.isEmpty() ? "Onbekende betaling" : merchant;
                unknown.amountCents = Math.abs(signedAmount);
                unknown.createdAt = System.currentTimeMillis();
                dao.insertUnknownItem(unknown);
                showLearningNotification(unknown.displayText);
            }
        });
    }

    private String extractMerchant(String title, String text, String big) {
        String candidate = title == null ? "" : title.trim();
        if (!candidate.isEmpty() && !candidate.toLowerCase(Locale.ROOT).contains("rabo") && !candidate.toLowerCase(Locale.ROOT).contains("wallet")) return candidate;
        String source = (big == null || big.trim().isEmpty()) ? text : big;
        if (source == null) return "";
        Matcher at = Pattern.compile("(?i)\\b(?:bij|at)\\s+([^,.\\n]+)").matcher(source);
        if (at.find()) return at.group(1).trim();
        return source.length() > 80 ? source.substring(0, 80).trim() : source.trim();
    }

    private static String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
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

    private void showLearningNotification(String item) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        String channelId = "budget_learning";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channelId, "Budget categorie kiezen", NotificationManager.IMPORTANCE_DEFAULT));

        Intent intent = new Intent(this, MainActivity.class).putExtra("open_learning", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("BudgetApp: categorie kiezen").setContentText(item).setAutoCancel(true).setContentIntent(pending);
        try {
            manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
        } catch (SecurityException ignored) {}
    }

    @Override
    public void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
