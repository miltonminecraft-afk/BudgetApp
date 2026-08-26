package nl.milton.budgetapp.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                PotEntity.class,
                TransactionEntity.class,
                ReceiptLineEntity.class,
                FixedCostEntity.class,
                SavingsGoalEntity.class,
                MerchantRuleEntity.class,
                UnknownItemEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract BudgetDao budgetDao();

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "budgetapp.db").build();
                }
            }
        }
        return INSTANCE;
    }
}
