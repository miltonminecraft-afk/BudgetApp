package nl.milton.budgetapp.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pots")
public class PotEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name = "";
    public long budgetCents;
    public String periodType = "MONTH";
    public boolean active = true;
    public boolean hiddenFromOverview = false;
    public int sortOrder = 0;
}
