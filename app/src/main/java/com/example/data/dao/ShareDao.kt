package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ShareEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareDao {
    @Query("SELECT * FROM shares ORDER BY createdAt DESC")
    fun getAllShares(): Flow<List<ShareEntity>>

    @Query("SELECT * FROM shares WHERE isEnabled = 1 ORDER BY createdAt DESC")
    fun getEnabledShares(): Flow<List<ShareEntity>>

    @Query("SELECT * FROM shares WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledSharesSync(): List<ShareEntity>

    @Query("SELECT * FROM shares ORDER BY createdAt DESC")
    suspend fun getAllSharesSync(): List<ShareEntity>

    @Query("SELECT * FROM shares WHERE id = :id LIMIT 1")
    suspend fun getShareById(id: String): ShareEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShare(share: ShareEntity)

    @Update
    suspend fun updateShare(share: ShareEntity)

    @Delete
    suspend fun deleteShare(share: ShareEntity)

    @Query("DELETE FROM shares WHERE id = :id")
    suspend fun deleteShareById(id: String)
}
