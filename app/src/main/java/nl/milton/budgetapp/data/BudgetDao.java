package nl.milton.budgetapp.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BudgetDao {
    @Query("SELECT COUNT(*) FROM pots")
    int potCount();

    @Query("SELECT * FROM pots WHERE active = 1 ORDER BY sortOrder, id")
    List<PotEntity> getActivePots();

    @Query("SELECT * FROM pots ORDER BY sortOrder, id")
    List<PotEntity> getAllPots();

    @Insert
    long insertPot(PotEntity pot);

    @Update
    void updatePot(PotEntity pot);

    @Delete
    void deletePot(PotEntity pot);

    @Query("UPDATE transactions SET potId = NULL WHERE potId = :potId")
    void clearTransactionPot(long potId);

    @Query("UPDATE receipt_lines SET potId = NULL WHERE potId = :potId")
    void clearReceiptLinePot(long potId);

    @Query("UPDATE merchant_rules SET potId = NULL WHERE potId = :potId")
    void clearRulePot(long potId);

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    List<TransactionEntity> getRecentTransactions(int limit);

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    TransactionEntity getTransaction(long id);

    @Query("SELECT * FROM transactions WHERE dedupeKey = :dedupeKey LIMIT 1")
    TransactionEntity findByDedupeKey(String dedupeKey);

    @Query("SELECT * FROM transactions WHERE source = 'NOTIFICATION' AND amountCents = :amountCents AND dateText = :dateText AND timeText = :timeText AND merchant = :merchant AND cardReference = :cardReference AND bankReference = :bankReference LIMIT 1")
    TransactionEntity findStrictNotificationMatch(long amountCents, String dateText, String timeText, String merchant, String cardReference, String bankReference);

    @Query("SELECT * FROM transactions WHERE source = 'NOTIFICATION' AND amountCents = :amountCents AND occurredAt BETWEEN :startMs AND :endMs ORDER BY ABS(occurredAt - :targetMs) LIMIT 1")
    TransactionEntity findNotificationForUnknown(long amountCents, long startMs, long endMs, long targetMs);

    @Query("SELECT * FROM transactions WHERE source != 'RECEIPT' AND amountCents = :amountCents AND occurredAt BETWEEN :startMs AND :endMs ORDER BY ABS(occurredAt - :targetMs) LIMIT 10")
    List<TransactionEntity> findBankCandidatesForReceipt(long amountCents, long startMs, long endMs, long targetMs);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertTransaction(TransactionEntity transaction);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("DELETE FROM unknown_items WHERE receiptLineId IN (SELECT id FROM receipt_lines WHERE transactionId = :transactionId)")
    void deleteUnknownForTransaction(long transactionId);

    @Query("DELETE FROM receipt_lines WHERE transactionId = :transactionId")
    void deleteReceiptLinesForTransaction(long transactionId);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE affectsBalance = 1")
    long sumBalanceDelta();

    @Query("SELECT COALESCE(SUM(-amountCents), 0) FROM transactions t WHERE t.potId = :potId AND t.excludeFromPots = 0 AND t.amountCents < 0 AND t.occurredAt BETWEEN :startMs AND :endMs AND NOT EXISTS (SELECT 1 FROM receipt_lines r WHERE r.transactionId = t.id)")
    long sumDirectSpendForPot(long potId, long startMs, long endMs);

    @Query("SELECT COALESCE(SUM(r.amountCents), 0) FROM receipt_lines r INNER JOIN transactions t ON t.id = r.transactionId WHERE r.potId = :potId AND t.occurredAt BETWEEN :startMs AND :endMs")
    long sumReceiptSpendForPot(long potId, long startMs, long endMs);

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE affectsBalance = 1 AND occurredAt BETWEEN :startMs AND :endMs")
    long sumNetBetween(long startMs, long endMs);

    @Query("SELECT * FROM transactions WHERE source = 'RECEIPT' AND matchedBankTransactionId IS NULL AND amountCents = :amountCents AND occurredAt BETWEEN :startMs AND :endMs ORDER BY ABS(occurredAt - :targetMs) LIMIT 10")
    List<TransactionEntity> findReceiptCandidates(long amountCents, long startMs, long endMs, long targetMs);

    @Query("UPDATE receipt_lines SET transactionId = :newTransactionId WHERE transactionId = :oldTransactionId")
    void moveReceiptLines(long oldTransactionId, long newTransactionId);

    @Query("SELECT * FROM receipt_lines WHERE transactionId = :transactionId ORDER BY id")
    List<ReceiptLineEntity> getReceiptLines(long transactionId);

    @Insert
    long insertReceiptLine(ReceiptLineEntity line);

    @Update
    void updateReceiptLine(ReceiptLineEntity line);

    @Query("SELECT * FROM receipt_lines WHERE id = :id LIMIT 1")
    ReceiptLineEntity getReceiptLine(long id);

    @Query("SELECT * FROM fixed_costs ORDER BY active DESC, annualLevy, name")
    List<FixedCostEntity> getFixedCosts();

    @Insert
    long insertFixedCost(FixedCostEntity fixedCost);

    @Update
    void updateFixedCost(FixedCostEntity fixedCost);

    @Delete
    void deleteFixedCost(FixedCostEntity fixedCost);

    @Query("SELECT * FROM savings_goals WHERE active = 1 ORDER BY id LIMIT 1")
    SavingsGoalEntity getActiveSavingsGoal();

    @Insert
    long insertSavingsGoal(SavingsGoalEntity goal);

    @Update
    void updateSavingsGoal(SavingsGoalEntity goal);

    @Query("SELECT * FROM merchant_rules WHERE matchType = :matchType AND matchText = :matchText LIMIT 1")
    MerchantRuleEntity findRule(String matchType, String matchText);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsertRule(MerchantRuleEntity rule);

    @Query("SELECT * FROM unknown_items ORDER BY createdAt ASC, id ASC")
    List<UnknownItemEntity> getUnknownItems();

    @Query("SELECT * FROM unknown_items WHERE id = :id LIMIT 1")
    UnknownItemEntity getUnknownItem(long id);

    @Insert
    long insertUnknownItem(UnknownItemEntity item);

    @Delete
    void deleteUnknownItem(UnknownItemEntity item);
}