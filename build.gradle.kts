import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

// Gradle-Version (gradle/wrapper): nicht neuer als die IntelliJ-Version, mit der das Projekt bearbeitet wird –
// IntelliJ 2026.2 übernimmt mit Gradle 9.8.x die Abhängigkeiten des main-Source-Sets nicht ins IDE-Modell.
group = "com.herreromarcos.idea"
version = "0.3.1"

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
    // OGNL-Parser in der Version, die Tapestry 4.1 verwendet (4.0: 2.6.7, gleiche Grammatik); ohne Abhängigkeiten
    implementation("ognl:ognl:2.6.9") {
        isTransitive = false
    }
    // Apache Commons Lang 3 liefert die IntelliJ-Plattform mit (lib/util-8.jar) – nicht ins Plugin packen
    compileOnly("org.apache.commons:commons-lang3:3.17.0")
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
        changeNotes = """
            <b>0.3.1</b>
            <ul>
              <li>Fixed possible NullPointerExceptions for <code>&lt;parameter&gt;</code> without name and classes
                  without modifier list</li>
              <li><i>New → Tapestry 4 Page / Component</i>: text and icon declared in plugin.xml, capitalised dialog titles</li>
              <li>Internal clean-up: shared caching, duplicated code removed</li>
            </ul>
            <b>0.3.0</b>
            <ul>
              <li>OGNL expressions are parsed with the original OGNL 2.6.9 parser: syntax errors are reported,
                  chains are resolved in operators, method arguments, indexes, lists and maps</li>
              <li>Element types after indexes (<code>users[0].name</code>), static members, OGNL pseudo-properties</li>
              <li>Binding prefixes are recognised only if registered (as in Tapestry); project-specific prefixes from
                  <code>tapestry.bindings.BindingFactories</code> are supported</li>
              <li>Configuration lookup like Tapestry: specification/<code>@Meta</code> → library or application →
                  web.xml init/context parameters → <code>hivemind.ApplicationDefaults</code></li>
              <li>Pages and components inside libraries use the library's <code>&lt;meta&gt;</code> values</li>
              <li>Application state objects, annotations, <code>validators:</code>, Tapestry 3.0 DTD and further
                  checks from the Tapestry 4.0/4.1 documentation</li>
            </ul>
            <b>0.2.0</b>
            <ul>
              <li>First release: templates, specifications, DTDs, navigation</li>
            </ul>
        """.trimIndent()
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
