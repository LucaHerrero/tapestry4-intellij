# Tapestry 4 Support – IntelliJ IDEA Plugin

Unterstützung für Apache Tapestry 4.0/4.1 in IntelliJ IDEA **2024.3.5** (Build 243, Community & Ultimate; `sinceBuild=243`, kein `untilBuild`).

## Installation

```
gradlew buildPlugin
```
→ `build/distributions/tapestry4-intellij-0.3.0.zip`, dann in IntelliJ: *Settings → Plugins → ⚙ → Install Plugin from Disk…*

Zum Ausprobieren in einer Sandbox-IDE: `gradlew runIde`. Tests: `gradlew test`. Kompatibilitätsprüfung: `gradlew verifyPlugin`.

## Funktionen

### HTML-Templates
| Was | Beispiel | Funktion |
|---|---|---|
| `jwcid` ist ein bekanntes Attribut | `<span jwcid="...">` | keine „Unknown attribute“-Warnung |
| deklarierte Komponente | `jwcid="greeting"` | Strg+Klick → `<component id="greeting">` in .page/.jwc bzw. `@Component`-Methode; Fehler, wenn nicht deklariert |
| implizite Komponente | `jwcid="@Insert"`, `jwcid="name@contrib:Table"` | Strg+Klick → .jwc; Completion aller Typen |
| Komponentenparameter | `<span jwcid="@Insert" value="…">` | Parameter als Attribut-Completion, Navigation zu `<parameter>`; Warnung bei fehlenden Pflichtparametern |
| `ognl:` | `value="ognl:user.address.city"` | jedes Segment navigierbar (Getter/Feld/`<property>`), Completion, Rename-fest |
| `listener:` | `listener="listener:onSave"` | → Methode der Seitenklasse |
| `message:` | `value="message:title"` | → Key in `Seite.properties` / `Seite_de.properties` / `<app>.properties` |
| `asset:` / `bean:` / `component:` | `src="asset:logo"` | → `<asset>` / `<bean>` / `<component>` der Spezifikation |
| PageLink | `page="Home"` | → Seite |
| `validators:` | `validators:required,email[%email-format],$myValidator` | `$name` → `<bean>`, `[%key]` → Message-Key |
| Lokalisierungs-Direktive | `<span key="hello" raw="true">` | → Message-Key; `key`/`raw` sind bekannte Attribute |
| Prüfungen | | doppelte bzw. ungültige ids, Komponenten in `$remove$` oder im verworfenen Body (`allow-body="no"`), Parameter doppelt in Spezifikation und Template gebunden, informelle Parameter bei `allow-informal-parameters="no"`, reservierte Namen, veraltete Komponenten/Parameter/Aliase |

Das Komponenten-Attribut heißt `jwcid`, sofern nicht über `org.apache.tapestry.jwcid-attribute-name` geändert.

### Java-Annotationen
`@InjectPage("Details")` → Seite, `@InjectComponent("x")` und `@Component(copyOf = "x")` → Komponente, `@Component(type = "TextField")` → Komponententyp, `@Component(bindings = {"value=ognl:x"})` und `@InitialValue("…")` → Binding-Ausdruck (Standard OGNL), `@InjectAsset("x")` → Asset, `@Message("key")` → Message-Key, `@InjectScript("x.script")` und `@Asset("…")` → Datei relativ zur Spezifikation bzw. zum Template – jeweils mit Navigation, Completion und Fehlermeldung bei unbekannten Zielen.

### Spezifikationen (.page, .jwc, .application, .library)
- werden als XML erkannt, eigene Icons
- Original-DTDs `Tapestry_3_0.dtd`, `Tapestry_4_0.dtd` und `Tapestry_4_1.dtd` (unverändert aus `tapestry-framework-4.1.6.jar`, `org/apache/tapestry/parse/`, Apache License 2.0) → Tag-/Attribut-Completion und Validierung ohne Netzwerkzugriff, zugeordnet über Public ID bzw. System-URL im DOCTYPE
- `component@type`, `copy-of`, `binding@name` (→ Parameter), `binding@value`/`set@value`/`property@initial-value`/`parameter@default-value` (OGNL als Standard, umstellbar über `org.apache.tapestry.default-binding-prefix`), `class`-Attribute, `<bean class>` inkl. „lightweight initialization“ und `bean-class-packages`, `specification-path`, `asset@path` (`context:`/`classpath:`, URLs bleiben unberührt), `inject type="page"`/`type="script"`
- 3.0-Elemente (`<property-specification>`, `<static-binding>`, `<message-binding>` …), da Tapestry 4 die 3.0-DTD weiterhin unterstützt
- Eigenschaften, die Tapestry aus der Spezifikation erzeugt (`<parameter>`, `<property>`, `property`-Attribute von `<inject>`/`<bean>`/`<asset>`/`<component>`), sind für OGNL bekannt – ebenso Eigenschaften mit nur einem Setter

### OGNL
Ausdrücke werden mit dem Original-OGNL-Parser (OGNL 2.6.9, wie in Tapestry 4.1; 4.0 nutzt 2.6.7 mit gleicher Grammatik) zerlegt:
- Syntaxfehler werden an der Fehlerstelle gemeldet (`Invalid OGNL expression: unexpected …`)
- alle Eigenschafts-/Methodenketten werden aufgelöst – auch in Operatoren (`a > b ? c : d`), Methodenargumenten, Indizes, Listen und Maps; `#root.x` bezieht sich auf die Seite
- nach Indizes gilt der Elementtyp (`users[0].name` bei `List<User>`, Arrays, Map-Werte)
- statische Zugriffe `@com.x.Util@FIELD`, `@Klasse@methode()`, `@@max(…)` (java.lang.Math); Klassennamen aus `new`, `instanceof` und `@Klasse@` sind navigierbar
- OGNL-Pseudo-Properties wie `list.size`, `list.isEmpty`, `map.keys`, `map.values`, `iterator.next`
- Projektionen/Selektionen (`users.{name}`, `users.{? active}`) und Variablen (`#this`, `#var`) werden gegen unbekannte Elemente ausgewertet – dort wird nicht geprüft

### Binding-Präfixe
Wie in Tapestrys `BindingSourceImpl` ist `name:` nur dann ein Präfix, wenn es in `tapestry.bindings.BindingFactories` registriert ist. Projekteigene Präfixe (`<binding prefix="spring" …/>` in einer hivemodule.xml) werden erkannt, in der Completion angeboten und navigieren zur Registrierung; ihr Ausdruck wird nicht ausgewertet. Ein nicht registriertes Präfix gehört zum Ausdruck: im Template ist `mailto:x` ein Literal, in Spezifikationen ergäbe es einen ungültigen OGNL-Ausdruck und wird gemeldet.

### Script-Dateien (.script)
- `.script` wird als XML erkannt
- Original-DTDs `Script_3_0.dtd` und `Script_4_0.dtd` (unverändert aus `tapestry-framework-4.1.6.jar`, `org/apache/tapestry/script/`) → Completion und Validierung, zugeordnet über den DOCTYPE
- `<include-script resource-path="/…">` → Datei im Classpath

### Navigation
- Gutter-Icon an Seiten-/Komponentenklassen → .page/.jwc + Template
- Gutter-Icon am Wurzel-Tag der Spezifikation → Template + Klasse; an `<component>` → Verwendung im Template
- *Navigate → Related Symbol* (Strg+Alt+Pos1) zwischen Template, Spezifikation und Klasse
- *New → Tapestry 4 Page / Component* legt Spezifikation + Template an

## Wie Dinge gefunden werden
- **Template ↔ Spezifikation**: gleicher Dateiname (`Home.html` ↔ `Home.page`/`Home.jwc`); bei mehreren Treffern gewinnt der ähnlichste Pfad (WEB-INF wird ignoriert).
- **Klasse** (laut „Determining the Page Class“): `class`-Attribut, sonst `page-class-packages` / `component-class-packages` mit dem Seitennamen inkl. Ordnern (`admin/EditUser` → `admin.EditUser`), dann das Default-Paket, dann `org.apache.tapestry.default-page-class`, sonst `BasePage`/`BaseComponent`. Lokalisierte Templates (`Home_de.html`) gehören zu `Home.page`.
- **Message-Kataloge**: `<Name>.properties` (+ Locale-Varianten) neben Spezifikation/Template, `<app>.properties` neben der .application sowie `WEB-INF/<servlet-name>.properties` (Servlet-Name aus `web.xml`).
- **Konfiguration** (Meta-Werte wie `page-class-packages`, `jwcid-attribute-name`, `template-extension`, `default-binding-prefix`), Suchpfad wie `tapestry.props.ComponentPropertySource`: `<meta>` der Spezifikation bzw. `@Meta` der Klasse → `<meta>` des Namespace (die .library, zu der die Seite/Komponente gehört, sonst die .application) → global: Servlet-`<init-param>`, `<context-param>` (web.xml), `hivemind.ApplicationDefaults`.
- **Komponententypen**: `component-type` aus .application/.library, eingebundene `<library id="…">` (Präfix `id:`, inkl. .jwc-Dateien im Ordner der Library-Spezifikation), lose .jwc-Dateien im Projekt, `org/apache/tapestry/Framework.library` aus dem Classpath (auch als `framework:`).
- **Message-Kataloge von Bibliotheken**: `<lib>.properties` neben der .library, für Seiten/Komponenten in deren Ordner.

## Abgleich mit der Dokumentation
Geprüft gegen die offizielle Dokumentation von Tapestry 4.0.2 (User's Guide, Annotations- und Komponenten-Referenz) und die Änderungen in 4.1. Die Regeln sind in `DocumentationComplianceTest` und `AnnotationsAndRulesTest` abgesichert.

| Kapitel | Regel | Stand |
|---|---|---|
| Component Bindings | Präfixe `asset, bean, clientId, component, hivemind, listener, literal, message, meta, ognl, state, translator, validator(s)` plus projekteigene aus `BindingFactories`; ohne (registriertes) Präfix: Template = Literal, Spezifikation/Annotation = OGNL | ✅ |
| Configuring Tapestry | `default-binding-prefix`, `page-`/`component-`/`bean-class-packages`, `default-page-class`, `jwcid-attribute-name`, `template-extension`; Suchpfad Spezifikation → Namespace (Library/Application) → web.xml → `hivemind.ApplicationDefaults` | ✅ (ohne JVM-System-Properties) |
| Specification DTDs | 3.0/4.0/4.1-DTD, boolesche Werte (`yes/on/1/aye` …), Property-Injection über `property`-Attribute, `default-value` als Binding-Referenz, `deprecated`, `aliases`, `reserved-parameter`, Asset-Präfixe `context:`/`classpath:`/URL | ✅ |
| Templates | `jwcid`, `@Type`, `id@Type`, `$content$`, `$remove$`, `<span key>`, eindeutige ids, Body-Regeln, Konflikt Spezifikation/Template, informelle/reservierte Parameter | ✅ |
| Components | Suche: `component-type`, Ordner, Bibliotheken (inkl. Ordner der Library), Framework-Namespace | ✅ |
| Page Class | Klassensuche mit Ordnern, Paketen, Default-Paket, `default-page-class` – in Bibliotheken mit den `<meta>`-Werten der .library | ✅ |
| Localization | Kataloge mit Locale-Varianten, Namespace-Katalog (Anwendung, Servlet-Name, Bibliothek), lokalisierte Templates | ✅ |
| Listener Methods | nur `public`-Instanzmethoden | ✅ |
| State / Properties | `<property>`, abstrakte Getter **oder** Setter | ✅ |
| Annotations | `@Component`, `@Parameter`, `@Asset`, `@Bean`, `@ComponentClass`, `@Meta`, `@InjectPage/-Component/-Asset/-Script`, `@Message`, `@InitialValue` | ✅ |
| Input Validation | `validators:` mit `$bean` und `[%key]` | ✅ |
| Script DTDs | Script-DTDs 3.0/4.0, `include-script` | ✅ |
| State | Application State Objects: `state:visit`, `<inject type="state">` bzw. `type="state-flag"`, `@InjectState`/`@InjectStateFlag` → `<state-object name>` in hivemodule.xml (`ApplicationObjects` überschreibt `FactoryObjects`) | ✅ |
| HiveMind | Objekt-Referenzen (`service:`, `configuration:` …), `@InjectObject`, Präfix `hivemind:` | ➖ bewusst im separaten HiveMind-Plugin |
| Friendly URLs, Events | betreffen Laufzeit-Konfiguration | ➖ nicht relevant |

## Grenzen / bewusste Entscheidungen
- Unbekannte OGNL-Eigenschaften und Syntaxfehler sind *Warnungen*, keine Fehler; bei Maps, nicht auflösbaren Oberklassen, in Projektionen/Selektionen und hinter Variablen wird nicht geprüft.
- Liegt die Tapestry-JAR nicht im Classpath, werden Framework-Komponenten (Insert, For, …) nicht als unbekannt markiert, aber auch nicht aufgelöst.
- Die Original-DTDs sind streng: z.B. muss `<description>` als erstes Kindelement stehen, und `component@id` ist vom Typ `ID` (eindeutig in der Datei).
- Dateien ohne DOCTYPE bekommen keine DTD-Validierung (die Plugin-Referenzen funktionieren trotzdem).
- Templates müssen in IntelliJ als HTML/XHTML erkannt werden. Eine per `org.apache.tapestry.template-extension` konfigurierte Endung (z.B. `.wml`) ggf. unter *Settings → Editor → File Types* dem HTML-Dateityp zuordnen.
- JVM-System-Properties (`-Dorg.apache.tapestry…`) gibt es nur zur Laufzeit; sie fließen nicht in die Konfiguration ein. `hivemind.FactoryDefaults` enthält Tapestrys Standardwerte, die das Plugin ohnehin verwendet.
- Was ein projekteigenes Binding-Präfix mit seinem Ausdruck macht, bestimmt dessen BindingFactory – das Plugin prüft den Ausdruck daher nicht.

## Lizenz

Apache License 2.0 – siehe [LICENSE](LICENSE). Die mitgelieferten DTDs stammen unverändert aus Apache Tapestry 4.1.6 (ebenfalls Apache License 2.0); das Plugin bündelt OGNL 2.6.9 (BSD-Lizenz). Details in [NOTICE](NOTICE).

## Releases

Jeder Push auf `master` startet den Workflow [`.github/workflows/release.yml`](.github/workflows/release.yml): Tests, `buildPlugin` und Plugin Verifier laufen, danach wird die ZIP als GitHub Release `v<version>` veröffentlicht. Die Version kommt aus `build.gradle.kts`. Gibt es das Release zu dieser Version schon, wird nur gebaut und geprüft – für ein neues Release also vorher die Version erhöhen.

## Entwicklung

**JDK:** Gebaut wird mit einer Java-21-Toolchain. Ist lokal kein JDK 21 vorhanden, lädt Gradle es über den Foojay-Resolver automatisch herunter.

**Gradle-Version:** Der Wrapper steht bewusst auf **Gradle 9.7.1**. Mit Gradle 9.8.x (neuer als IntelliJ 2026.2.3) übernimmt der IntelliJ-Sync die Abhängigkeiten des `main`-Source-Sets nicht ins IDE-Modell – der Kommandozeilen-Build funktioniert, im Editor sind aber alle `com.intellij.*`-Imports rot. Gradle erst aktualisieren, wenn die IDE nachgezogen hat.

**Code-Stil:** Meldungstexte als Format-Strings (`"Unknown page '%s'".formatted(name)`); `final` an Feldern, Parametern und lokalen Variablen, sofern nicht neu zugewiesen; kein `final` an Klassen.

**Abhängigkeiten:** Allgemeine Funktionen kommen aus Bibliotheken statt aus eigenem Code – OGNL-Parser aus `ognl:ognl:2.6.9` (wird ins Plugin gepackt), String-Hilfen aus Apache Commons Lang 3 (liefert die IntelliJ-Plattform mit, daher nur `compileOnly`), Bezeichner-Prüfung über `javax.lang.model.SourceVersion`.

### Aufbau (`com.herreromarcos.idea.tapestry4plugin`)
| Paket | Inhalt |
|---|---|
| `model` | **`TapestryModel`** (Template ↔ Spezifikation ↔ Klasse, `TapestryContext`), **`TapestryConfiguration`** (Meta-Suchpfad: Spezifikation → Namespace → global), **`ComponentModel`** (deklarierte Komponenten, Parameter), **`TapestryRegistry`** (Komponententypen, Seiten, Bibliotheks-Namespaces), **`HiveModules`** (Beiträge aller hivemodule.xml) mit **`ApplicationStateObjects`** und **`BindingPrefixes`**, **`WebXml`**, Parser **`Jwcid`** / **`BindingExpression`** / **`OgnlExpression`** (auf Basis des OGNL-Parsers), Helfer `TapestryFiles`, `TapestryPaths`, `SpecXml`, `JavaClasses` |
| `references` | `TemplateReferenceProvider`, `SpecReferenceProvider`, `AnnotationReferenceProvider`, `ScriptReferenceProvider`, `SpecPathReferences`, `BindingReferences` und je eine Referenzklasse pro Ziel (Komponente, Typ, Parameter, OGNL, Listener, Message, Asset/Bean, Seite, State Object, Bean-Klasse) |
| `highlighting` | `TapestryAnnotator` (unaufgelöste Referenzen, Präfix-Hervorhebung) mit den Regeln in `TemplateChecks`, `SpecificationChecks` und `ComponentUsageChecks`; Completion der Binding-Präfixe |
| `html` | Attribut-Deskriptoren für `jwcid` und Komponentenparameter |
| `navigation` | Gutter-Icons, Go to Related, Icons |
| `dtd`, `actions` | DTD-Registrierung, „New → Tapestry 4 Page/Component“ |

Jede Referenz entscheidet selbst, ob und wie streng sie unaufgelöst gemeldet wird (`TapestryReference#getUnresolvedSeverity`); der Annotator wertet das nur aus.

Tests: `TemplateSupportTest`, `SpecificationSupportTest`, `OgnlPropertyTest`, `AnnotationsAndRulesTest`, `DocumentationComplianceTest`, `StateObjectsTest`, `ConfigurationSourcesTest`, `BindingPrefixAndOgnlTest`, `ReviewFindingsTest` (IDE-Fixture, gemeinsame Basis `TapestryTestCase` mit Stubs und Hilfsmethoden) sowie `ParserTest` und `OgnlExpressionTest` (reine Unit-Tests).

Nach Änderungen an Ressourcen (z.B. DTDs) bei merkwürdigen Testergebnissen die Sandbox löschen: `.intellijPlatform/sandbox`. Der VFS-Cache der Test-IDE erkennt geänderte Plugin-JARs wegen der reproduzierbaren Zeitstempel nicht immer.
