package com.xiaoyu.app.di

import android.content.Context
import androidx.room.Room
import com.xiaoyu.app.data.backup.BackupRepository
import com.xiaoyu.app.data.db.DiaryDao
import com.xiaoyu.app.data.db.DraftDao
import com.xiaoyu.app.data.db.EmbeddingDao
import com.xiaoyu.app.data.db.FactCardDao
import com.xiaoyu.app.data.db.FavorabilityDao
import com.xiaoyu.app.data.db.IdentityDao
import com.xiaoyu.app.data.db.EmotionEventDao
import com.xiaoyu.app.data.db.CorrectionDao
import com.xiaoyu.app.data.db.LifeFragmentDao
import com.xiaoyu.app.data.db.MIGRATION_1_2
import com.xiaoyu.app.data.db.MIGRATION_2_3
import com.xiaoyu.app.data.db.MIGRATION_3_4
import com.xiaoyu.app.data.db.MIGRATION_4_5
import com.xiaoyu.app.data.db.MIGRATION_5_6
import com.xiaoyu.app.data.db.MIGRATION_6_7
import com.xiaoyu.app.data.db.MIGRATION_7_8
import com.xiaoyu.app.data.db.MIGRATION_8_9
import com.xiaoyu.app.data.db.MemoryDao
import com.xiaoyu.app.data.db.MessageDao
import com.xiaoyu.app.data.db.MoodDao
import com.xiaoyu.app.data.db.RequestSnapshotDao
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.db.SettingDao
import com.xiaoyu.app.data.db.StickerArchiveDao
import com.xiaoyu.app.data.db.StickerDao
import com.xiaoyu.app.data.db.StyleDao
import com.xiaoyu.app.data.db.XiaoyuDatabase
import com.xiaoyu.app.data.prompt.PromptAssetSource
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.prompt.PromptLibrary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): XiaoyuDatabase =
        Room.databaseBuilder(context, XiaoyuDatabase::class.java, "xiaoyu.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()

    @Provides
    fun sessionDao(db: XiaoyuDatabase): SessionDao = db.sessionDao()

    @Provides
    fun messageDao(db: XiaoyuDatabase): MessageDao = db.messageDao()

    @Provides
    fun stickerDao(db: XiaoyuDatabase): StickerDao = db.stickerDao()

    @Provides
    fun requestSnapshotDao(db: XiaoyuDatabase): RequestSnapshotDao = db.requestSnapshotDao()

    @Provides
    fun identityDao(db: XiaoyuDatabase): IdentityDao = db.identityDao()

    @Provides
    fun styleDao(db: XiaoyuDatabase): StyleDao = db.styleDao()

    @Provides
    fun memoryDao(db: XiaoyuDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun factCardDao(db: XiaoyuDatabase): FactCardDao = db.factCardDao()

    @Provides
    fun diaryDao(db: XiaoyuDatabase): DiaryDao = db.diaryDao()

    @Provides
    fun moodDao(db: XiaoyuDatabase): MoodDao = db.moodDao()

    @Provides
    fun favorabilityDao(db: XiaoyuDatabase): FavorabilityDao = db.favorabilityDao()

    @Provides
    fun lifeFragmentDao(db: XiaoyuDatabase): LifeFragmentDao = db.lifeFragmentDao()

    @Provides
    fun emotionEventDao(db: XiaoyuDatabase): EmotionEventDao = db.emotionEventDao()

    @Provides
    fun correctionDao(db: XiaoyuDatabase): CorrectionDao = db.correctionDao()

    @Provides
    fun settingDao(db: XiaoyuDatabase): SettingDao = db.settingDao()

    @Provides
    fun draftDao(db: XiaoyuDatabase): DraftDao = db.draftDao()

    @Provides
    fun embeddingDao(db: XiaoyuDatabase): EmbeddingDao = db.embeddingDao()

    @Provides
    fun stickerArchiveDao(db: XiaoyuDatabase): StickerArchiveDao = db.stickerArchiveDao()

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun promptLibrary(source: PromptAssetSource): PromptLibrary = PromptLibrary(source)

    @Provides
    @Singleton
    fun soulEngine(library: PromptLibrary): SoulEngine = SoulEngine(library)

    @Provides
    @Singleton
    fun backupRepository(
        @ApplicationContext context: Context,
        db: XiaoyuDatabase,
    ): BackupRepository = BackupRepository(context, db)
}
