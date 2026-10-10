# Tapestry 4 Support für IntelliJ IDEA

Ein Plugin für alle, die noch mit Apache Tapestry 4.0 oder 4.1 arbeiten. Es läuft ab IntelliJ IDEA 2024.3, in der Community- wie in der Ultimate-Edition.

## Installation

```
gradlew buildPlugin
```

Die ZIP-Datei liegt danach in `build/distributions/`. In IntelliJ unter *Settings → Plugins → ⚙ → Install Plugin from Disk…* auswählen, fertig.

Wer es erst einmal ausprobieren will, startet mit `gradlew runIde` eine Sandbox-IDE mit dem Plugin.

## Was das Plugin kann

### Templates
- `jwcid` wird erkannt, mit Completion und Strg+Klick: für deklarierte Komponenten (`jwcid="greeting"`) genauso wie für implizite (`jwcid="@Insert"`, `jwcid="name@contrib:Table"`).
- Die Parameter einer Komponente tauchen als Attribute in der Completion auf. Fehlende Pflichtparameter, veraltete Parameter und doppelte ids werden markiert.
- Binding-Ausdrücke sind anklickbar: `ognl:user.address.city` führt zum Getter, `listener:onSave` zur Methode, `message:title` zum Eintrag in der Properties-Datei, `asset:`, `bean:` und `component:` in die Spezifikation.
- `<span key="...">` führt ebenfalls zum Message-Key.

Heißt das Attribut im Projekt anders (über `org.apache.tapestry.jwcid-attribute-name`), nimmt das Plugin den konfigurierten Namen.

### Spezifikationen
- `.page`, `.jwc`, `.application`, `.library` und `.script` werden als XML erkannt und bekommen eigene Icons.
- Die Original-DTDs von Tapestry (3.0, 4.0 und 4.1) sind dabei. Completion und Validierung funktionieren deshalb auch ohne Netzwerk.
- Komponententypen, Parameter, Klassen, Beans, Assets, injizierte Seiten und Application State Objects lassen sich per Strg+Klick öffnen.

### OGNL
Ausdrücke werden mit dem echten OGNL-Parser zerlegt, in der Version, die auch Tapestry 4.1 verwendet. Syntaxfehler sieht man sofort, und Ketten werden auch in Operatoren, Methodenaufrufen und Indizes aufgelöst. `users[0].name` kennt den Elementtyp der Liste, `@com.x.Util@FIELD` funktioniert, Pseudo-Properties wie `list.size` ebenfalls. Bei Maps, Projektionen und Variablen prüft das Plugin bewusst nicht.

### Annotationen
Bei `@InjectPage`, `@InjectComponent`, `@Component`, `@InjectAsset`, `@Message`, `@InjectScript`, `@Asset`, `@InitialValue` und `@InjectState` gilt: Wo ein String auf etwas zeigt, gibt es Navigation, Completion und eine Warnung, wenn das Ziel fehlt.

### Navigation
Gutter-Icons verbinden Klasse, Spezifikation und Template. Mit *Navigate → Related Symbol* (Strg+Alt+Pos1) springt man zwischen den dreien hin und her. Unter *New* gibt es außerdem „Tapestry 4 Page“ und „Tapestry 4 Component“.

## Wie Dinge gefunden werden

Hier habe ich mich an die Tapestry-Doku gehalten:

- Template und Spezifikation gehören zusammen, wenn sie gleich heißen (`Home.html` und `Home.page`). Gibt es mehrere Kandidaten, gewinnt der mit dem ähnlichsten Pfad.
- Die Seitenklasse kommt aus dem `class`-Attribut, sonst aus `page-class-packages` bzw. `component-class-packages`, sonst aus `org.apache.tapestry.default-page-class`.
- Konfiguration wird in derselben Reihenfolge gesucht wie in Tapestry selbst: Spezifikation bzw. `@Meta`, dann Library oder Application, dann `web.xml` und zuletzt `hivemind.ApplicationDefaults`.
- Ein Binding-Präfix zählt nur, wenn es in `tapestry.bindings.BindingFactories` registriert ist. Eigene Präfixe aus dem Projekt werden dadurch auch erkannt.

## Grenzen

- Unbekannte OGNL-Properties sind Warnungen, keine Fehler.
- Ohne Tapestry-JAR im Classpath werden Framework-Komponenten wie `Insert` oder `For` nicht aufgelöst, aber auch nicht als Fehler gemeldet.
- Templates müssen in IntelliJ als HTML erkannt werden. Wer eine eigene Endung nutzt, zum Beispiel `.wml`, muss sie unter *Settings → Editor → File Types* zuordnen.
- Dateien ohne DOCTYPE bekommen keine DTD-Validierung.
- JVM-Properties wie `-Dorg.apache.tapestry…` kennt das Plugin nicht, die gibt es erst zur Laufzeit.
- Verweise auf HiveMind-Services (`service:`, `@InjectObject`, `hivemind:`) übernimmt das separate HiveMind-Plugin.

## Entwicklung

- Gebaut wird mit Java 21. Fehlt das JDK, lädt Gradle es selbst herunter.
- Der Gradle-Wrapper bleibt vorerst auf 9.7.1. Mit 9.8 übernimmt IntelliJ 2026.2 die Abhängigkeiten nicht richtig ins Projekt, und alle `com.intellij.*`-Imports werden rot.
- `gradlew test` startet die Tests, `gradlew verifyPlugin` prüft die Kompatibilität.
- Commons Lang 3 kommt aus der IntelliJ-Plattform und ist deshalb nur `compileOnly`. IntelliJ 2024.3 bringt Version 3.17 mit, neuere APIs wie `Strings.CS` gibt es dort noch nicht.
- Liefern die Tests nach Änderungen an DTDs oder anderen Ressourcen komische Ergebnisse, hilft es, `.intellijPlatform/sandbox` zu löschen.

Zum Code-Stil: `final` an Feldern und lokalen Variablen, nicht an Parametern und Klassen. Meldungen schreibe ich als Format-Strings.

## Releases

Jeder Push auf `master` baut das Plugin, lässt Tests und Verifier laufen und veröffentlicht die ZIP als GitHub Release. Die Version steht in `build.gradle.kts`. Gibt es zu der Version schon ein Release, wird nur gebaut. Für ein neues Release also vorher die Version hochziehen.

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE). Die DTDs stammen unverändert aus Apache Tapestry 4.1.6, außerdem ist OGNL 2.6.9 (BSD-Lizenz) enthalten. Details stehen in [NOTICE](NOTICE).
