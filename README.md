# Tapestry 4 Support – IntelliJ IDEA Plugin

Unterstützung für Apache Tapestry 4.0/4.1 in IntelliJ IDEA **2024.3.5** (Build 243, Community & Ultimate; `sinceBuild=243`, kein `untilBuild`).

## Installation

```
gradlew buildPlugin
```
→ `build/distributions/tapestry4-intellij-0.2.0.zip`, dann in IntelliJ: *Settings → Plugins → ⚙ → Install Plugin from Disk…*

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
| doppelte ids | `jwcid="x@Insert"` 2× | Fehler |

### Spezifikationen (.page, .jwc, .application, .library)
- werden als XML erkannt, eigene Icons
- Original-DTDs `Tapestry_4_0.dtd` und `Tapestry_4_1.dtd` (unverändert aus `tapestry-framework-4.1.6.jar`, `org/apache/tapestry/parse/`, Apache License 2.0) → Tag-/Attribut-Completion und Validierung ohne Netzwerkzugriff, zugeordnet über Public ID bzw. System-URL im DOCTYPE
- `component@type`, `copy-of`, `binding@name` (→ Parameter), `binding@value` (OGNL als Standard), `class`-Attribute, `specification-path`, `asset@path` (`context:`/`classpath:`), `inject type="page"`

### Script-Dateien (.script)
- `.script` wird als XML erkannt
- Original-DTDs `Script_3_0.dtd` und `Script_4_0.dtd` (unverändert aus `tapestry-framework-4.1.6.jar`, `org/apache/tapestry/script/`) → Completion und Validierung, zugeordnet über den DOCTYPE

### Navigation
- Gutter-Icon an Seiten-/Komponentenklassen → .page/.jwc + Template
- Gutter-Icon am Wurzel-Tag der Spezifikation → Template + Klasse; an `<component>` → Verwendung im Template
- *Navigate → Related Symbol* (Strg+Alt+Pos1) zwischen Template, Spezifikation und Klasse
- *New → Tapestry 4 Page / Component* legt Spezifikation + Template an

## Wie Dinge gefunden werden
- **Template ↔ Spezifikation**: gleicher Dateiname (`Home.html` ↔ `Home.page`/`Home.jwc`); bei mehreren Treffern gewinnt der ähnlichste Pfad (WEB-INF wird ignoriert).
- **Klasse**: `class`-Attribut, sonst `org.apache.tapestry.page-class-packages` / `component-class-packages` aus der .application, sonst `BasePage`/`BaseComponent`.
- **Komponententypen**: `component-type` aus .application/.library, eingebundene `<library id="…">` (Präfix `id:`), lose .jwc-Dateien im Projekt, `org/apache/tapestry/Framework.library` aus dem Classpath (auch als `framework:`).

## Grenzen / bewusste Entscheidungen
- OGNL wird nur für einfache Eigenschafts-/Methodenketten ausgewertet; komplexe Ausdrücke werden ab der ersten unbekannten Stelle ignoriert. Unbekannte Eigenschaften sind *Warnungen*, keine Fehler; bei Maps oder nicht auflösbaren Oberklassen wird nicht geprüft.
- Liegt die Tapestry-JAR nicht im Classpath, werden Framework-Komponenten (Insert, For, …) nicht als unbekannt markiert, aber auch nicht aufgelöst.
- Die Original-DTDs sind streng: z.B. muss `<description>` als erstes Kindelement stehen, und `component@id` ist vom Typ `ID` (eindeutig in der Datei).
- Dateien ohne DOCTYPE bekommen keine DTD-Validierung (die Plugin-Referenzen funktionieren trotzdem).

## Lizenz

Apache License 2.0 – siehe [LICENSE](LICENSE). Die mitgelieferten DTDs stammen unverändert aus Apache Tapestry 4.1.6 (ebenfalls Apache License 2.0), Details in [NOTICE](NOTICE).

## Entwicklung

**JDK:** Gebaut wird mit einer Java-21-Toolchain. Ist lokal kein JDK 21 vorhanden, lädt Gradle es über den Foojay-Resolver automatisch herunter.

**Gradle-Version:** Der Wrapper steht bewusst auf **Gradle 9.7.1**. Mit Gradle 9.8.x (neuer als IntelliJ 2026.2.3) übernimmt der IntelliJ-Sync die Abhängigkeiten des `main`-Source-Sets nicht ins IDE-Modell – der Kommandozeilen-Build funktioniert, im Editor sind aber alle `com.intellij.*`-Imports rot. Gradle erst aktualisieren, wenn die IDE nachgezogen hat.

**Code-Stil:** Meldungstexte als Format-Strings (`"Unknown page '%s'".formatted(name)`); `final` an Feldern, Parametern und lokalen Variablen, sofern nicht neu zugewiesen; kein `final` an Klassen.

### Aufbau (`com.herreromarcos.idea.tapestry4plugin`)
| Paket | Inhalt |
|---|---|
| `model` | **`TapestryModel`** (Template ↔ Spezifikation ↔ Klasse, `TapestryContext`), **`ComponentModel`** (deklarierte Komponenten, Parameter), **`TapestryRegistry`** (Komponententypen, Seiten), Parser **`Jwcid`** / **`BindingExpression`**, Helfer `TapestryFiles`, `TapestryPaths`, `SpecXml`, `JavaClasses` |
| `references` | `TemplateReferenceProvider`, `SpecReferenceProvider`, `SpecPathReferences` und je eine Referenzklasse pro Ziel (Komponente, Typ, Parameter, OGNL, Listener, Message, Asset/Bean, Seite) |
| `highlighting` | Annotator (Fehler/Warnungen) und Completion der Binding-Präfixe |
| `html` | Attribut-Deskriptoren für `jwcid` und Komponentenparameter |
| `navigation` | Gutter-Icons, Go to Related, Icons |
| `dtd`, `actions` | DTD-Registrierung, „New → Tapestry 4 Page/Component“ |

Jede Referenz entscheidet selbst, ob und wie streng sie unaufgelöst gemeldet wird (`TapestryReference#getUnresolvedSeverity`); der Annotator wertet das nur aus.

Tests: `TemplateSupportTest`, `SpecificationSupportTest` (IDE-Fixture, gemeinsame Basis `TapestryTestCase`) und `ParserTest` (reine Unit-Tests).

Nach Änderungen an Ressourcen (z.B. DTDs) bei merkwürdigen Testergebnissen die Sandbox löschen: `.intellijPlatform/sandbox`. Der VFS-Cache der Test-IDE erkennt geänderte Plugin-JARs wegen der reproduzierbaren Zeitstempel nicht immer.
- Das jwcid-Attribut ist fest `jwcid` (die Meta-Einstellung `org.apache.tapestry.jwcid-attribute-name` wird nicht ausgewertet).
