package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
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
    public static @NotNull Map<String, XmlAttributeValue> getAll(@NotNull final Project project) {
        final Map<String, XmlAttributeValue> result = new LinkedHashMap<>();
        collect(project, STATE_APPLICATION_OBJECTS, result);
        collect(project, STATE_FACTORY_OBJECTS, result);
        return result;
    }

    private static void collect(final Project project, final String configurationId, final Map<String, XmlAttributeValue> out) {
        for (final XmlTag stateObject : HiveModules.getContributedElements(project, configurationId, TAG_STATE_OBJECT)) {
            final XmlAttribute name = stateObject.getAttribute(ATTR_NAME);
            if (name != null && name.getValueElement() != null && !StringUtil.isEmptyOrSpaces(name.getValue())) {
                out.putIfAbsent(name.getValue().trim(), name.getValueElement());
            }
        }
    }
}
