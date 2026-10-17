plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

// Windows Java worker argument files can mis-decode non-ASCII classpath entries.
// Keep sources in the user's workspace, but give all generated classes an ASCII cache path.
if (System.getProperty("os.name").startsWith("Windows") && rootDir.absolutePath.any { it.code > 127 }) {
    val cacheRoot = gradle.gradleUserHomeDir.resolve("bead-pattern-build/${rootDir.absolutePath.hashCode().toUInt()}")
    allprojects { layout.buildDirectory.set(cacheRoot.resolve(name)) }
}

tasks.register<Copy>("stageDebugArtifacts") {
    dependsOn(":app:assembleDebug", ":app:assembleDebugAndroidTest")
    from(project(":app").layout.buildDirectory.dir("outputs/apk/debug")) { include("*.apk") }
    from(project(":app").layout.buildDirectory.dir("outputs/apk/androidTest/debug")) { include("*.apk") }
    into(layout.projectDirectory.dir("artifacts"))
}
tasks.register<Copy>("stageVerificationReports") {
    dependsOn(":app:testDebugUnitTest", ":app:lintDebug")
    from(project(":app").layout.buildDirectory.dir("reports"))
    into(layout.projectDirectory.dir("artifacts/reports"))
}
