package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.util.TextRange;
import ognl.Node;
import ognl.OgnlParser;
import ognl.OgnlParserConstants;
import ognl.ParseException;
import ognl.Token;
import ognl.TokenMgrError;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Ein mit dem OGNL-Parser (OGNL 2.6, wie von Tapestry 4 verwendet) zerlegter Ausdruck: die auflösbaren
 * Eigenschafts-/Methodenketten, Klassennamen und ggf. der Syntaxfehler.
 *
 * <p>Die AST-Knoten von OGNL sind nicht öffentlich und tragen keine Positionen. Ausgewertet werden deshalb der
 * Knotentyp (Klassenname) und die öffentliche {@link Node}-Schnittstelle; die Positionen stammen aus den Tokens des
 * Parsers, die in Quelltextreihenfolge den Knoten zugeordnet werden.
 *
 * <p>Auswertungskontext laut OGNL: Ketten am Anfang eines (Teil-)Ausdrucks beziehen sich auf das aktuelle Objekt – auf
 * oberster Ebene die Seite/Komponente. Methodenargumente und Indizes werden gegen das Root-Objekt ausgewertet,
 * Projektionen/Selektionen ({@code list.{name}}, {@code list.{? active}}) gegen das jeweilige Element – dort sind
 * Ketten nicht auflösbar. {@code #root} ist das Root-Objekt, {@code #this} und Variablen sind unbekannt.
 */
public class OgnlExpression {
    /** {@code @@max(1, 2)}: ohne Klassennamen gilt java.lang.Math. */
    public static final String DEFAULT_STATIC_CLASS = "java.lang.Math";
    /** Knoten, deren Kinder gegen die Elemente einer Collection ausgewertet werden. */
    private static final Set<String> ELEMENT_CONTEXT_NODES = Set.of("ASTProject", "ASTSelect", "ASTSelectFirst", "ASTSelectLast");

    /** Wurzel einer Kette. */
    public enum RootKind {
        /** das Objekt, gegen das der Ausdruck ausgewertet wird (Seite/Komponente) */
        ROOT,
        /** statischer Zugriff {@code @Klasse@member}: das erste Glied ist ein statisches Feld bzw. eine statische Methode */
        STATIC
    }

    /**
     * Ein Glied einer Kette.
     *
     * @param name       Name der Eigenschaft bzw. Methode
     * @param range      Bereich des Namens im Ausdruck
     * @param call       Methodenaufruf {@code name(...)}
     * @param indexCount Anzahl der Indizes {@code [..]} zwischen dem Vorgänger und diesem Glied
     */
    public record Segment(@NotNull String name, @NotNull TextRange range, boolean call, int indexCount) {
    }

    /**
     * @param rootKind    Art der Wurzel
     * @param staticClass Klassenname bei {@link RootKind#STATIC}
     * @param segments    die Glieder in Reihenfolge
     */
    public record Chain(@NotNull RootKind rootKind, @Nullable String staticClass, @NotNull List<Segment> segments) {
    }

    /** Klassenname aus {@code new}, {@code instanceof}, {@code @Klasse@} bzw. {@code #@Klasse@{...}}. */
    public record ClassName(@NotNull String name, @NotNull TextRange range) {
    }

    /** Syntaxfehler mit Meldung und Bereich im Ausdruck. */
    public record SyntaxError(@NotNull String message, @NotNull TextRange range) {
    }

    private final List<Chain> chains = new ArrayList<>();
    private final List<ClassName> classNames = new ArrayList<>();
    private @Nullable SyntaxError syntaxError;

    private OgnlExpression() {
    }

    public @NotNull List<Chain> chains() {
        return chains;
    }

    public @NotNull List<ClassName> classNames() {
        return classNames;
    }

    public @Nullable SyntaxError syntaxError() {
        return syntaxError;
    }

    public static @NotNull OgnlExpression parse(@NotNull final String text) {
        final OgnlExpression result = new OgnlExpression();
        final OgnlParser parser = new OgnlParser(new StringReader(text));
        final Token start = parser.token;
        try {
            final Node root = parser.topLevelExpression();
            new Walker(text, located(text, start), result).walk(root, true);
        } catch (final ParseException e) {
            result.syntaxError = syntaxError(text, start, e.currentToken != null ? e.currentToken.next : null);
        } catch (final TokenMgrError e) {
            result.syntaxError = new SyntaxError("Invalid OGNL expression", TextRange.create(0, text.length()));
        }
        return result;
    }

    private static SyntaxError syntaxError(final String text, final Token start, @Nullable final Token unexpected) {
        final int end = text.stripTrailing().length();
        if (unexpected == null || unexpected.kind == OgnlParserConstants.EOF) {
            final int from = Math.max(0, end - 1);
            return new SyntaxError("Invalid OGNL expression: unexpected end", TextRange.create(from, Math.max(from, end)));
        }
        for (final Located token : located(text, start)) {
            if (token.token() == unexpected) {
                return new SyntaxError("Invalid OGNL expression: unexpected '%s'".formatted(unexpected.image), token.range());
            }
        }
        return new SyntaxError("Invalid OGNL expression", TextRange.create(0, end));
    }

    // ------------------------------------------------------------------ Tokens

    /** Token mit seiner Position im Ausdruck. */
    private record Located(@NotNull Token token, @NotNull TextRange range) {
        boolean isIdentifier() {
            return token.kind == OgnlParserConstants.IDENT;
        }

        boolean is(final String image) {
            return image.equals(token.image);
        }
    }

    /**
     * Die gelesenen Tokens mit Offsets. Die Offsets werden über den Text der Tokens bestimmt – die Zeilen-/Spaltenangaben
     * des Parsers rechnen Tabulatoren auf Tabstopps um.
     */
    private static List<Located> located(final String text, final Token start) {
        final List<Located> result = new ArrayList<>();
        int position = 0;
        for (Token token = start.next; token != null && token.kind != OgnlParserConstants.EOF; token = token.next) {
            final int offset = text.indexOf(token.image, position);
            if (offset < 0) break;
            position = offset + token.image.length();
            result.add(new Located(token, TextRange.create(offset, position)));
        }
        return result;
    }

    // ------------------------------------------------------------------ AST

    private static class Walker {
        private final String text;
        private final List<Located> tokens;
        private final OgnlExpression result;
        private int cursor;

        Walker(final String text, final List<Located> tokens, final OgnlExpression result) {
            this.text = text;
            this.tokens = tokens;
            this.result = result;
        }

        private static String type(final Node node) {
            return node.getClass().getSimpleName();
        }

        /** Durchläuft einen Knoten in Quelltextreihenfolge; {@code rootContext}: Ketten beziehen sich auf das Root-Objekt. */
        void walk(final Node node, final boolean rootContext) {
            switch (type(node)) {
                case "ASTChain" -> walkChain(node, rootContext);
                case "ASTProperty", "ASTMethod", "ASTStaticField", "ASTStaticMethod" -> {
                    final List<Segment> segments = new ArrayList<>();
                    final Chain chain = walkChainStart(node, rootContext, segments);
                    if (chain != null) record(chain.rootKind(), chain.staticClass(), segments);
                }
                case "ASTCtor" -> {
                    nextToken("new");
                    className();
                    walkChildren(node, true);
                }
                case "ASTInstanceof" -> {
                    walkChildren(node, rootContext);
                    nextToken("instanceof");
                    className();
                }
                case "ASTMap" -> {
                    if (node.toString().startsWith("#@")) {
                        nextToken("@");
                        className();
                    }
                    walkChildren(node, rootContext);
                }
                case "ASTVarRef" -> nextIdentifier(node.toString().substring(1));
                default -> walkChildren(node, !ELEMENT_CONTEXT_NODES.contains(type(node)) && rootContext);
            }
        }

        private void walkChildren(final Node node, final boolean rootContext) {
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                walk(node.jjtGetChild(i), rootContext);
            }
        }

        /**
         * Erstes Glied einer Kette; liefert deren Wurzel oder {@code null}, wenn sie nicht auflösbar ist. Die Tokens
         * werden in jedem Fall verbraucht, damit die Zuordnung der folgenden Knoten stimmt.
         */
        private @Nullable Chain walkChainStart(final Node node, final boolean rootContext, final List<Segment> segments) {
            switch (type(node)) {
                case "ASTProperty" -> {
                    if (isIndexed(node)) {
                        walkChildren(node, true);
                        return null;
                    }
                    final Segment segment = segment(node.toString(), false, 0);
                    if (segment == null || !rootContext) return null;
                    segments.add(segment);
                    return new Chain(RootKind.ROOT, null, segments);
                }
                case "ASTMethod" -> {
                    final Segment segment = segment(methodName(node), true, 0);
                    walkChildren(node, true);
                    if (segment == null || !rootContext) return null;
                    segments.add(segment);
                    return new Chain(RootKind.ROOT, null, segments);
                }
                case "ASTStaticField", "ASTStaticMethod" -> {
                    // "@java.lang.Math@max(a, b)" – auch bei "@@max" nennt toString() die Klasse
                    final String description = node.toString();
                    final String staticClass = StringUtils.substringBetween(description, "@", "@");
                    final String member = StringUtils.substringBefore(StringUtils.substringAfter(description.substring(1), "@"), "(");
                    nextToken("@");
                    className();
                    nextToken("@");
                    final Segment segment = segment(member, "ASTStaticMethod".equals(type(node)), 0);
                    walkChildren(node, true);
                    if (segment == null || staticClass == null) return null;
                    segments.add(segment);
                    return new Chain(RootKind.STATIC, StringUtils.defaultIfEmpty(staticClass, DEFAULT_STATIC_CLASS), segments);
                }
                case "ASTRootVarRef" -> {
                    return new Chain(RootKind.ROOT, null, segments);
                }
                default -> {
                    walk(node, rootContext);
                    return null;
                }
            }
        }

        private void walkChain(final Node chainNode, final boolean rootContext) {
            List<Segment> segments = new ArrayList<>();
            final Chain chain = walkChainStart(chainNode.jjtGetChild(0), rootContext, segments);
            if (chain == null) segments = null;
            int indexCount = 0;
            for (int i = 1; i < chainNode.jjtGetNumChildren(); i++) {
                final Node child = chainNode.jjtGetChild(i);
                final String type = type(child);
                if ("ASTProperty".equals(type) && isIndexed(child)) {
                    // Index wird gegen das Root-Objekt ausgewertet
                    walkChildren(child, true);
                    indexCount++;
                } else if ("ASTProperty".equals(type) || "ASTMethod".equals(type)) {
                    final boolean call = "ASTMethod".equals(type);
                    final Segment segment = segment(call ? methodName(child) : child.toString(), call, indexCount);
                    if (call) walkChildren(child, true);
                    if (segments != null && segment != null) segments.add(segment);
                    indexCount = 0;
                } else {
                    // Projektion, Selektion oder Teilausdruck: ab hier ist der Typ unbekannt
                    walk(child, false);
                    if (segments != null) record(chain.rootKind(), chain.staticClass(), segments);
                    segments = null;
                }
            }
            if (segments != null) record(chain.rootKind(), chain.staticClass(), segments);
        }

        private void record(final RootKind rootKind, @Nullable final String staticClass, final List<Segment> segments) {
            if (!segments.isEmpty()) result.chains.add(new Chain(rootKind, staticClass, segments));
        }

        private static boolean isIndexed(final Node property) {
            return property.toString().startsWith("[");
        }

        private static String methodName(final Node method) {
            return StringUtils.substringBefore(method.toString(), "(");
        }

        /** Glied für den nächsten Bezeichner mit diesem Namen; {@code null}, wenn er sich nicht zuordnen lässt. */
        private @Nullable Segment segment(final String name, final boolean call, final int indexCount) {
            final Located identifier = nextIdentifier(name);
            return identifier != null ? new Segment(name, identifier.range(), call, indexCount) : null;
        }

        private @Nullable Located nextIdentifier(final String name) {
            for (int i = cursor; i < tokens.size(); i++) {
                final Located token = tokens.get(i);
                if (token.isIdentifier() && token.is(name)) {
                    cursor = i + 1;
                    return token;
                }
            }
            return null;
        }

        private void nextToken(final String image) {
            for (int i = cursor; i < tokens.size(); i++) {
                if (tokens.get(i).is(image)) {
                    cursor = i + 1;
                    return;
                }
            }
        }

        /** Qualifizierter Klassenname ({@code java.util.Date}) ab der aktuellen Position; leer bei {@code @@}. */
        private void className() {
            final int first = cursor;
            int last = -1;
            while (cursor < tokens.size() && tokens.get(cursor).isIdentifier()) {
                last = cursor++;
                if (cursor + 1 < tokens.size() && tokens.get(cursor).is(".") && tokens.get(cursor + 1).isIdentifier()) {
                    cursor++;
                } else {
                    break;
                }
            }
            if (last < 0) return;
            final TextRange range = TextRange.create(tokens.get(first).range().getStartOffset(), tokens.get(last).range().getEndOffset());
            result.classNames.add(new ClassName(range.substring(text), range));
        }
    }
}
