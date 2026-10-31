package ru.sbros.app.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;

/** Never discard either side of a conflicting edit, including deletion conflicts. */
@Entity(tableName="sync_conflicts",primaryKeys={"scope","recordId"})
public class SyncConflict {
    @NonNull public String scope="";
    @NonNull public String recordId="";
    @NonNull public String localJson="{}";
    @NonNull public String remoteJson="{}";
    @NonNull public String createdAt="";
}
