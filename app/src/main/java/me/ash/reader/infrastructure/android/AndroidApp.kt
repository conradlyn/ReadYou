package me.ash.reader.infrastructure.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.Coil
import coil.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.BuildConfig
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.AppService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.ui.ext.del
import me.ash.reader.ui.ext.getLatestApk
import me.ash.reader.ui.ext.isGitHub
import timber.log.Timber

/** The Application class, where the Dagger components is generated. */
@HiltAndroidApp
class AndroidApp : Application(), Configuration.Provider {

    /**
     * From: [Feeder](https://gitlab.com/spacecowboy/Feeder).
     *
     * Install Conscrypt to handle TLSv1.3 pre Android10.
     */
    init {
        // Cancel TLSv1.3 support pre Android10
        // Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var appService: AppService

    @Inject lateinit var accountService: AccountService

    @Inject lateinit var rssService: RssService

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    @Inject @IODispatcher lateinit var ioDispatcher: CoroutineDispatcher

    @Inject lateinit var imageLoader: ImageLoader

    /**
     * 注意：本字段不直接引用，但**必须保留** —— 它的 `init{}` 订阅了 `currentAccountFlow`，
     * 靠这个注入才会产生副作用。
     *
     * 其余未使用的 `lateinit var` 注入已移除：Hilt 的字段注入会在 Application 创建时
     * 把所有字段全部实例化，全部落在冷启动路径、主线程上。
     */
    @Inject lateinit var diffMapHolder: DiffMapHolder

    /**
     * When the application startup.
     * 1. Set the uncaught exception handler
     * 2. Initialize the default account if there is none
     * 3. Synchronize once
     * 4. Check for new version
     */
    override fun onCreate() {
        super.onCreate()
        CrashHandler(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        applicationScope.launch {
            accountInit()
            workerInit()
            checkUpdate()
        }
        Coil.setImageLoader(imageLoader)
    }

    /** Override the [Configuration.Builder] to provide the [HiltWorkerFactory]. */
    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setWorkerCoroutineContext(Dispatchers.IO)
                .setMinimumLoggingLevel(
                    if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN
                )
                .build()

    private suspend fun accountInit() {
        withContext(ioDispatcher) {
            if (accountService.isNoAccount()) {
                launch { accountService.initWithDefaultAccount() }
                    .invokeOnCompletion {
                        rssService.get().doSyncOneTime(accountService.getCurrentAccountId())
                    }
            }
        }
    }

    private suspend fun workerInit() {
        rssService.get().initSync()
    }

    private suspend fun checkUpdate() {
        if (!isGitHub) return
        withContext(ioDispatcher) {
            applicationContext.getLatestApk().let { if (it.exists()) it.del() }
        }
        appService.checkUpdate(showToast = false)
    }
}
