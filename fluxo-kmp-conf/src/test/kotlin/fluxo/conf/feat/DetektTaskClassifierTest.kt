package fluxo.conf.feat

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class DetektTaskClassifierTest {

    @Test
    fun `metadata aggregate tasks keep their real platform`() {
        assertTask("detektMetadataCommonJsMain", DetectedTaskPlatform.JS, metadata = true)
        assertTask("detektMetadataCommonWasmMain", DetectedTaskPlatform.WASM, metadata = true)
        assertTask("detektMetadataNativeMain", DetectedTaskPlatform.NATIVE, metadata = true)
        assertTask("detektMetadataNonJvmMain", DetectedTaskPlatform.NON_JVM, metadata = true)
        assertTask("detektMetadataUnixMain", DetectedTaskPlatform.UNIX, metadata = true)
    }

    @Test
    fun `common and root Detekt tasks remain target-neutral`() {
        assertTask("detekt", null, main = false)
        assertTask("detektMain", null)
        assertTask("detektMetadataMain", null, metadata = true)
        assertTask("detektMetadataCommonMain", null, metadata = true)
    }

    @Test
    fun `platform source set tasks map to target families`() {
        assertTask("detektJvmMain", DetectedTaskPlatform.JVM)
        assertTask("detektAndroidDebug", DetectedTaskPlatform.ANDROID, main = false)
        assertTask("detektWasmJsMain", DetectedTaskPlatform.WASM)
        assertTask("detektWasmWasiMain", DetectedTaskPlatform.WASM)
        assertTask("detektExperimentalLatest", DetectedTaskPlatform.JVM, main = false)
        assertTask("detektIosSimulatorArm64Main", DetectedTaskPlatform.APPLE)
        assertTask("detektMingwX64Main", DetectedTaskPlatform.MINGW)
        assertTask("detektLinuxX64Test", DetectedTaskPlatform.LINUX, main = false, test = true)
    }

    @Test
    fun `unknown platform tasks fail closed instead of bypassing filters`() {
        assertTask("detektFuturePlatformMain", DetectedTaskPlatform.UNKNOWN)
    }

    private fun assertTask(
        name: String,
        platform: DetectedTaskPlatform?,
        main: Boolean = true,
        test: Boolean = false,
        metadata: Boolean = false,
    ) {
        val details = getTaskDetailsFromName(name)
        assertEquals(platform, details.platform, name)
        assertEquals(main, details.isMain, name)
        assertEquals(test, details.isTest, name)
        assertEquals(metadata, details.isMetadata, name)
    }
}
