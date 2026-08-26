package nl.milton.budgetapp.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "transactions",
    indices = {@Index(value = {"dedupeKey"}, unique = true)}
)
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String source = "MANUAL";
    public long importedAt;
    public long occurredAt;
    public long amountCents;
    public String merchant = "";
    public String description = "";
    public String category = "";

    @Nullable public Long potId;
    public String cardReference = "";
    public String bankReference = "";
    public String dateText = "";
    public String timeText = "";
    public String dedupeKey = "";

    public boolean affectsBalance = true;
    public boolean excludeFromPots = false;
    @Nullable public Long matchedBankTransactionId;
    @Nullable public Long balanceAfterCents;
}
