package com.herreromarcos.idea.tapestry4plugin;

import java.util.List;

public class TapestryConstants {
    public static final String JWCID = "jwcid";
    public static final String CONTENT_ID = "$content$";
    public static final String REMOVE_ID = "$remove$";

    public static final String EXT_PAGE = "page";
    public static final String EXT_COMPONENT = "jwc";
    public static final String EXT_APPLICATION = "application";
    public static final String EXT_LIBRARY = "library";
    public static final String TEMPLATE_EXT = "html";

    public static final String ROOT_PAGE = "page-specification";
    public static final String ROOT_COMPONENT = "component-specification";
    public static final String ROOT_APPLICATION = "application";
    public static final String ROOT_LIBRARY = "library-specification";

    // Kind-Elemente der Spezifikationen
    public static final String TAG_COMPONENT = "component";
    public static final String TAG_BINDING = "binding";
    public static final String TAG_INHERITED_BINDING = "inherited-binding";
    public static final String TAG_PARAMETER = "parameter";
    public static final String TAG_PROPERTY = "property";
    public static final String TAG_ASSET = "asset";
    public static final String TAG_BEAN = "bean";
    public static final String TAG_META = "meta";
    public static final String TAG_PAGE = "page";
    public static final String TAG_LIBRARY = "library";
    public static final String TAG_COMPONENT_TYPE = "component-type";
    public static final String TAG_EXTENSION = "extension";

    // Attribute der Spezifikationen
    public static final String ATTR_ID = "id";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_CLASS = "class";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_COPY_OF = "copy-of";
    public static final String ATTR_SPECIFICATION_PATH = "specification-path";

    public static final String WEB_INF = "WEB-INF";
    public static final String PAGE_LINK = "PageLink";

    public static final String FRAMEWORK_LIBRARY = "Framework.library";
    public static final String FRAMEWORK_NAMESPACE = "framework";

    public static final String META_PAGE_PACKAGES = "org.apache.tapestry.page-class-packages";
    public static final String META_COMPONENT_PACKAGES = "org.apache.tapestry.component-class-packages";

    public static final String DEFAULT_PAGE_CLASS = "org.apache.tapestry.html.BasePage";
    public static final String DEFAULT_COMPONENT_CLASS = "org.apache.tapestry.BaseComponent";

    public static final String ANNOTATION_COMPONENT = "org.apache.tapestry.annotations.Component";
    public static final String ANNOTATION_PARAMETER = "org.apache.tapestry.annotations.Parameter";

    public static final String PREFIX_OGNL = "ognl";
    public static final String PREFIX_LITERAL = "literal";
    public static final String PREFIX_LISTENER = "listener";
    public static final String PREFIX_MESSAGE = "message";
    public static final String PREFIX_ASSET = "asset";
    public static final String PREFIX_BEAN = "bean";
    public static final String PREFIX_COMPONENT = "component";

    /** Binding-Präfixe von Tapestry 4.0/4.1 (inkl. häufiger Erweiterungen). */
    public static final List<String> BINDING_PREFIXES = List.of(
            PREFIX_OGNL, PREFIX_LITERAL, PREFIX_MESSAGE, PREFIX_LISTENER, PREFIX_ASSET, PREFIX_BEAN,
            PREFIX_COMPONENT, "state", "translator", "validators", "hivemind", "clientId", "spring");

    /** Fallback, falls die Tapestry-Bibliothek (Framework.library) nicht im Classpath liegt. */
    public static final List<String> FRAMEWORK_COMPONENTS = List.of(
            "Any", "Block", "Body", "Button", "Checkbox", "Conditional", "Delegator", "DirectLink", "Else",
            "ExceptionDisplay", "ExternalLink", "FieldLabel", "For", "Foreach", "Form", "Frame", "GenericLink",
            "Hidden", "If", "Image", "ImageSubmit", "Insert", "InsertText", "InvokeListener", "LinkSubmit",
            "ListEdit", "Option", "PageLink", "PropertySelection", "Radio", "RadioGroup", "RenderBlock",
            "RenderBody", "Rollover", "Script", "Select", "ServiceLink", "Shell", "Submit", "TextArea",
            "TextField", "Upload", "ValidField", "DatePicker", "Checkboxes", "Relation", "Style");

    private TapestryConstants() {
    }
}
