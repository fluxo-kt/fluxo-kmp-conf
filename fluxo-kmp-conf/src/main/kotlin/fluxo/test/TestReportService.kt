package fluxo.test

import fluxo.log.d
import fluxo.log.v
import java.util.Collections
import javax.annotation.concurrent.ThreadSafe
import org.gradle.api.logging.Logger
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

@ThreadSafe
internal abstract class TestReportService :
    BuildService<TestReportService.Parameters>,
    AutoCloseable {

    private val results = Collections.synchronizedList(ArrayList<TestReportResult>())

    val testResults: Array<TestReportResult>
        get() = synchronized(results) { results.toTypedArray() }

    @Volatile
    internal var logger: Logger? = null


    fun registerTestResult(
        result: TestReportResult,
        logger: Logger? = null,
    ) {
        if (logger != null && this.logger == null) {
            this.logger = logger
        }
        results += result
    }

    fun clear() {
        logger?.v("TestReportService clear")
        results.clear()
    }

    override fun close() {
        logger?.d("TestReportService close")

        clear()

        logger = null
    }


    interface Parameters : BuildServiceParameters

    internal companion object {
        internal const val NAME = "fluxoTestReportService"
    }
}
