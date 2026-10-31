package ru.sbros.app.data.local;
import android.content.Context;
import androidx.room.*;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

@Database(entities={LocalRecord.class,WorkspaceRow.class,SyncConflict.class},version=2,exportSchema=true)
public abstract class SbrosDatabase extends RoomDatabase {
    public abstract LocalDao localDao();
    public static final Migration MIGRATION_1_2=new Migration(1,2) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE records ADD COLUMN deletedAt TEXT");
        }
    };
    public static SbrosDatabase open(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(),SbrosDatabase.class,"sbros.db")
            .addMigrations(MIGRATION_1_2).build();
    }
}
