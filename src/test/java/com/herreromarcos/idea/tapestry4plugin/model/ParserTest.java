package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.util.TextRange;
import junit.framework.TestCase;

/** Reine Unit-Tests der Parser für jwcid und Binding-Ausdrücke (ohne IDE). */
public class ParserTest extends TestCase {

    public void testDeclaredId() {
        final Jwcid jwcid = Jwcid.parse(" foo ");
        assertEquals("foo", jwcid.id());
        assertNull(jwcid.type());
        assertTrue(jwcid.isDeclaredReference());
        assertEquals(TextRange.create(1, 4), jwcid.idRange());
    }

    public void testImplicitComponent() {
        final Jwcid jwcid = Jwcid.parse("@Insert");
        assertNull(jwcid.id());
        assertEquals("Insert", jwcid.type());
        assertTrue(jwcid.isImplicit());
        assertEquals(TextRange.create(1, 7), jwcid.typeRange());
    }

    public void testNamedImplicitComponentWithLibraryPrefix() {
        final Jwcid jwcid = Jwcid.parse("table@contrib:Table");
        assertEquals("table", jwcid.id());
        assertEquals("contrib:Table", jwcid.type());
        assertEquals("contrib:Table", jwcid.typeRange().substring("table@contrib:Table"));
    }

    public void testSpecialIds() {
        assertTrue(Jwcid.parse("$content$").special());
        assertTrue(Jwcid.parse("$remove$").special());
        assertNull(Jwcid.parse("$content$").id());
    }

    public void testEmptyJwcid() {
        final Jwcid jwcid = Jwcid.parse("");
        assertTrue(jwcid.isDeclaredReference());
        assertTrue(jwcid.idRange().isEmpty());
    }

    public void testBindingWithKnownPrefix() {
        final BindingExpression binding = BindingExpression.parse("ognl:user.name");
        assertEquals("ognl", binding.prefix());
        assertEquals(5, binding.expressionStart());
        assertEquals("user.name", binding.expression());
        assertFalse(binding.isLiteral());
    }

    public void testUnknownPrefixIsLiteral() {
        final BindingExpression binding = BindingExpression.parse("http://example.com");
        assertNull(binding.prefix());
        assertEquals("http://example.com", binding.expression());
        assertTrue(binding.isLiteral());
        assertEquals("ognl", binding.prefixOr("ognl"));
    }

    public void testLiteralPrefix() {
        final BindingExpression binding = BindingExpression.parse("literal:Home");
        assertTrue(binding.isLiteral());
        assertEquals("Home", binding.expression());
    }
}
