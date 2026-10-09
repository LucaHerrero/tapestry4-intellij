package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.util.TextRange;
import junit.framework.TestCase;

import java.util.Set;

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

    /** Wie BindingSourceImpl: ein nicht registriertes "name:" gehört zum Ausdruck, es gilt das Standard-Präfix. */
    public void testUnregisteredPrefixBelongsToExpression() {
        final BindingExpression binding = BindingExpression.parse("http://example.com");
        assertNull(binding.prefix());
        assertNull(binding.customPrefix());
        assertEquals("http", binding.unregisteredPrefix());
        assertEquals("http://example.com", binding.expression());
        assertTrue(binding.isLiteral());
        assertEquals("ognl", binding.effectivePrefix("ognl"));
    }

    /** Registriertes projekteigenes Präfix: wird abgetrennt, der Ausdruck aber nicht ausgewertet. */
    public void testRegisteredCustomPrefix() {
        final BindingExpression binding = BindingExpression.parse("spring:userService", Set.of("spring"));
        assertEquals("spring", binding.customPrefix());
        assertEquals("spring", binding.registeredPrefix());
        assertEquals("userService", binding.expression());
        assertFalse(binding.isLiteral());
        assertNull(binding.effectivePrefix("ognl"));
    }

    public void testNoPrefixUsesDefault() {
        final BindingExpression binding = BindingExpression.parse("user.name");
        assertNull(binding.prefix());
        assertNull(binding.customPrefix());
        assertEquals("ognl", binding.effectivePrefix("ognl"));
    }

    public void testDocumentedPrefixes() {
        for (final String prefix : java.util.List.of("validator", "validators", "meta", "clientId", "translator", "state", "hivemind")) {
            assertEquals(prefix, BindingExpression.parse(prefix + ":x").prefix());
        }
    }

    public void testLiteralPrefix() {
        final BindingExpression binding = BindingExpression.parse("literal:Home");
        assertTrue(binding.isLiteral());
        assertEquals("Home", binding.expression());
    }
}
