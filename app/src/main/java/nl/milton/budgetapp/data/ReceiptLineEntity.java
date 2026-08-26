package nl.milton.budgetapp.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "receipt_lines")
public class ReceiptLineEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long transactionId;
    public String description = "";
    public long amountCents;
    @Nullable public Long potId;
    public String category = "";
}
