package nl.milton.budgetapp.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fixed_costs")
public class FixedCostEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name = "";
    public long amountCents;
    public String periodType = "MONTH";
    public int dueDay = 1;
    public boolean active = true;
    public boolean annualLevy = false;
}
