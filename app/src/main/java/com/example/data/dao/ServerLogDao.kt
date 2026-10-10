package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ServerLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerLogDao {
    @Query("SELECT * FROM server_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<ServerLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ServerLogEntity)

    @Query("DELETE FROM server_logs")
    suspend fun clearLogs()

    @Query("SELECT COUNT(*) FROM server_logs")
    suspend fun getTotalLogCount(): Long

    @Query("SELECT SUM(bytesTransferred) FROM server_logs")
    suspend fun getTotalBytesTransferred(): Long?
}
