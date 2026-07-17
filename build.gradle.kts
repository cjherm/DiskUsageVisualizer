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

javafx {
    version = "21.0.2"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("com.diskusage.App")
}

tasks.test {
    useJUnitPlatform()
}
