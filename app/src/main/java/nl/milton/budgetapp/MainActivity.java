package nl.milton.budgetapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nl.milton.budgetapp.data.AppDatabase;
import nl.milton.budgetapp.data.BudgetDao;
import nl.milton.budgetapp.data.FixedCostEntity;
import nl.milton.budgetapp.data.MerchantRuleEntity;
import nl.milton.budgetapp.data.PotEntity;
import nl.milton.budgetapp.data.ReceiptLineEntity;
import nl.milton.budgetapp.data.SavingsGoalEntity;
import nl.milton.budgetapp.data.TransactionEntity;
import nl.milton.budgetapp.data.UnknownItemEntity;
import nl.milton.budgetapp.domain.BudgetPeriods;
import nl.milton.budgetapp.domain.Normalizer;
import nl.milton.budgetapp.importers.BankStatementParser;
import nl.milton.budgetapp.importers.ImportCoordinator;
import nl.milton.budgetapp.importers.PdfStatementImporter;
import nl.milton.budgetapp.importers.ReceiptParser;

public class MainActivity extends ComponentActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private BudgetDao dao;
    private WebView webView;
    private boolean openLearning;

    private final ActivityResultLauncher<Intent> pdfLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
                importPdf(result.getData().getData());
            });

    private final ActivityResultLauncher<Intent> receiptLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String text = result.getData().getStringExtra("receipt_text");
                    if (text != null && !text.trim().isEmpty()) processReceipt(text);
                    else callJsError("onReceiptError", "Er is geen tekst op de bon herkend.");
                } else if (result.getData() != null) {
                    String error = result.getData().getStringExtra("error");
                    if (error != null && !error.isEmpty()) callJsError("onReceiptError", error);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dao = AppDatabase.get(this).budgetDao();
        openLearning = getIntent().getBooleanExtra("open_learning", false);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 991);
        }
        io.execute(() -> {
            seedDefaults();
            runOnUiThread(this::buildWebApp);
        });
    }

    private void buildWebApp() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setBackgroundColor(0xFF0F1116);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "BudgetAppAndroid");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (openLearning) {
                    openLearning = false;
                    view.evaluateJavascript("window.BudgetAppNative&&window.BudgetAppNative.openReview()", null);
                }
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("open_learning", false)) {
            if (webView == null) openLearning = true;
            else webView.evaluateJavascript("window.BudgetAppNative&&window.BudgetAppNative.openReview()", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.evaluateJavascript("window.BudgetAppNative&&window.BudgetAppNative.refresh()", null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null) webView.evaluateJavascript("window.BudgetAppNative&&window.BudgetAppNative.handleBack()", null);
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        io.shutdown();
        super.onDestroy();
    }

    private void seedDefaults() {
        if (dao.potCount() == 0) {
            PotEntity free = new PotEntity();
            free.name = "Vrije uitgaven";
            free.budgetCents = 8_000L;
            free.periodType = BudgetPeriods.WEEK;
            free.sortOrder = 0;
            dao.insertPot(free);

            PotEntity groceries = new PotEntity();
            groceries.name = "Boodschappen";
            groceries.budgetCents = 18_000L;
            groceries.periodType = BudgetPeriods.SALARY_PERIOD;
            groceries.sortOrder = 1;
            dao.insertPot(groceries);

            PotEntity fuel = new PotEntity();
            fuel.name = "Brandstof";
            fuel.budgetCents = 6_000L;
            fuel.periodType = BudgetPeriods.SALARY_PERIOD;
            fuel.sortOrder = 2;
            dao.insertPot(fuel);
        }
        if (dao.getActiveSavingsGoal() == null) {
            SavingsGoalEntity goal = new SavingsGoalEntity();
            goal.name = "Spaardoel";
            goal.targetCents = 3_000_000L;
            goal.currentCents = 0L;
            dao.insertSavingsGoal(goal);
        }
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public String getState() {
            try {
                SharedPreferences prefs = getSharedPreferences("budgetapp", MODE_PRIVATE);
                int salaryStartDay = Math.max(1, Math.min(31, prefs.getInt("salary_start_day", 23)));
                int weekStartDay = Math.max(0, Math.min(6, prefs.getInt("week_start_day", 1)));

                JSONObject root = new JSONObject();
                JSONArray pots = new JSONArray();
                long currentTime = System.currentTimeMillis();
                for (PotEntity pot : dao.getAllPots()) {
                    JSONObject o = new JSONObject();
                    o.put("id", pot.id);
                    o.put("name", pot.name);
                    o.put("budgetCents", pot.budgetCents);
                    o.put("periodType", pot.periodType);
                    o.put("active", pot.active);
                    o.put("hiddenFromOverview", pot.hiddenFromOverview);
                    o.put("sortOrder", pot.sortOrder);
                    BudgetPeriods.Range r = BudgetPeriods.currentRange(pot.periodType, currentTime, salaryStartDay, weekStartDay);
                    long spent = dao.sumDirectSpendForPot(pot.id, r.startMs, r.endMs) + dao.sumReceiptSpendForPot(pot.id, r.startMs, r.endMs);
                    o.put("spentCents", spent);
                    pots.put(o);
                }
                root.put("pots", pots);

                JSONArray transactions = new JSONArray();
                for (TransactionEntity tx : dao.getRecentTransactions(1000)) transactions.put(transactionJson(tx));
                root.put("transactions", transactions);

                JSONArray costs = new JSONArray();
                for (FixedCostEntity cost : dao.getFixedCosts()) {
                    JSONObject o = new JSONObject();
                    o.put("id", cost.id);
                    o.put("name", cost.name);
                    o.put("amountCents", cost.amountCents);
                    o.put("periodType", cost.periodType);
                    o.put("dueDay", cost.dueDay);
                    o.put("active", cost.active);
                    o.put("annualLevy", cost.annualLevy);
                    costs.put(o);
                }
                root.put("fixedCosts", costs);

                SavingsGoalEntity goal = dao.getActiveSavingsGoal();
                if (goal != null) {
                    JSONObject o = new JSONObject();
                    o.put("id", goal.id);
                    o.put("name", goal.name);
                    o.put("currentCents", goal.currentCents);
                    o.put("targetCents", goal.targetCents);
                    o.put("active", goal.active);
                    root.put("goal", o);
                } else root.put("goal", JSONObject.NULL);

                JSONArray unknown = new JSONArray();
                for (UnknownItemEntity item : dao.getUnknownItems()) {
                    JSONObject o = new JSONObject();
                    o.put("id", item.id);
                    o.put("receiptLineId", item.receiptLineId);
                    o.put("normalizedText", item.normalizedText);
                    o.put("displayText", item.displayText);
                    o.put("amountCents", item.amountCents);
                    o.put("createdAt", item.createdAt);
                    unknown.put(o);
                }
                root.put("unknown", unknown);

                JSONObject settings = new JSONObject();
                settings.put("salaryStartDay", salaryStartDay);
                settings.put("weekStartDay", weekStartDay);
                root.put("settings", settings);

                if (prefs.getBoolean("has_known_balance", false)) root.put("knownBalanceCents", prefs.getLong("known_balance_cents", 0L));
                else root.put("knownBalanceCents", JSONObject.NULL);
                root.put("platform", "android");
                return root.toString();
            } catch (Exception e) {
                return "{\"pots\":[],\"transactions\":[],\"fixedCosts\":[],\"unknown\":[],\"goal\":null,\"knownBalanceCents\":null,\"settings\":{\"salaryStartDay\":23,\"weekStartDay\":1},\"platform\":\"android\"}";
            }
        }

        @JavascriptInterface
        public void savePot(String json) {
            try {
                JSONObject o = new JSONObject(json);
                long id = o.optLong("id", 0L);
                PotEntity pot = id > 0 ? findPot(id) : new PotEntity();
                if (pot == null) pot = new PotEntity();
                pot.name = o.optString("name", "").trim();
                pot.budgetCents = Math.max(0L, o.optLong("budgetCents", 0L));
                pot.periodType = o.optString("periodType", BudgetPeriods.MONTH);
                pot.active = o.optBoolean("active", true);
                pot.hiddenFromOverview = o.optBoolean("hiddenFromOverview", false);
                pot.sortOrder = o.optInt("sortOrder", 0);
                if (id > 0 && pot.id > 0) dao.updatePot(pot); else dao.insertPot(pot);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void deletePot(long id) {
            PotEntity pot = findPot(id);
            if (pot == null) return;
            dao.clearTransactionPot(id);
            dao.clearReceiptLinePot(id);
            dao.clearRulePot(id);
            dao.deletePot(pot);
        }

        @JavascriptInterface
        public void saveTransaction(String json) {
            try {
                JSONObject o = new JSONObject(json);
                long id = o.optLong("id", 0L);
                TransactionEntity tx = id > 0 ? dao.getTransaction(id) : new TransactionEntity();
                if (tx == null) tx = new TransactionEntity();
                long time = o.optLong("occurredAt", System.currentTimeMillis());
                tx.source = o.optString("source", id > 0 ? tx.source : "MANUAL");
                tx.importedAt = id > 0 && tx.importedAt > 0 ? tx.importedAt : System.currentTimeMillis();
                tx.occurredAt = time;
                tx.amountCents = o.optLong("amountCents", 0L);
                tx.merchant = o.optString("merchant", "");
                tx.description = o.optString("description", "");
                tx.category = o.optString("category", "");
                tx.potId = nullableLong(o, "potId");
                tx.cardReference = o.optString("cardReference", tx.cardReference == null ? "" : tx.cardReference);
                tx.bankReference = o.optString("bankReference", tx.bankReference == null ? "" : tx.bankReference);
                tx.dateText = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date(time));
                tx.timeText = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(time));
                tx.affectsBalance = o.optBoolean("affectsBalance", true);
                tx.excludeFromPots = o.optBoolean("excludeFromPots", false);
                if (id <= 0 || tx.dedupeKey == null || tx.dedupeKey.isEmpty()) tx.dedupeKey = hash("MANUAL|" + time + "|" + tx.amountCents + "|" + System.nanoTime());
                if (id > 0 && tx.id > 0) dao.updateTransaction(tx); else dao.insertTransaction(tx);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void deleteTransaction(long id) {
            TransactionEntity tx = dao.getTransaction(id);
            if (tx == null) return;
            dao.deleteUnknownForTransaction(id);
            dao.deleteReceiptLinesForTransaction(id);
            dao.deleteTransaction(tx);
        }

        @JavascriptInterface
        public void saveFixedCost(String json) {
            try {
                JSONObject o = new JSONObject(json);
                long id = o.optLong("id", 0L);
                FixedCostEntity cost = id > 0 ? findFixed(id) : new FixedCostEntity();
                if (cost == null) cost = new FixedCostEntity();
                cost.name = o.optString("name", "").trim();
                cost.amountCents = Math.max(0L, o.optLong("amountCents", 0L));
                cost.periodType = o.optString("periodType", BudgetPeriods.MONTH);
                cost.dueDay = Math.max(1, Math.min(31, o.optInt("dueDay", 1)));
                cost.active = o.optBoolean("active", true);
                cost.annualLevy = o.optBoolean("annualLevy", false);
                if (id > 0 && cost.id > 0) dao.updateFixedCost(cost); else dao.insertFixedCost(cost);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void deleteFixedCost(long id) {
            FixedCostEntity cost = findFixed(id);
            if (cost != null) dao.deleteFixedCost(cost);
        }

        @JavascriptInterface
        public void saveGoal(String json) {
            try {
                JSONObject o = new JSONObject(json);
                SavingsGoalEntity goal = dao.getActiveSavingsGoal();
                if (goal == null) goal = new SavingsGoalEntity();
                goal.name = o.optString("name", "Spaardoel");
                goal.currentCents = Math.max(0L, o.optLong("currentCents", 0L));
                goal.targetCents = Math.max(1L, o.optLong("targetCents", 3_000_000L));
                goal.active = true;
                if (goal.id > 0) dao.updateSavingsGoal(goal); else dao.insertSavingsGoal(goal);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void saveSettings(String json) {
            try {
                JSONObject o = new JSONObject(json);
                int salaryStartDay = Math.max(1, Math.min(31, o.optInt("salaryStartDay", 23)));
                int weekStartDay = Math.max(0, Math.min(6, o.optInt("weekStartDay", 1)));
                getSharedPreferences("budgetapp", MODE_PRIVATE)
                        .edit()
                        .putInt("salary_start_day", salaryStartDay)
                        .putInt("week_start_day", weekStartDay)
                        .apply();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void assignUnknown(long id, String category, long potId) {
            UnknownItemEntity item = dao.getUnknownItem(id);
            if (item == null) return;
            Long selectedPot = potId > 0 ? potId : null;
            if (item.receiptLineId > 0) {
                ReceiptLineEntity line = dao.getReceiptLine(item.receiptLineId);
                if (line != null) {
                    line.category = category;
                    line.potId = selectedPot;
                    dao.updateReceiptLine(line);
                    saveRule("ITEM", item.normalizedText, category, selectedPot);
                }
            } else {
                long signed = -Math.abs(item.amountCents);
                TransactionEntity tx = dao.findNotificationForUnknown(signed, item.createdAt - 10 * 60_000L, item.createdAt + 10 * 60_000L, item.createdAt);
                if (tx != null) {
                    tx.category = category;
                    tx.potId = selectedPot;
                    dao.updateTransaction(tx);
                }
                saveRule("MERCHANT", item.normalizedText, category, selectedPot);
            }
            dao.deleteUnknownItem(item);
        }

        @JavascriptInterface
        public String getReceiptLines(long transactionId) {
            try {
                JSONArray a = new JSONArray();
                for (ReceiptLineEntity line : dao.getReceiptLines(transactionId)) {
                    JSONObject o = new JSONObject();
                    o.put("id", line.id);
                    o.put("transactionId", line.transactionId);
                    o.put("description", line.description);
                    o.put("amountCents", line.amountCents);
                    if (line.potId == null) o.put("potId", JSONObject.NULL); else o.put("potId", line.potId);
                    o.put("category", line.category);
                    a.put(o);
                }
                return a.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveReceiptLine(String json) {
            try {
                JSONObject o = new JSONObject(json);
                ReceiptLineEntity line = dao.getReceiptLine(o.optLong("id", 0L));
                if (line == null) return;
                line.category = o.optString("category", "");
                line.potId = nullableLong(o, "potId");
                dao.updateReceiptLine(line);
                if (o.optBoolean("learn", true)) saveRule("ITEM", Normalizer.key(line.description), line.category, line.potId);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void pickPdf() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/pdf");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                pdfLauncher.launch(intent);
            });
        }

        @JavascriptInterface
        public void scanReceipt() {
            runOnUiThread(() -> receiptLauncher.launch(new Intent(MainActivity.this, ReceiptScanActivity.class)));
        }

        @JavascriptInterface
        public void openNotificationSettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }

        @JavascriptInterface
        public void finishApp() {
            runOnUiThread(MainActivity.this::finish);
        }
    }

    private void importPdf(Uri uri) {
        PdfStatementImporter.importPdf(this, uri, new PdfStatementImporter.Callback() {
            @Override
            public void onSuccess(BankStatementParser.ParsedStatement statement, String rawText) {
                io.execute(() -> {
                    try {
                        ImportCoordinator.ImportSummary summary = ImportCoordinator.importStatement(MainActivity.this, statement);
                        JSONObject o = new JSONObject();
                        o.put("added", summary.added);
                        o.put("skipped", summary.skipped);
                        o.put("matchedNotifications", summary.matchedNotifications);
                        o.put("matchedReceipts", summary.matchedReceipts);
                        o.put("balanceChecked", summary.balanceChecked);
                        o.put("balanceValid", summary.balanceValid);
                        callJs("onPdfImport", o.toString());
                    } catch (Exception e) {
                        callJsError("onPdfError", e.getMessage() == null ? "PDF kon niet worden verwerkt." : e.getMessage());
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                callJsError("onPdfError", error.getMessage() == null ? "PDF kon niet worden gelezen." : error.getMessage());
            }
        });
    }

    private void processReceipt(String rawText) {
        io.execute(() -> {
            try {
                ReceiptParser.ParsedReceipt receipt = ReceiptParser.parse(rawText);
                if (receipt.totalCents <= 0) throw new IllegalArgumentException("Geen totaalbedrag op de bon gevonden.");
                String dedupe = hash("RECEIPT|" + Normalizer.key(rawText));
                TransactionEntity duplicate = dao.findByDedupeKey(dedupe);
                if (duplicate != null) {
                    JSONObject result = new JSONObject();
                    result.put("merchant", duplicate.merchant);
                    result.put("totalCents", Math.abs(duplicate.amountCents));
                    result.put("lines", dao.getReceiptLines(duplicate.matchedBankTransactionId != null ? duplicate.matchedBankTransactionId : duplicate.id).size());
                    result.put("duplicate", true);
                    callJs("onReceiptImport", result.toString());
                    return;
                }

                long timestamp = System.currentTimeMillis();
                long[] bounds = dayBounds(timestamp);
                TransactionEntity bankMatch = null;
                for (TransactionEntity candidate : dao.findBankCandidatesForReceipt(-receipt.totalCents, bounds[0], bounds[1], timestamp)) {
                    if (merchantCompatible(receipt.merchant, candidate.merchant)) {
                        bankMatch = candidate;
                        break;
                    }
                }

                TransactionEntity receiptTx = new TransactionEntity();
                receiptTx.source = "RECEIPT";
                receiptTx.importedAt = timestamp;
                receiptTx.occurredAt = timestamp;
                receiptTx.amountCents = -receipt.totalCents;
                receiptTx.merchant = receipt.merchant;
                receiptTx.description = "Bon";
                receiptTx.dateText = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date(timestamp));
                receiptTx.timeText = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(timestamp));
                receiptTx.dedupeKey = dedupe;
                receiptTx.affectsBalance = bankMatch == null;
                if (bankMatch != null) receiptTx.matchedBankTransactionId = bankMatch.id;

                MerchantRuleEntity merchantRule = dao.findRule("MERCHANT", Normalizer.key(receipt.merchant));
                if (receipt.lines.isEmpty() && merchantRule != null) {
                    receiptTx.category = merchantRule.category;
                    receiptTx.potId = merchantRule.potId;
                }

                long receiptId = dao.insertTransaction(receiptTx);
                if (receiptId <= 0) throw new IllegalStateException("Bon kon niet worden opgeslagen.");
                long targetId = bankMatch == null ? receiptId : bankMatch.id;

                int unknownCount = 0;
                for (ReceiptParser.ParsedLine parsed : receipt.lines) {
                    ReceiptLineEntity line = new ReceiptLineEntity();
                    line.transactionId = targetId;
                    line.description = parsed.description;
                    line.amountCents = parsed.amountCents;
                    MerchantRuleEntity rule = dao.findRule("ITEM", parsed.normalizedText);
                    if (rule != null) {
                        line.category = rule.category;
                        line.potId = rule.potId;
                    }
                    long lineId = dao.insertReceiptLine(line);
                    if (rule == null) {
                        UnknownItemEntity unknown = new UnknownItemEntity();
                        unknown.receiptLineId = lineId;
                        unknown.normalizedText = parsed.normalizedText;
                        unknown.displayText = parsed.description;
                        unknown.amountCents = parsed.amountCents;
                        unknown.createdAt = timestamp;
                        dao.insertUnknownItem(unknown);
                        unknownCount++;
                    }
                }

                JSONObject result = new JSONObject();
                result.put("merchant", receipt.merchant);
                result.put("totalCents", receipt.totalCents);
                result.put("lines", receipt.lines.size());
                result.put("unknown", unknownCount);
                result.put("matchedBank", bankMatch != null);
                callJs("onReceiptImport", result.toString());
            } catch (Exception e) {
                callJsError("onReceiptError", e.getMessage() == null ? "Bon kon niet worden verwerkt." : e.getMessage());
            }
        });
    }

    private JSONObject transactionJson(TransactionEntity tx) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", tx.id);
        o.put("source", tx.source);
        o.put("importedAt", tx.importedAt);
        o.put("occurredAt", tx.occurredAt);
        o.put("amountCents", tx.amountCents);
        o.put("merchant", tx.merchant);
        o.put("description", tx.description);
        o.put("category", tx.category);
        if (tx.potId == null) o.put("potId", JSONObject.NULL); else o.put("potId", tx.potId);
        o.put("cardReference", tx.cardReference);
        o.put("bankReference", tx.bankReference);
        o.put("dateText", tx.dateText);
        o.put("timeText", tx.timeText);
        o.put("dedupeKey", tx.dedupeKey);
        o.put("affectsBalance", tx.affectsBalance);
        o.put("excludeFromPots", tx.excludeFromPots);
        if (tx.matchedBankTransactionId == null) o.put("matchedBankTransactionId", JSONObject.NULL); else o.put("matchedBankTransactionId", tx.matchedBankTransactionId);
        if (tx.balanceAfterCents == null) o.put("balanceAfterCents", JSONObject.NULL); else o.put("balanceAfterCents", tx.balanceAfterCents);
        return o;
    }

    private PotEntity findPot(long id) {
        for (PotEntity pot : dao.getAllPots()) if (pot.id == id) return pot;
        return null;
    }

    private FixedCostEntity findFixed(long id) {
        for (FixedCostEntity cost : dao.getFixedCosts()) if (cost.id == id) return cost;
        return null;
    }

    private void saveRule(String type, String text, String category, Long potId) {
        if (text == null || text.trim().isEmpty()) return;
        MerchantRuleEntity rule = dao.findRule(type, text);
        if (rule == null) rule = new MerchantRuleEntity();
        rule.matchType = type;
        rule.matchText = text;
        rule.category = category == null ? "" : category;
        rule.potId = potId;
        dao.upsertRule(rule);
    }

    private Long nullableLong(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        long value = o.optLong(key, 0L);
        return value > 0 ? value : null;
    }

    private boolean merchantCompatible(String a, String b) {
        String na = Normalizer.key(a);
        String nb = Normalizer.key(b);
        if (na.isEmpty() || nb.isEmpty()) return true;
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    private long[] dayBounds(long timeMs) {
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

    private void callJs(String function, String json) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.BudgetAppNative&&window.BudgetAppNative." + function + "(" + JSONObject.quote(json) + ")", null);
        });
    }

    private void callJsError(String function, String message) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.BudgetAppNative&&window.BudgetAppNative." + function + "(" + JSONObject.quote(message == null ? "Onbekende fout" : message) + ")", null);
        });
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
}
