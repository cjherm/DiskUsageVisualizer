plugins {
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "com.diskusage"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("com.diskusage.App")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Packages the app as a native Windows app-image (a folder with a double-clickable .exe " +
            "and a bundled runtime - no separate Java install needed on the target machine)."
    dependsOn("installDist")

    doFirst {
        val launcher = javaToolchains.launcherFor(java.toolchain).get()
        val jpackageExe = launcher.metadata.installationPath.file("bin/jpackage.exe").asFile

        val inputDir = layout.buildDirectory.dir("install/${project.name}/lib").get().asFile
        val destDir = layout.buildDirectory.dir("jpackage").get().asFile
        destDir.deleteRecursively()

        commandLine(
            jpackageExe.absolutePath,
            "--type", "app-image",
            "--input", inputDir.absolutePath,
            "--dest", destDir.absolutePath,
            "--name", "DiskUsageVisualizer",
            "--app-version", project.version.toString(),
            "--icon", file("icons/app-icon.ico").absolutePath,
            "--main-jar", "${project.name}-${project.version}.jar",
            "--main-class", "com.diskusage.App",
            "--java-options", "-Dfile.encoding=UTF-8",
            // The app isn't modularized, so its jars sit on the classpath - but JavaFX
            // itself must be resolved from the module path or the launcher fails with
            // "JavaFX runtime components are missing". $APPDIR is a jpackage token
            // resolved at launch time to the app's own install directory.
            "--java-options", "--module-path=\$APPDIR",
            "--java-options", "--add-modules=javafx.controls"
        )
    }

    doLast {
        println("App image created at: ${layout.buildDirectory.dir("jpackage/DiskUsageVisualizer").get().asFile.absolutePath}")
    }
}
