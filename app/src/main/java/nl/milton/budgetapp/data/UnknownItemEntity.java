package nl.milton.budgetapp.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "unknown_items")
public class UnknownItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long receiptLineId;
    public String normalizedText = "";
    public String displayText = "";
    public long amountCents;
    public long createdAt;
}
