package fluxo.test

import fluxo.conf.impl.l
import fluxo.conf.impl.v
import javax.annotation.concurrent.ThreadSafe
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ListProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

@ThreadSafe
internal abstract class TestReportService : BuildService<TestReportService.Parameters>,
    AutoCloseable {

    val testResults: Array<TestReportResult>
        get() = parameters.testResults.get().toTypedArray()

    @Volatile
    internal var logger: Logger? = null


    fun registerTestResult(
        result: TestReportResult,
        logger: Logger? = null,
    ) {
        if (logger != null && this.logger == null) {
            this.logger = logger
        }
        parameters.testResults.add(result)
    }

    fun clear() {
        logger?.v("TestReportService clear")
        parameters.testResults.empty()
    }

    override fun close() {
        logger?.l("TestReportService close")

        clear()

        logger = null
    }


    interface Parameters : BuildServiceParameters {
        val testResults: ListProperty<TestReportResult>
    }

    internal companion object {
        internal const val NAME = "fluxoTestReportService"
    }
}
