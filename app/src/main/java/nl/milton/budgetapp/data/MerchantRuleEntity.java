package nl.milton.budgetapp.data;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "merchant_rules",
    indices = {@Index(value = {"matchType", "matchText"}, unique = true)}
)
public class MerchantRuleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String matchType = "MERCHANT";
    public String matchText = "";
    public String category = "";
    @Nullable public Long potId;
}
