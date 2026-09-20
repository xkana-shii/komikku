package eu.kanade.tachiyomi.ui.more

import androidx.work.WorkInfo
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.model.ScreenModelStore
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, InternalVoyagerApi::class)
class NewUpdateScreenModelTest {
    private val url = "https://example.invalid/update.apk"
    private fun work(state: WorkInfo.State, percent: Int = 0, uri: String? = null) = mockk<WorkInfo>(relaxed = true) {
        every { tags } returns setOf(AppUpdateDownloadJob.urlTag(url))
        every { this@mockk.state } returns state
        every { progress.getInt(AppUpdateDownloadJob.PROGRESS, 0) } returns percent
        every { outputData.getString(AppUpdateDownloadJob.EXTRA_FILE_URI) } returns uri
        every { outputData.getString(AppUpdateDownloadJob.ERROR) } returns "Failed request"
    }

    @Test
    fun `download progress retry install and duplicate tap handling follow worker state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val works = MutableStateFlow(emptyList<WorkInfo>())
        var starts = 0
        val installs = mutableListOf<String>()
        val model = ScreenModelStore.getOrPut("update-test", null) { NewUpdateScreenModel(url, "version", mockk(), works, { starts++ }, installs::add) }
        try {
            testScheduler.runCurrent()
            model.state.value.stage shouldBe NewUpdateScreenModel.Stage.Available
            repeat(2) { model.accept() }
            starts shouldBe 1
            works.value = listOf(work(WorkInfo.State.RUNNING, 42))
            testScheduler.runCurrent()
            model.state.value.progress shouldBe 42
            works.value = listOf(work(WorkInfo.State.FAILED))
            testScheduler.runCurrent()
            model.state.value.stage shouldBe NewUpdateScreenModel.Stage.Failed
            model.accept()
            starts shouldBe 2
            works.value = listOf(work(WorkInfo.State.SUCCEEDED, uri = "content://apk"))
            testScheduler.runCurrent()
            model.state.value.stage shouldBe NewUpdateScreenModel.Stage.Downloaded
            model.accept()
            installs shouldBe listOf("content://apk")
            starts shouldBe 2
        } finally {
            ScreenModelStore.onDisposeNavigator("update-test")
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `reopened prompt observes existing work and missing APK offers retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val works = MutableStateFlow(listOf(work(WorkInfo.State.RUNNING, 55)))
        var starts = 0
        val model = ScreenModelStore.getOrPut("update-test", null) { NewUpdateScreenModel(url, "version", mockk(), works, { starts++ }, { error("APK missing") }) }
        try {
            model.accept()
            testScheduler.runCurrent()
            model.accept()
            starts shouldBe 0
            model.state.value.progress shouldBe 55
            works.value = listOf(work(WorkInfo.State.SUCCEEDED, uri = "content://missing"))
            testScheduler.runCurrent()
            model.accept()
            model.state.value.stage shouldBe NewUpdateScreenModel.Stage.Failed
            model.accept()
            starts shouldBe 1
        } finally {
            ScreenModelStore.onDisposeNavigator("update-test")
            Dispatchers.resetMain()
        }
    }
}
