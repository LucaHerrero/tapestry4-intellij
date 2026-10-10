package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Application State Objects (User's Guide, "Managing server-side state"): {@code <state-object name>} in Beiträgen zu
 * {@code tapestry.state.ApplicationObjects} überschreiben gleichnamige aus {@code tapestry.state.FactoryObjects}
 * (dort definiert Tapestry die Standard-ASOs "visit" und "global").
 */
public class ApplicationStateObjects {
    private static final String TAG_STATE_OBJECT = "state-object";

    private ApplicationStateObjects() {
    }

    /** Name → {@code name}-Attributwert der {@code <state-object>}-Deklaration. */
    public static @NotNull Map<String, XmlAttributeValue> getAll(@NotNull Project project) {
        final Map<String, XmlAttributeValue> result = new LinkedHashMap<>();
        HiveModules.collectAttributeValues(project, STATE_APPLICATION_OBJECTS, TAG_STATE_OBJECT, ATTR_NAME, result);
        HiveModules.collectAttributeValues(project, STATE_FACTORY_OBJECTS, TAG_STATE_OBJECT, ATTR_NAME, result);
        return result;
    }
}
