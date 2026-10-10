package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SharedItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedItemDao {
    @Query("SELECT * FROM shared_items WHERE shareId = :shareId ORDER BY isDirectory DESC, displayName ASC")
    fun getItemsForShare(shareId: String): Flow<List<SharedItemEntity>>

    @Query("SELECT * FROM shared_items WHERE shareId = :shareId")
    suspend fun getItemsForShareSync(shareId: String): List<SharedItemEntity>

    @Query("SELECT * FROM shared_items ORDER BY addedAt DESC")
    fun getAllSharedItems(): Flow<List<SharedItemEntity>>

    @Query("SELECT * FROM shared_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): SharedItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SharedItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SharedItemEntity>)

    @Delete
    suspend fun deleteItem(item: SharedItemEntity)

    @Query("DELETE FROM shared_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM shared_items WHERE shareId = :shareId")
    suspend fun deleteItemsForShare(shareId: String)
}
