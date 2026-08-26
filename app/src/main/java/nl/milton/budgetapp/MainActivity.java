package nl.milton.budgetapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

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
import nl.milton.budgetapp.domain.Money;
import nl.milton.budgetapp.domain.Normalizer;
import nl.milton.budgetapp.importers.ImportCoordinator;
import nl.milton.budgetapp.importers.PdfStatementImporter;
import nl.milton.budgetapp.importers.ReceiptParser;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int REQ_PDF = 100;
    private static final int REQ_RECEIPT = 101;

    private static final int BG = 0xFF0B0D10;
    private static final int PANEL = 0xFF161B22;
    private static final int TEXT = 0xFFF0F6FC;
    private static final int MUTED = 0xFF8B949E;
    private static final int ACCENT = 0xFF56D364;
    private static final int DANGER = 0xFFFF7B72;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private BudgetDao dao;
    private LinearLayout content;
    private TextView title;
    private String currentScreen = "dashboard";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dao = AppDatabase.get(this).budgetDao();
        setContentView(buildShell());

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 991);
        }

        io.execute(() -> {
            seedDefaults();
            runOnUiThread(() -> {
                if (getIntent().getBooleanExtra("open_learning", false)) renderImport();
                else renderDashboard();
            });
        });
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackgroundColor(PANEL);
        header.addView(text("BudgetApp", 22, TEXT, true));
        title = text("Overzicht", 13, MUTED, false);
        header.addView(title);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(22));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setBackgroundColor(PANEL);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.addView(navButton("Overzicht", v -> renderDashboard()));
        nav.addView(navButton("Potjes", v -> renderPots()));
        nav.addView(navButton("Transacties", v -> renderTransactions()));
        nav.addView(navButton("Import", v -> renderImport()));
        nav.addView(navButton("Instellingen", v -> renderSettings()));
        navScroll.addView(nav);
        root.addView(navScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    private Button navButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setOnClickListener(listener);
        return b;
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

    private void renderDashboard() {
        currentScreen = "dashboard";
        title.setText("Overzicht");
        showLoading();
        io.execute(() -> {
            List<PotEntity> pots = dao.getActivePots();
            SavingsGoalEntity goal = dao.getActiveSavingsGoal();
            long now = System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_MONTH, -90);
            long net90 = dao.sumNetBetween(c.getTimeInMillis(), now);

            SharedPreferences prefs = getSharedPreferences("budgetapp", MODE_PRIVATE);
            boolean known = prefs.getBoolean("has_known_balance", false);
            long balance = known ? prefs.getLong("known_balance_cents", 0L) : dao.sumBalanceDelta();

            List<PotStatus> status = new ArrayList<>();
            for (PotEntity pot : pots) {
                if (pot.hiddenFromOverview) continue;
                BudgetPeriods.Range range = BudgetPeriods.currentRange(pot.periodType, now);
                long spent = dao.sumDirectSpendForPot(pot.id, range.startMs, range.endMs)
                        + dao.sumReceiptSpendForPot(pot.id, range.startMs, range.endMs);
                status.add(new PotStatus(pot, spent));
            }

            runOnUiThread(() -> {
                content.removeAllViews();
                content.addView(section("Saldo"));
                content.addView(card((known ? "Bekend banksaldo" : "Berekend saldo") + "\n" + Money.format(balance),
                        known ? "Laatste gevalideerde PDF" : "Som van geregistreerde transacties"));
                content.addView(section("Potjes nu"));
                for (PotStatus ps : status) {
                    long remaining = ps.pot.budgetCents - ps.spent;
                    TextView row = card(ps.pot.name + "  " + Money.format(remaining) + " over",
                            Money.format(ps.spent) + " gebruikt van " + Money.format(ps.pot.budgetCents)
                                    + " per " + BudgetPeriods.displayName(ps.pot.periodType));
                    row.setOnClickListener(v -> showPotDialog(ps.pot));
                    content.addView(row);
                }

                content.addView(section("Spaardoel"));
                if (goal != null) {
                    long left = Math.max(0L, goal.targetCents - goal.currentCents);
                    long avgMonthly = Math.max(0L, Math.round(net90 / 3.0d));
                    String eta;
                    if (left == 0L) eta = "Doel bereikt";
                    else if (avgMonthly <= 0L) eta = "Nog geen positieve spaartrend om een schatting te maken";
                    else {
                        long months = (long) Math.ceil(left / (double) avgMonthly);
                        eta = "Geschat: " + months + " maand" + (months == 1 ? "" : "en")
                                + " bij " + Money.format(avgMonthly) + " netto per maand";
                    }
                    TextView goalCard = card(goal.name + "  " + Money.format(goal.currentCents) + " / " + Money.format(goal.targetCents), eta);
                    goalCard.setOnClickListener(v -> showSavingsGoalDialog(goal));
                    content.addView(goalCard);
                }
            });
        });
    }

    private void renderPots() {
        currentScreen = "pots";
        title.setText("Potjes");
        showLoading();
        io.execute(() -> {
            List<PotEntity> pots = dao.getAllPots();
            long now = System.currentTimeMillis();
            List<PotStatus> status = new ArrayList<>();
            for (PotEntity pot : pots) {
                BudgetPeriods.Range range = BudgetPeriods.currentRange(pot.periodType, now);
                long spent = dao.sumDirectSpendForPot(pot.id, range.startMs, range.endMs)
                        + dao.sumReceiptSpendForPot(pot.id, range.startMs, range.endMs);
                status.add(new PotStatus(pot, spent));
            }
            runOnUiThread(() -> {
                content.removeAllViews();
                content.addView(actionButton("+ Potje toevoegen", v -> showPotDialog(null)));
                for (PotStatus ps : status) {
                    String prefix = ps.pot.active ? "" : "Inactief • ";
                    TextView row = card(prefix + ps.pot.name,
                            Money.format(ps.pot.budgetCents) + " per " + BudgetPeriods.displayName(ps.pot.periodType)
                                    + " • " + Money.format(Math.max(0L, ps.pot.budgetCents - ps.spent)) + " over");
                    row.setOnClickListener(v -> showPotDialog(ps.pot));
                    content.addView(row);
                }
            });
        });
    }

    private void renderTransactions() {
        currentScreen = "transactions";
        title.setText("Transacties");
        showLoading();
        io.execute(() -> {
            List<TransactionEntity> transactions = dao.getRecentTransactions(250);
            runOnUiThread(() -> {
                content.removeAllViews();
                content.addView(actionButton("+ Handmatig toevoegen", v -> showTransactionDialog(null)));
                if (transactions.isEmpty()) {
                    content.addView(card("Nog geen transacties", "Importeer een PDF, scan een bon of voeg handmatig toe."));
                    return;
                }
                SimpleDateFormat date = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ROOT);
                for (TransactionEntity tx : transactions) {
                    String name = !tx.merchant.isEmpty() ? tx.merchant : (!tx.description.isEmpty() ? tx.description : "Transactie");
                    String subtitle = date.format(new Date(tx.occurredAt)) + " • " + tx.source
                            + (tx.category.isEmpty() ? "" : " • " + tx.category);
                    TextView row = card(name + "  " + Money.format(tx.amountCents), subtitle);
                    row.setOnClickListener(v -> showTransactionDetails(tx.id));
                    content.addView(row);
                }
            });
        });
    }

    private void renderImport() {
        currentScreen = "import";
        title.setText("Import & leren");
        content.removeAllViews();
        content.addView(section("Bank"));
        content.addView(actionButton("PDF-bankafschrift importeren", v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("application/pdf");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, REQ_PDF);
        }));
        content.addView(card("PDF-regels",
                "Overlappende of opnieuw geïmporteerde afschriften voegen alleen ontbrekende transacties toe. Begin- en eindsaldo worden gecontroleerd wanneer beide in de PDF staan."));

        content.addView(section("Bonnen"));
        content.addView(actionButton("Bon scannen", v -> startActivityForResult(new Intent(this, ReceiptScanActivity.class), REQ_RECEIPT)));
        content.addView(card("Bonregels per potje",
                "Een bon kan zelfstandig worden opgeslagen. Bonregels kunnen afzonderlijk aan verschillende potjes worden gekoppeld. Bij een latere bankmatch telt de banktransactie maar één keer mee voor het saldo."));

        content.addView(section("Live betaalmeldingen"));
        content.addView(actionButton("Notificatietoegang openen", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        content.addView(card("Rabobank + Google Wallet",
                "BudgetApp registreert relevante betaalnotificaties lokaal. PDF-data corrigeert een notificatie alleen bij een strikte match op bedrag/richting, datum, tijd, merchant, kaart en referentie."));

        content.addView(section("Nog te categoriseren"));
        loadUnknownItems();
    }

    private void loadUnknownItems() {
        io.execute(() -> {
            List<UnknownItemEntity> unknown = dao.getUnknownItems();
            runOnUiThread(() -> {
                if (unknown.isEmpty()) {
                    content.addView(card("Alles gecategoriseerd", "Er staan geen onbekende items in de wachtrij."));
                    return;
                }
                for (UnknownItemEntity item : unknown) {
                    TextView row = card(item.displayText + "  " + Money.format(item.amountCents), "Tik om potje en categorie te kiezen");
                    row.setOnClickListener(v -> showUnknownItemDialog(item));
                    content.addView(row);
                }
            });
        });
    }

    private void renderSettings() {
        currentScreen = "settings";
        title.setText("Instellingen");
        showLoading();
        io.execute(() -> {
            List<FixedCostEntity> costs = dao.getFixedCosts();
            SavingsGoalEntity goal = dao.getActiveSavingsGoal();
            runOnUiThread(() -> {
                content.removeAllViews();
                content.addView(section("Vaste lasten"));
                content.addView(actionButton("+ Vaste last toevoegen", v -> showFixedCostDialog(null)));
                if (costs.isEmpty()) {
                    content.addView(card("Nog geen vaste lasten ingevoerd",
                            "Jaarlijkse heffingen, zoals waterheffing, worden apart herkend en zijn geen zichtbaar potje."));
                } else {
                    for (FixedCostEntity cost : costs) {
                        TextView row = card(cost.name + "  " + Money.format(cost.amountCents),
                                BudgetPeriods.displayName(cost.periodType) + (cost.annualLevy ? " • jaarlijkse heffing" : ""));
                        row.setOnClickListener(v -> showFixedCostDialog(cost));
                        content.addView(row);
                    }
                }

                content.addView(section("Spaardoel"));
                if (goal != null) {
                    TextView row = card(goal.name + "  " + Money.format(goal.currentCents) + " / " + Money.format(goal.targetCents), "Tik om aan te passen");
                    row.setOnClickListener(v -> showSavingsGoalDialog(goal));
                    content.addView(row);
                }

                content.addView(section("Privacy"));
                content.addView(card("Lokaal op dit toestel",
                        "Room/SQLite, PDF-OCR, bon-OCR, categoriegeheugen en transacties blijven lokaal. BudgetApp heeft geen cloud-account of externe analyse nodig."));
            });
        });
    }

    private void showPotDialog(PotEntity existing) {
        boolean edit = existing != null;
        PotEntity pot = edit ? existing : new PotEntity();
        LinearLayout form = dialogForm();
        EditText name = field("Naam", edit ? pot.name : "");
        EditText amount = moneyField("Budget", edit ? Money.format(pot.budgetCents).replace("€", "").trim() : "");
        Spinner period = periodSpinner(edit ? pot.periodType : BudgetPeriods.MONTH);
        CheckBox active = check("Actief", edit ? pot.active : true);
        CheckBox hidden = check("Verbergen in overzicht", edit && pot.hiddenFromOverview);
        form.addView(name);
        form.addView(amount);
        form.addView(label("Periode"));
        form.addView(period);
        form.addView(active);
        form.addView(hidden);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(edit ? "Potje aanpassen" : "Potje toevoegen")
                .setView(wrapDialog(form))
                .setPositiveButton("Opslaan", null)
                .setNegativeButton("Annuleren", null);
        if (edit) builder.setNeutralButton("Verwijderen", null);
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    pot.name = name.getText().toString().trim();
                    pot.budgetCents = Math.abs(Money.parseCents(amount.getText().toString()));
                    pot.periodType = periodCode(period.getSelectedItemPosition());
                    pot.active = active.isChecked();
                    pot.hiddenFromOverview = hidden.isChecked();
                    if (pot.name.isEmpty()) throw new IllegalArgumentException("Naam is verplicht.");
                    io.execute(() -> {
                        if (edit) dao.updatePot(pot); else dao.insertPot(pot);
                        runOnUiThread(() -> { dialog.dismiss(); refreshCurrent(); });
                    });
                } catch (Exception e) { toast(e.getMessage()); }
            });
            if (edit) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DANGER);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> io.execute(() -> {
                    dao.deletePot(pot);
                    runOnUiThread(() -> { dialog.dismiss(); refreshCurrent(); });
                }));
            }
        });
        dialog.show();
    }

    private void showTransactionDialog(TransactionEntity existing) {
        boolean edit = existing != null;
        TransactionEntity tx = edit ? existing : new TransactionEntity();
        io.execute(() -> {
            List<PotEntity> pots = dao.getActivePots();
            runOnUiThread(() -> {
                LinearLayout form = dialogForm();
                EditText amount = moneyField("Bedrag (uitgave negatief)", edit ? String.format(Locale.ROOT, "%.2f", tx.amountCents / 100.0d).replace('.', ',') : "");
                EditText merchant = field("Merchant / naam", edit ? tx.merchant : "");
                EditText description = field("Omschrijving", edit ? tx.description : "");
                EditText category = field("Categorie", edit ? tx.category : "");
                Spinner potSpinner = potSpinner(pots, edit ? tx.potId : null);
                form.addView(amount);
                form.addView(merchant);
                form.addView(description);
                form.addView(category);
                form.addView(label("Potje"));
                form.addView(potSpinner);

                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                        .setTitle(edit ? "Transactie aanpassen" : "Handmatige transactie")
                        .setView(wrapDialog(form))
                        .setPositiveButton("Opslaan", null)
                        .setNegativeButton("Annuleren", null);
                if (edit) builder.setNeutralButton("Verwijderen", null);
                AlertDialog dialog = builder.create();

                dialog.setOnShowListener(x -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                        try {
                            tx.amountCents = Money.parseCents(amount.getText().toString());
                            tx.merchant = merchant.getText().toString().trim();
                            tx.description = description.getText().toString().trim();
                            tx.category = category.getText().toString().trim();
                            tx.potId = selectedPotId(pots, potSpinner.getSelectedItemPosition());
                            if (!edit) {
                                tx.source = "MANUAL";
                                tx.occurredAt = System.currentTimeMillis();
                                tx.importedAt = System.currentTimeMillis();
                                tx.dateText = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date(tx.occurredAt));
                                tx.timeText = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(tx.occurredAt));
                                tx.dedupeKey = hash("MANUAL|" + tx.occurredAt + "|" + tx.amountCents + "|" + Math.random());
                            }
                            io.execute(() -> {
                                if (edit) dao.updateTransaction(tx); else dao.insertTransaction(tx);
                                runOnUiThread(() -> { dialog.dismiss(); refreshCurrent(); });
                            });
                        } catch (Exception e) { toast("Controleer het bedrag."); }
                    });
                    if (edit) {
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DANGER);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> io.execute(() -> {
                            dao.deleteTransaction(tx);
                            runOnUiThread(() -> { dialog.dismiss(); refreshCurrent(); });
                        }));
                    }
                });
                dialog.show();
            });
        });
    }

    private void showTransactionDetails(long id) {
        io.execute(() -> {
            TransactionEntity tx = dao.getTransaction(id);
            List<ReceiptLineEntity> lines = dao.getReceiptLines(id);
            runOnUiThread(() -> {
                if (tx == null) return;
                LinearLayout detail = dialogForm();
                addDetail(detail, "Bedrag", Money.format(tx.amountCents));
                addDetail(detail, "Naam", tx.merchant);
                addDetail(detail, "Omschrijving", tx.description);
                addDetail(detail, "Categorie", tx.category);
                addDetail(detail, "Bron", tx.source);
                addDetail(detail, "Datum", new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.ROOT).format(new Date(tx.occurredAt)));
                addDetail(detail, "Kaart", tx.cardReference);
                addDetail(detail, "Referentie", tx.bankReference);
                if (tx.excludeFromPots) addDetail(detail, "Potjes", "Verborgen jaarlijkse heffing");
                if (!lines.isEmpty()) {
                    detail.addView(section("Bonregels"));
                    for (ReceiptLineEntity line : lines) {
                        TextView lineView = card(line.description + "  " + Money.format(line.amountCents),
                                line.category.isEmpty() ? "Tik om potje/categorie te wijzigen" : line.category);
                        lineView.setOnClickListener(v -> showReceiptLineDialog(line));
                        detail.addView(lineView);
                    }
                }
                new AlertDialog.Builder(this)
                        .setTitle(tx.merchant.isEmpty() ? "Transactie" : tx.merchant)
                        .setView(wrapDialog(detail))
                        .setPositiveButton("Sluiten", null)
                        .setNeutralButton("Bewerken", (d, w) -> showTransactionDialog(tx))
                        .show();
            });
        });
    }

    private void showFixedCostDialog(FixedCostEntity existing) {
        boolean edit = existing != null;
        FixedCostEntity cost = edit ? existing : new FixedCostEntity();
        LinearLayout form = dialogForm();
        EditText name = field("Naam", edit ? cost.name : "");
        EditText amount = moneyField("Bedrag", edit ? String.format(Locale.ROOT, "%.2f", cost.amountCents / 100.0d).replace('.', ',') : "");
        Spinner period = periodSpinner(edit ? cost.periodType : BudgetPeriods.MONTH);
        EditText due = field("Vervaldag (1-31)", edit ? String.valueOf(cost.dueDay) : "1");
        due.setInputType(InputType.TYPE_CLASS_NUMBER);
        CheckBox active = check("Actief", edit ? cost.active : true);
        CheckBox annual = check("Jaarlijkse heffing (niet als potje tonen)", edit && cost.annualLevy);
        form.addView(name);
        form.addView(amount);
        form.addView(label("Periode"));
        form.addView(period);
        form.addView(due);
        form.addView(active);
        form.addView(annual);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(edit ? "Vaste last aanpassen" : "Vaste last toevoegen")
                .setView(wrapDialog(form))
                .setPositiveButton("Opslaan", null)
                .setNegativeButton("Annuleren", null);
        if (edit) builder.setNeutralButton("Verwijderen", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    cost.name = name.getText().toString().trim();
                    cost.amountCents = Math.abs(Money.parseCents(amount.getText().toString()));
                    cost.periodType = periodCode(period.getSelectedItemPosition());
                    cost.dueDay = Math.max(1, Math.min(31, Integer.parseInt(due.getText().toString())));
                    cost.active = active.isChecked();
                    cost.annualLevy = annual.isChecked();
                    if (cost.name.isEmpty()) throw new IllegalArgumentException("Naam is verplicht.");
                    io.execute(() -> {
                        if (edit) dao.updateFixedCost(cost); else dao.insertFixedCost(cost);
                        runOnUiThread(() -> { dialog.dismiss(); renderSettings(); });
                    });
                } catch (Exception e) { toast("Controleer de invoer."); }
            });
            if (edit) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DANGER);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> io.execute(() -> {
                    dao.deleteFixedCost(cost);
                    runOnUiThread(() -> { dialog.dismiss(); renderSettings(); });
                }));
            }
        });
        dialog.show();
    }

    private void showSavingsGoalDialog(SavingsGoalEntity goal) {
        LinearLayout form = dialogForm();
        EditText name = field("Naam", goal.name);
        EditText current = moneyField("Nu gespaard", String.format(Locale.ROOT, "%.2f", goal.currentCents / 100.0d).replace('.', ','));
        EditText target = moneyField("Doel", String.format(Locale.ROOT, "%.2f", goal.targetCents / 100.0d).replace('.', ','));
        form.addView(name);
        form.addView(current);
        form.addView(target);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Spaardoel aanpassen")
                .setView(wrapDialog(form))
                .setPositiveButton("Opslaan", null)
                .setNegativeButton("Annuleren", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                goal.name = name.getText().toString().trim();
                goal.currentCents = Math.max(0L, Money.parseCents(current.getText().toString()));
                goal.targetCents = Math.max(0L, Money.parseCents(target.getText().toString()));
                io.execute(() -> {
                    dao.updateSavingsGoal(goal);
                    runOnUiThread(() -> { dialog.dismiss(); refreshCurrent(); });
                });
            } catch (Exception e) { toast("Controleer de bedragen."); }
        }));
        dialog.show();
    }

    private void showUnknownItemDialog(UnknownItemEntity item) {
        io.execute(() -> {
            List<PotEntity> pots = dao.getActivePots();
            runOnUiThread(() -> {
                LinearLayout form = dialogForm();
                Spinner pot = potSpinner(pots, null);
                EditText category = field("Categorie", "");
                CheckBox remember = check("Onthoud deze keuze voor dit item", true);
                form.addView(label("Potje"));
                form.addView(pot);
                form.addView(category);
                form.addView(remember);
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(item.displayText)
                        .setView(wrapDialog(form))
                        .setPositiveButton("Opslaan", null)
                        .setNegativeButton("Annuleren", null)
                        .create();
                dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Long potId = selectedPotId(pots, pot.getSelectedItemPosition());
                    String categoryValue = category.getText().toString().trim();
                    io.execute(() -> {
                        if (item.receiptLineId > 0) {
                            ReceiptLineEntity line = dao.getReceiptLine(item.receiptLineId);
                            if (line != null) {
                                line.potId = potId;
                                line.category = categoryValue;
                                dao.updateReceiptLine(line);
                            }
                        }
                        if (remember.isChecked() && !item.normalizedText.isEmpty()) {
                            MerchantRuleEntity rule = new MerchantRuleEntity();
                            rule.matchType = item.receiptLineId > 0 ? "ITEM" : "MERCHANT";
                            rule.matchText = item.normalizedText;
                            rule.potId = potId;
                            rule.category = categoryValue;
                            dao.upsertRule(rule);
                        }
                        dao.deleteUnknownItem(item);
                        runOnUiThread(() -> { dialog.dismiss(); renderImport(); });
                    });
                }));
                dialog.show();
            });
        });
    }

    private void showReceiptLineDialog(ReceiptLineEntity line) {
        io.execute(() -> {
            List<PotEntity> pots = dao.getActivePots();
            runOnUiThread(() -> {
                LinearLayout form = dialogForm();
                Spinner pot = potSpinner(pots, line.potId);
                EditText category = field("Categorie", line.category);
                CheckBox remember = check("Onthoud voor dezelfde bonregel", true);
                form.addView(label("Potje"));
                form.addView(pot);
                form.addView(category);
                form.addView(remember);
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle(line.description)
                        .setView(wrapDialog(form))
                        .setPositiveButton("Opslaan", null)
                        .setNegativeButton("Annuleren", null)
                        .create();
                dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    line.potId = selectedPotId(pots, pot.getSelectedItemPosition());
                    line.category = category.getText().toString().trim();
                    io.execute(() -> {
                        dao.updateReceiptLine(line);
                        if (remember.isChecked()) {
                            MerchantRuleEntity rule = new MerchantRuleEntity();
                            rule.matchType = "ITEM";
                            rule.matchText = Normalizer.key(line.description);
                            rule.potId = line.potId;
                            rule.category = line.category;
                            dao.upsertRule(rule);
                        }
                        runOnUiThread(dialog::dismiss);
                    });
                }));
                dialog.show();
            });
        });
    }

    private void importReceiptText(String rawText) {
        ReceiptParser.ParsedReceipt parsed = ReceiptParser.parse(rawText);
        if (parsed.totalCents <= 0L) {
            toast("Geen totaalbedrag op de bon gevonden.");
            return;
        }
        EditText merchant = field("Winkel / merchant", parsed.merchant);
        new AlertDialog.Builder(this)
                .setTitle("Bon opslaan")
                .setMessage(parsed.lines.size() + " bonregels gevonden • totaal " + Money.format(parsed.totalCents))
                .setView(merchant)
                .setPositiveButton("Opslaan", (dialog, which) -> io.execute(() -> {
                    long now = System.currentTimeMillis();
                    TransactionEntity tx = new TransactionEntity();
                    tx.source = "RECEIPT";
                    tx.importedAt = now;
                    tx.occurredAt = now;
                    tx.amountCents = -parsed.totalCents;
                    tx.merchant = merchant.getText().toString().trim();
                    tx.description = "Bon";
                    tx.dateText = new SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).format(new Date(now));
                    tx.timeText = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new Date(now));
                    tx.dedupeKey = hash("RECEIPT|" + now + "|" + parsed.totalCents + "|" + Math.random());
                    tx.affectsBalance = true;
                    long txId = dao.insertTransaction(tx);
                    for (ReceiptParser.ParsedLine sourceLine : parsed.lines) {
                        ReceiptLineEntity line = new ReceiptLineEntity();
                        line.transactionId = txId;
                        line.description = sourceLine.description;
                        line.amountCents = sourceLine.amountCents;
                        MerchantRuleEntity rule = dao.findRule("ITEM", sourceLine.normalizedText);
                        if (rule != null) {
                            line.potId = rule.potId;
                            line.category = rule.category;
                        }
                        long lineId = dao.insertReceiptLine(line);
                        if (rule == null) {
                            UnknownItemEntity unknown = new UnknownItemEntity();
                            unknown.receiptLineId = lineId;
                            unknown.normalizedText = sourceLine.normalizedText;
                            unknown.displayText = sourceLine.description;
                            unknown.amountCents = sourceLine.amountCents;
                            unknown.createdAt = now;
                            dao.insertUnknownItem(unknown);
                        }
                    }
                    runOnUiThread(() -> {
                        toast("Bon opgeslagen. Onbekende regels staan bij Import & leren.");
                        renderImport();
                    });
                }))
                .setNegativeButton("Annuleren", null)
                .show();
    }

    private void importPdf(Uri uri) {
        title.setText("PDF wordt lokaal gelezen…");
        toast("PDF import gestart");
        PdfStatementImporter.importPdf(this, uri, new PdfStatementImporter.Callback() {
            @Override
            public void onSuccess(nl.milton.budgetapp.importers.BankStatementParser.ParsedStatement statement, String rawText) {
                io.execute(() -> {
                    ImportCoordinator.ImportSummary summary = ImportCoordinator.importStatement(MainActivity.this, statement);
                    runOnUiThread(() -> {
                        title.setText("Import & leren");
                        String balance;
                        if (!summary.balanceChecked) balance = "Saldo niet controleerbaar: begin- en/of eindsaldo niet herkend.";
                        else if (summary.balanceValid) balance = "Saldocontrole klopt.";
                        else balance = "WAARSCHUWING: begin + unieke PDF-mutaties komt niet uit op het eindsaldo.";
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("PDF import gereed")
                                .setMessage("Nieuw: " + summary.added
                                        + "\nOvergeslagen/dubbel: " + summary.skipped
                                        + "\nStrikte notificatiematches: " + summary.matchedNotifications
                                        + "\nBonmatches: " + summary.matchedReceipts
                                        + "\n\n" + balance)
                                .setPositiveButton("OK", (d, w) -> renderTransactions())
                                .show();
                    });
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    title.setText("Import & leren");
                    toast("PDF import mislukt: " + error.getMessage());
                });
            }
        });
    }

    @Override
    @Deprecated
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PDF && resultCode == RESULT_OK && data != null && data.getData() != null) importPdf(data.getData());
        else if (requestCode == REQ_RECEIPT) {
            if (resultCode == RESULT_OK && data != null) {
                String text = data.getStringExtra("receipt_text");
                importReceiptText(text == null ? "" : text);
            } else if (data != null && data.hasExtra("error")) toast(data.getStringExtra("error"));
        }
    }

    private void refreshCurrent() {
        switch (currentScreen) {
            case "pots": renderPots(); break;
            case "transactions": renderTransactions(); break;
            case "import": renderImport(); break;
            case "settings": renderSettings(); break;
            default: renderDashboard(); break;
        }
    }

    private void showLoading() {
        content.removeAllViews();
        TextView loading = text("Laden…", 15, MUTED, false);
        loading.setPadding(dp(8), dp(20), dp(8), dp(20));
        content.addView(loading);
    }

    private TextView section(String value) {
        TextView tv = text(value, 16, TEXT, true);
        tv.setPadding(dp(2), dp(14), dp(2), dp(8));
        return tv;
    }

    private TextView card(String main, String sub) {
        TextView tv = text(main + (sub == null || sub.isEmpty() ? "" : "\n" + sub), 15, TEXT, false);
        tv.setLineSpacing(0, 1.12f);
        tv.setPadding(dp(14), dp(12), dp(14), dp(12));
        tv.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setBackgroundColor(ACCENT);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(2), dp(2), dp(2), dp(2));
        return form;
    }

    private View wrapDialog(View view) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(20), dp(8), dp(20), dp(8));
        holder.addView(view);
        scroll.addView(holder);
        return scroll;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setSingleLine(false);
        return e;
    }

    private EditText moneyField(String hint, String value) {
        EditText e = field(hint, value);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        e.setSingleLine(true);
        return e;
    }

    private CheckBox check(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(TEXT);
        box.setChecked(checked);
        return box;
    }

    private TextView label(String value) {
        TextView tv = text(value, 12, MUTED, false);
        tv.setPadding(0, dp(8), 0, dp(2));
        return tv;
    }

    private Spinner periodSpinner(String selectedCode) {
        String[] labels = {"Week", "Salarisperiode 23e–22e", "Maand", "Jaar", "Eenmalig"};
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(periodIndex(selectedCode));
        return spinner;
    }

    private int periodIndex(String code) {
        if (BudgetPeriods.WEEK.equals(code)) return 0;
        if (BudgetPeriods.SALARY_PERIOD.equals(code)) return 1;
        if (BudgetPeriods.YEAR.equals(code)) return 3;
        if (BudgetPeriods.ONE_TIME.equals(code)) return 4;
        return 2;
    }

    private String periodCode(int index) {
        switch (index) {
            case 0: return BudgetPeriods.WEEK;
            case 1: return BudgetPeriods.SALARY_PERIOD;
            case 3: return BudgetPeriods.YEAR;
            case 4: return BudgetPeriods.ONE_TIME;
            default: return BudgetPeriods.MONTH;
        }
    }

    private Spinner potSpinner(List<PotEntity> pots, Long selectedId) {
        List<String> labels = new ArrayList<>();
        labels.add("Geen potje");
        int selected = 0;
        for (int i = 0; i < pots.size(); i++) {
            labels.add(pots.get(i).name);
            if (selectedId != null && pots.get(i).id == selectedId.longValue()) selected = i + 1;
        }
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(selected);
        return spinner;
    }

    private Long selectedPotId(List<PotEntity> pots, int position) {
        if (position <= 0 || position > pots.size()) return null;
        return pots.get(position - 1).id;
    }

    private void addDetail(LinearLayout parent, String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        parent.addView(card(key, value));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private void toast(String value) {
        if (value == null || value.isEmpty()) return;
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
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

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class PotStatus {
        final PotEntity pot;
        final long spent;
        PotStatus(PotEntity pot, long spent) {
            this.pot = pot;
            this.spent = spent;
        }
    }
}
