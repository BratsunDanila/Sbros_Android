package ru.sbros.app.data.local;
import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName="workspaces")
public class WorkspaceRow {
    @PrimaryKey @NonNull public String scope="local";
    @NonNull public String draft="";
    @NonNull public String uiState="{}";
    public boolean syncEnabled=false;
}
