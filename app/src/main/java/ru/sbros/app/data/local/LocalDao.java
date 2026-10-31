package ru.sbros.app.data.local;
import androidx.room.*;
import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface LocalDao {
    @Query("SELECT * FROM records WHERE scope=:scope ORDER BY localId,id") List<LocalRecord> records(String scope);
    @Query("SELECT * FROM records WHERE scope=:scope ORDER BY localId,id") Flow<List<LocalRecord>> observe(String scope);
    @Query("SELECT * FROM workspaces WHERE scope=:scope") WorkspaceRow workspace(String scope);
    @Query("SELECT * FROM workspaces WHERE scope=:scope") Flow<WorkspaceRow> observeWorkspace(String scope);
    @Query("SELECT COUNT(*) FROM workspaces WHERE scope='legacy'") Flow<Integer> observeLegacy();
    @Insert(onConflict=OnConflictStrategy.REPLACE) void putRecords(List<LocalRecord> records);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void putWorkspace(WorkspaceRow workspace);
    @Query("SELECT * FROM sync_conflicts WHERE scope=:scope") List<SyncConflict> conflicts(String scope);
    @Query("SELECT * FROM sync_conflicts WHERE scope=:scope") Flow<List<SyncConflict>> observeConflicts(String scope);
    @Insert(onConflict=OnConflictStrategy.REPLACE) void putConflict(SyncConflict conflict);
    @Query("DELETE FROM sync_conflicts WHERE scope=:scope AND recordId=:id") void clearConflict(String scope,String id);
    @Query("DELETE FROM sync_conflicts WHERE EXISTS (SELECT 1 FROM records r WHERE r.scope=sync_conflicts.scope AND r.id=sync_conflicts.recordId AND r.kind='review')") void clearWeeklyReviewConflicts();
    @Query("DELETE FROM records WHERE kind='review'") void clearWeeklyReviews();
    @Query("DELETE FROM records WHERE scope='demo'") void clearDemo();
    @Query("DELETE FROM workspaces WHERE scope='demo'") void clearDemoWorkspace();
}
