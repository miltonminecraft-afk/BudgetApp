package nl.milton.budgetapp.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "savings_goals")
public class SavingsGoalEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name = "Spaardoel";
    public long targetCents;
    public long currentCents;
    public boolean active = true;
}
