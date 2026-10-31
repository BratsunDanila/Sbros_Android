package ru.sbros.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

/** Stable, account-scoped storage. UI integer IDs never leave the device. */
@Entity(tableName="records", primaryKeys={"scope","id"}, indices={@Index(value={"scope","kind","localId"},unique=true)})
public class LocalRecord {
    @NonNull public String scope="local";
    @NonNull public String id="";
    @NonNull public String kind="";
    public int localId;
    @NonNull public String payload="{}";
    public String projectId;
    public String captureId;
    @NonNull public String createdAt="";
    @NonNull public String updatedAt="";
    public String deletedAt;
    public long serverVersion;
    @NonNull public String changeId="";
    public boolean dirty=true;
}
