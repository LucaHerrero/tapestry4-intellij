package com.herreromarcos.idea.tapestry4plugin.dtd;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;

/**
 * Bindet die Original-DTDs an ihre Public-/System-IDs, alle unverändert aus tapestry-framework-4.1.6.jar:
 * Spezifikationen 4.0/4.1 (org/apache/tapestry/parse/) und Scripts 3.0/4.0 (org/apache/tapestry/script/).
 * Damit bekommen .page/.jwc/.application/.library/.script Tag-/Attribut-Completion und Validierung,
 * ohne dass die DTD aus dem Netz geladen werden muss.
 */
public class TapestryResourceProvider implements StandardResourceProvider {
    private static final String DTD_4_0 = "dtd/Tapestry_4_0.dtd";
    private static final String DTD_4_1 = "dtd/Tapestry_4_1.dtd";
    private static final String SCRIPT_3_0 = "dtd/Script_3_0.dtd";
    private static final String SCRIPT_4_0 = "dtd/Script_4_0.dtd";

    @Override
    public void registerResources(final ResourceRegistrar registrar) {
        final ClassLoader loader = getClass().getClassLoader();
        // Tapestry 4.0
        registrar.addStdResource("-//Apache Software Foundation//Tapestry Specification 4.0//EN", DTD_4_0, loader);
        registrar.addStdResource("http://jakarta.apache.org/tapestry/dtd/Tapestry_4_0.dtd", DTD_4_0, loader);
        registrar.addStdResource("http://tapestry.apache.org/dtd/Tapestry_4_0.dtd", DTD_4_0, loader);
        // Tapestry 4.1
        registrar.addStdResource("-//Apache Software Foundation//Tapestry Specification 4.1//EN", DTD_4_1, loader);
        registrar.addStdResource("http://tapestry.apache.org/dtd/Tapestry_4_1.dtd", DTD_4_1, loader);
        registrar.addStdResource("http://jakarta.apache.org/tapestry/dtd/Tapestry_4_1.dtd", DTD_4_1, loader);
        // Script-Spezifikationen (.script), aus org/apache/tapestry/script/
        registrar.addStdResource("-//Apache Software Foundation//Tapestry Script Specification 3.0//EN", SCRIPT_3_0, loader);
        registrar.addStdResource("http://jakarta.apache.org/tapestry/dtd/Script_3_0.dtd", SCRIPT_3_0, loader);
        registrar.addStdResource("-//Apache Software Foundation//Tapestry Script Specification 4.0//EN", SCRIPT_4_0, loader);
        registrar.addStdResource("http://tapestry.apache.org/dtd/Script_4_0.dtd", SCRIPT_4_0, loader);
    }
}
