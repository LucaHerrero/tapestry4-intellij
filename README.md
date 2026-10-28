# Tapestry 4 für IntelliJ IDEA

IntelliJ-Plugin für Apache Tapestry 4.0 und 4.1. Läuft ab IntelliJ IDEA 2024.3 in der Community- und der Ultimate-Edition.

## Installation

**Über den Marketplace:** *Settings → Plugins → Marketplace*, nach „Tapestry 4“ suchen und installieren.

**Manuell:** Die ZIP aus den [Releases](https://github.com/LucaHerrero/tapestry4-intellij/releases) herunterladen und unter *Settings → Plugins → ⚙ → Install Plugin from Disk…* auswählen.

## Features

### Templates
- `jwcid`-Completion und Navigation für deklarierte (`jwcid="greeting"`) und implizite Komponenten (`jwcid="@Insert"`, `jwcid="name@contrib:Table"`)
- Komponentenparameter als Attribute in der Completion
- Markierung fehlender Pflichtparameter, veralteter Parameter und doppelter ids
- Fehler wie zur Laufzeit von Tapestry 4.1: reservierte Parameter als Ausdruck, Parameter unter Name und Alias gebunden, implizite ids, die schon in Spezifikation oder Klasse deklariert sind
- Navigation aus Binding-Ausdrücken: `ognl:`, `listener:`, `message:`, `asset:`, `bean:`, `meta:`, `component:`, `clientId:`
- Navigation von `<span key="...">` zum Message-Key
- Unterstützung eines umkonfigurierten `jwcid`-Attributnamens

### Spezifikationen
- Erkennung von `.page`, `.jwc`, `.application`, `.library` und `.script` mit eigenen Icons (anhand des Wurzel-Elements, fremde Dateien bleiben unberührt)
- Mitgelieferte Tapestry-DTDs (3.0, 4.0, 4.1) für Completion und Validierung ohne Netzwerk
- Navigation zu Komponententypen, Parametern, Klassen, Beans, Assets, injizierten Seiten, Meta-Schlüsseln und Application State Objects
- Prüfungen wie beim Laden durch Tapestry 4.1: `<component>` ohne oder mit beidem von `type`/`copy-of`, `copy-of` auf eine später deklarierte Komponente, Pflichtparameter mit Default, `inherit-informal-parameters` bei rein formalen Komponenten, doppelt gebundene Parameter
- Doppelte Parameter (inkl. Aliase), Assets, Beans, Properties sowie Eigenschaften, die zwei Elemente erzeugen; doppelte Seiten, Komponententypen, Libraries und Extensions in `.application`/`.library`
- Namensregeln aus Tapestrys Parser (ids, Parameter, Properties, Seiten, Komponententypen, Libraries, Extensions) und reservierte Library-ids `framework`/`application`

### OGNL
- Syntaxprüfung mit dem originalen OGNL-Parser
- Auflösung von Property-Ketten inkl. Operatoren, Methodenaufrufen, Indizes und statischen Zugriffen
- Navigation zu Gettern und Methoden

### Annotationen
- Navigation, Completion und Prüfung für `@InjectPage`, `@InjectComponent`, `@Component`, `@InjectAsset`, `@Message`, `@InjectScript`, `@Asset`, `@InitialValue`, `@InjectState`, `@InjectMeta` und `@EventListener`
- Ableitung von Schlüsseln aus dem Methodennamen wie in Tapestry (`getPageTitle` → `page-title`)

### Navigation
- Gutter-Icons zwischen Klasse, Spezifikation und Template
- Wechsel per *Navigate → Related Symbol* (Strg+Alt+Pos1)
- *New → Tapestry 4 Page* und *Tapestry 4 Component*

### Konfiguration
- Auflösung von Seitenklassen über `class`, `page-class-packages`, `component-class-packages` und `default-page-class`
- Konfigurationssuche in derselben Reihenfolge wie Tapestry: Spezifikation/`@Meta`, Library/Application, `web.xml`, `hivemind.ApplicationDefaults`
- Erkennung eigener Binding-Präfixe aus `tapestry.bindings.BindingFactories`

## Grenzen

- Unbekannte OGNL-Properties sind Warnungen, keine Fehler.
- Ohne Tapestry-JAR im Classpath werden Framework-Komponenten nicht aufgelöst, aber auch nicht als Fehler gemeldet.
- Templates mit eigener Endung (z. B. `.wml`) müssen unter *Settings → Editor → File Types* als HTML zugeordnet werden.
- Dateien ohne DOCTYPE bekommen keine DTD-Validierung.
- JVM-Properties (`-Dorg.apache.tapestry…`) werden nicht ausgewertet.
- HiveMind-Verweise (`service:`, `@InjectObject`, `hivemind:`) übernimmt das separate HiveMind-Plugin.
- `<binding>`-Werte im Element-Inhalt statt im `value`-Attribut werden nicht ausgewertet.
- Unbekannte Meta-Schlüssel werden nicht gemeldet.
- Templates und `.jwc`-Dateien werden projektweit gesucht, Tapestry selbst sucht nur an festen Stellen.

## Selbst bauen

```
gradlew buildPlugin
```

Die ZIP liegt danach in `build/distributions/`. Mit `gradlew runIde` startet eine Sandbox-IDE mit dem Plugin, `gradlew test` führt die Tests aus, `gradlew verifyPlugin` prüft die Kompatibilität. Benötigt Java 21 (wird bei Bedarf von Gradle heruntergeladen).

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE). Enthält die DTDs aus Apache Tapestry 4.1.6 und OGNL 2.6.9 (BSD-Lizenz), Details in [NOTICE](NOTICE).
