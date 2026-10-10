package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ShareEntity
import com.example.data.repository.LocalShareRepository
import com.example.network.NetworkUtils
import com.example.util.QrCodeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LocalShare", appName)
    }

    @Test
    fun `test password hashing and verification`() {
        val salt = LocalShareRepository.generateSalt()
        assertTrue(salt.isNotEmpty())

        val password = "SecretSharePassword123"
        val hash = LocalShareRepository.hashPassword(password, salt)

        val share = ShareEntity(
            id = "test_share",
            name = "Vault",
            isPasswordProtected = true,
            passwordHash = hash,
            salt = salt
        )

        // Correct password should match
        assertTrue(LocalShareRepository(ApplicationProvider.getApplicationContext(), nullMockDao(), nullMockItemDao(), nullMockLogDao()).verifyPassword(share, "SecretSharePassword123"))

        // Incorrect password should fail
        assertFalse(LocalShareRepository(ApplicationProvider.getApplicationContext(), nullMockDao(), nullMockItemDao(), nullMockLogDao()).verifyPassword(share, "WrongPassword"))
    }

    @Test
    fun `test mime type detection`() {
        assertEquals("image/png", LocalShareRepository.determineMimeType("photo.png"))
        assertEquals("video/mp4", LocalShareRepository.determineMimeType("movie.mp4"))
        assertEquals("audio/mpeg", LocalShareRepository.determineMimeType("song.mp3"))
        assertEquals("application/pdf", LocalShareRepository.determineMimeType("document.pdf"))
        assertEquals("text/markdown; charset=utf-8", LocalShareRepository.determineMimeType("readme.md"))
        assertEquals("application/zip", LocalShareRepository.determineMimeType("archive.zip"))
    }

    @Test
    fun `test url formatting`() {
        val url = NetworkUtils.formatShareUrl("192.168.1.15", 8080)
        assertEquals("http://192.168.1.15:8080/", url)

        val shareUrl = NetworkUtils.formatShareUrl("192.168.1.15", 8080, "vault")
        assertEquals("http://192.168.1.15:8080/#/share/vault", shareUrl)
    }

    @Test
    fun `test qr code generation`() {
        val bitmap = QrCodeUtils.generateQrBitmap("http://192.168.1.15:8080/", size = 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap!!.width)
        assertEquals(256, bitmap.height)
    }

    // Mock dummy DAO helpers for isolated unit tests
    private fun nullMockDao(): com.example.data.dao.ShareDao = object : com.example.data.dao.ShareDao {
        override fun getAllShares() = kotlinx.coroutines.flow.emptyFlow<List<ShareEntity>>()
        override fun getEnabledShares() = kotlinx.coroutines.flow.emptyFlow<List<ShareEntity>>()
        override suspend fun getEnabledSharesSync() = emptyList<ShareEntity>()
        override suspend fun getAllSharesSync() = emptyList<ShareEntity>()
        override suspend fun getShareById(id: String) = null
        override suspend fun insertShare(share: ShareEntity) {}
        override suspend fun updateShare(share: ShareEntity) {}
        override suspend fun deleteShare(share: ShareEntity) {}
        override suspend fun deleteShareById(id: String) {}
    }

    private fun nullMockItemDao(): com.example.data.dao.SharedItemDao = object : com.example.data.dao.SharedItemDao {
        override fun getItemsForShare(shareId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.model.SharedItemEntity>>()
        override suspend fun getItemsForShareSync(shareId: String) = emptyList<com.example.data.model.SharedItemEntity>()
        override fun getAllSharedItems() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.model.SharedItemEntity>>()
        override suspend fun getItemById(id: String) = null
        override suspend fun insertItem(item: com.example.data.model.SharedItemEntity) {}
        override suspend fun insertItems(items: List<com.example.data.model.SharedItemEntity>) {}
        override suspend fun deleteItem(item: com.example.data.model.SharedItemEntity) {}
        override suspend fun deleteItemById(id: String) {}
        override suspend fun deleteItemsForShare(shareId: String) {}
    }

    private fun nullMockLogDao(): com.example.data.dao.ServerLogDao = object : com.example.data.dao.ServerLogDao {
        override fun getRecentLogs() = kotlinx.coroutines.flow.emptyFlow<List<com.example.data.model.ServerLogEntity>>()
        override suspend fun insertLog(log: com.example.data.model.ServerLogEntity) {}
        override suspend fun clearLogs() {}
        override suspend fun getTotalLogCount() = 0L
        override suspend fun getTotalBytesTransferred() = 0L
    }
}
