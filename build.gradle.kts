import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

// Gradle-Version (gradle/wrapper): nicht neuer als die IntelliJ-Version, mit der das Projekt bearbeitet wird –
// IntelliJ 2026.2 übernimmt mit Gradle 9.8.x die Abhängigkeiten des main-Source-Sets nicht ins IDE-Modell.
group = "com.herreromarcos.idea"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    // Keine GUI-Designer-Formulare → Bytecode-Instrumentierung unnötig
    instrumentCode = false

    pluginConfiguration {
        id = "com.herreromarcos.idea.tapestry4plugin"
        name = "Tapestry 4 Support"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create("IC", "2024.3.5")
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // Das Plugin enthält die Apache-Tapestry-DTDs → LICENSE und NOTICE gehören ins JAR
    processResources {
        from(files("LICENSE", "NOTICE")) {
            into("META-INF")
        }
    }
}
