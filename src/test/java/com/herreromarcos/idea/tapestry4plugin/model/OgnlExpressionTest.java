package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.util.TextRange;
import junit.framework.TestCase;

import java.util.List;
import java.util.stream.Stream;

/** Unit-Tests für {@link OgnlExpression}: welche Ketten, Klassennamen und Syntaxfehler der OGNL-Parser liefert. */
public class OgnlExpressionTest extends TestCase {

    /** Ketten als "a.b()" bzw. "@Klasse@a" (Indizes als "[]" vor dem Glied). */
    private static List<String> chains(String expression) {
        return OgnlExpression.parse(expression).chains().stream().map(chain -> {
            final StringBuilder result = new StringBuilder(chain.rootKind() == OgnlExpression.RootKind.STATIC
                    ? "@%s@".formatted(chain.staticClass()) : "");
            for (int i = 0; i < chain.segments().size(); i++) {
                final OgnlExpression.Segment segment = chain.segments().get(i);
                if (i > 0) result.append('.');
                result.repeat("[]", segment.indexCount()).append(segment.name()).append(segment.call() ? "()" : "");
            }
            return result.toString();
        }).sorted().toList();
    }

    public void testSimpleChain() {
        assertEquals(List.of("user.address.city"), chains("user.address.city"));
    }

    public void testOperatorsAndKeywords() {
        assertEquals(List.of("count", "limit", "user.active"), chains("user.active and count gt limit"));
        assertEquals(List.of("a", "b", "flag"), chains("flag ? a : b"));
        assertEquals(List.of("name"), chains("name == null || name.length() == 0").subList(0, 1));
        assertEquals(List.of("items"), chains("!items"));
        assertEquals(List.of("a", "b"), chains("a + 'text' + b"));
    }

    public void testMethodArgumentsAreRootContext() {
        assertEquals(Stream.of("format()", "user.birthday", "user.name").sorted().toList(),
                chains("format(user.birthday, user.name)"));
        assertEquals(List.of("index", "messages.format()"), chains("messages.format('key', index)"));
    }

    public void testIndexes() {
        assertEquals(List.of("i", "items.[]name"), chains("items[i].name"));
        assertEquals(List.of("map.[][]value"), chains("map['a'][0].value"));
    }

    public void testProjectionAndSelectionAreNotResolved() {
        // innerhalb von {...} ist das aktuelle Objekt das Element – nur die Liste davor wird aufgelöst
        assertEquals(List.of("users"), chains("users.{name}"));
        assertEquals(List.of("users"), chains("users.{? active}.size()"));
        assertEquals(List.of("users"), chains("users.{^ #this.active}"));
    }

    public void testVariables() {
        assertEquals(List.of("visit.user"), chains("#root.visit.user"));
        assertEquals(List.of(), chains("#this.name"));
        assertEquals(List.of("value"), chains("#var = value"));
    }

    public void testStaticAccess() {
        assertEquals(List.of("@java.lang.Math@max()", "a", "b"), chains("@java.lang.Math@max(a, b)"));
        assertEquals(List.of("@com.example.Constants@NAME.length()"), chains("@com.example.Constants@NAME.length()"));
        assertEquals(List.of("@java.lang.Math@PI"), chains("@@PI"));
        assertEquals(List.of("java.lang.Math", "com.example.Constants"),
                List.of(OgnlExpression.parse("@java.lang.Math@max(1, 2) + @com.example.Constants@X").classNames().get(0).name(),
                        OgnlExpression.parse("@java.lang.Math@max(1, 2) + @com.example.Constants@X").classNames().get(1).name()));
    }

    public void testNewAndInstanceof() {
        final OgnlExpression ognl = OgnlExpression.parse("new java.util.Date(time) instanceof java.util.Date");
        assertEquals(List.of("java.util.Date", "java.util.Date"), ognl.classNames().stream().map(OgnlExpression.ClassName::name).toList());
        assertEquals(List.of("time"), chains("new java.util.Date(time)"));
    }

    public void testListAndMapLiterals() {
        assertEquals(List.of("a", "b"), chains("{a, b}"));
        assertEquals(List.of("value"), chains("#{'key' : value}"));
        assertEquals(List.of("value"), chains("#@java.util.TreeMap@{'key' : value}"));
    }

    public void testStringsAreSkipped() {
        assertEquals(List.of(), chains("'user.name'"));
        assertEquals(List.of(), chains("\"a \\\" b\""));
        assertEquals(List.of(), chains("42"));
    }

    public void testSyntaxErrors() {
        final OgnlExpression.SyntaxError incomplete = OgnlExpression.parse("user.").syntaxError();
        assertNotNull(incomplete);
        assertEquals("Invalid OGNL expression: unexpected end", incomplete.message());
        assertEquals(new TextRange(4, 5), incomplete.range());

        final OgnlExpression.SyntaxError unexpected = OgnlExpression.parse("user name").syntaxError();
        assertNotNull(unexpected);
        assertEquals("Invalid OGNL expression: unexpected 'name'", unexpected.message());
        assertEquals(new TextRange(5, 9), unexpected.range());

        assertNull(OgnlExpression.parse("a ? b : c").syntaxError());
    }

    public void testRanges() {
        final OgnlExpression.Chain chain = OgnlExpression.parse(" user.name").chains().getFirst();
        assertEquals(1, chain.segments().get(0).range().getStartOffset());
        assertEquals(6, chain.segments().get(1).range().getStartOffset());
        assertEquals(10, chain.segments().get(1).range().getEndOffset());
    }
}
