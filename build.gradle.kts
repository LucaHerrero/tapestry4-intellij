import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

// Gradle-Version (gradle/wrapper): nicht neuer als die IntelliJ-Version, mit der das Projekt bearbeitet wird –
// IntelliJ 2026.2 übernimmt mit Gradle 9.8.x die Abhängigkeiten des main-Source-Sets nicht ins IDE-Modell.
group = "com.herreromarcos.idea"
version = "0.5.0"

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
    compileOnly("org.apache.commons:commons-lang3:3.18.0")
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
        name = "Tapestry 4"
        version = project.version.toString()
        changeNotes = """
            <b>0.5.0</b>
            <ul>
              <li>Checks verified against a running Tapestry 4.1.6 application: every new error is one Tapestry throws
                  when loading the page</li>
              <li>Specifications: <code>&lt;component&gt;</code> with both <code>type</code> and <code>copy-of</code>,
                  <code>copy-of</code> of a component declared later, required parameters with a default value
                  (also <code>@Parameter</code>), <code>inherit-informal-parameters</code> with components or containers
                  that allow only formal parameters, a parameter bound under its name and an alias</li>
              <li>Duplicate parameters (including aliases), assets, beans and properties, properties created by two
                  elements; duplicate pages, component types, libraries and extensions in applications and libraries</li>
              <li>Name rules of Tapestry's parser for properties, bindings, pages, component types, library ids and
                  extensions; library ids <code>framework</code> and <code>application</code> are reserved</li>
              <li>Templates: implicit ids already declared in the specification or class, a parameter bound twice via
                  name and alias; reserved parameters bound to an expression and unbound required parameters are now
                  errors, as in Tapestry</li>
              <li>Fixed: possible hang with cyclic class inheritance, file type detection of upper-case extensions
                  under a Turkish locale, <code>&lt;property&gt;</code> of the 3.0 DTD offered as page property,
                  page references of PageLinks copied via <code>copy-of</code></li>
            </ul>
            <b>0.4.0</b>
            <ul>
              <li>Performance: caches are no longer discarded on every keystroke, only when specifications,
                  configuration, the file structure or Java classes change – fixed problems disappear immediately</li>
              <li>OGNL finds getters like OGNL itself: <code>isActive()</code> also with return type <code>Boolean</code></li>
              <li>Components declared in the template (<code>jwcid="field@TextField"</code>) are valid targets of
                  <code>component:</code>, <code>clientId:</code>, <code>@InjectComponent</code> and
                  <code>@EventListener(targets)</code></li>
              <li><code>@Component</code> without <code>type</code> uses the return type</li>
              <li>Meta keys: navigation and completion for <code>meta:</code>, <code>&lt;inject type="meta"&gt;</code>
                  and <code>@InjectMeta</code></li>
              <li><code>@Message</code>, <code>@InjectState</code> and <code>@InjectStateFlag</code> without value are
                  checked against the key derived from the method name</li>
              <li>Specifications: <code>&lt;component&gt;</code> needs <code>type</code> or <code>copy-of</code>;
                  parameter, bean and asset names must be Java identifiers</li>
              <li>Configuration as documented in the Tapestry 4.1 User's Guide: <code>template-extension</code> and
                  <code>*-class-packages</code> only from specification, library or application;
                  <code>namespace-properties-name</code> is supported</li>
            </ul>
            <b>0.3.3</b>
            <ul>
              <li>.page, .jwc, .application, .library and .script files are treated as XML only if they are Tapestry
                  specifications (detected by their root element); other files with these extensions keep their type</li>
              <li>Plugin logo in 40×40 with a dark variant</li>
              <li>Plugin renamed to "Tapestry 4"; vendor e-mail and website added</li>
            </ul>
            <b>0.3.2</b>
            <ul>
              <li>No longer uses the deprecated <code>StringUtils.removeStart</code> of Apache Commons Lang</li>
            </ul>
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

    // Leistungs- und Lasttest (LargeProjectPerformanceTest) nur auf Anfrage:
    // gradlew test --tests *LargeProjectPerformanceTest -Pperformance [-Pperformance.pages=6000]
    test {
        val performance = providers.gradleProperty("performance")
        systemProperty("tapestry.performance", performance.isPresent)
        providers.gradleProperty("performance.pages").orNull?.let { systemProperty("tapestry.performance.pages", it) }
        if (performance.isPresent) {
            maxHeapSize = "4g"
            jvmArgs("-XX:StartFlightRecording=filename=${layout.buildDirectory.get().asFile}/performance.jfr,settings=profile")
        }
    }

    // Das Plugin enthält die Apache-Tapestry-DTDs → LICENSE und NOTICE gehören ins JAR
    processResources {
        from(files("LICENSE", "NOTICE")) {
            into("META-INF")
        }
    }
}
