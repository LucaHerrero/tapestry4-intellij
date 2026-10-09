package com.herreromarcos.idea.tapestry4plugin;

import java.util.List;
import java.util.Set;

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

    // Kind-Elemente der Spezifikationen (Tapestry 4.0/4.1)
    public static final String TAG_COMPONENT = "component";
    public static final String TAG_BINDING = "binding";
    public static final String TAG_INHERITED_BINDING = "inherited-binding";
    public static final String TAG_PARAMETER = "parameter";
    public static final String TAG_PROPERTY = "property";
    public static final String TAG_ASSET = "asset";
    public static final String TAG_BEAN = "bean";
    public static final String TAG_INJECT = "inject";
    public static final String TAG_META = "meta";
    public static final String TAG_PAGE = "page";
    public static final String TAG_LIBRARY = "library";
    public static final String TAG_COMPONENT_TYPE = "component-type";
    public static final String TAG_EXTENSION = "extension";

    // Nur Tapestry-3.0-DTD (von Tapestry 4 weiterhin unterstützt)
    public static final String TAG_PROPERTY_SPECIFICATION = "property-specification";
    public static final String TAG_STATIC_BINDING = "static-binding";
    public static final String TAG_MESSAGE_BINDING = "message-binding";
    public static final String TAG_LISTENER_BINDING = "listener-binding";
    public static final String TAG_CONTEXT_ASSET = "context-asset";
    public static final String TAG_PRIVATE_ASSET = "private-asset";
    public static final String TAG_EXTERNAL_ASSET = "external-asset";

    /** Elemente, die einen Parameter einer eingebetteten Komponente binden (4.0 und 3.0). */
    public static final List<String> BINDING_TAGS = List.of(TAG_BINDING, TAG_INHERITED_BINDING, TAG_STATIC_BINDING,
            TAG_MESSAGE_BINDING, TAG_LISTENER_BINDING);
    /** Elemente, die ein Asset definieren (4.0 und 3.0). */
    public static final List<String> ASSET_TAGS = List.of(TAG_ASSET, TAG_CONTEXT_ASSET, TAG_PRIVATE_ASSET, TAG_EXTERNAL_ASSET);

    // Attribute der Spezifikationen
    public static final String ATTR_ID = "id";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_CLASS = "class";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_KEY = "key";
    public static final String ATTR_PROPERTY = "property";
    public static final String ATTR_PROPERTY_NAME = "property-name";
    public static final String ATTR_COPY_OF = "copy-of";
    public static final String ATTR_SPECIFICATION_PATH = "specification-path";

    // Lokalisierungs-Direktive im Template: <span key="..." raw="true">
    public static final String TAG_SPAN = "span";
    public static final String ATTR_RAW = "raw";

    public static final String WEB_INF = "WEB-INF";
    public static final String PAGE_LINK = "PageLink";
    public static final String WEB_XML = "web.xml";

    // HiveMind: Application State Objects
    public static final String HIVEMODULE_XML = "hivemodule.xml";
    public static final String STATE_APPLICATION_OBJECTS = "tapestry.state.ApplicationObjects";
    public static final String STATE_FACTORY_OBJECTS = "tapestry.state.FactoryObjects";

    public static final String FRAMEWORK_LIBRARY = "Framework.library";
    public static final String FRAMEWORK_NAMESPACE = "framework";

    public static final String META_PAGE_PACKAGES = "org.apache.tapestry.page-class-packages";
    public static final String META_COMPONENT_PACKAGES = "org.apache.tapestry.component-class-packages";
    public static final String META_BEAN_PACKAGES = "org.apache.tapestry.bean-class-packages";
    public static final String META_DEFAULT_PAGE_CLASS = "org.apache.tapestry.default-page-class";
    public static final String META_DEFAULT_BINDING_PREFIX = "org.apache.tapestry.default-binding-prefix";
    public static final String META_JWCID_ATTRIBUTE = "org.apache.tapestry.jwcid-attribute-name";
    public static final String META_TEMPLATE_EXTENSION = "org.apache.tapestry.template-extension";

    public static final String DEFAULT_PAGE_CLASS = "org.apache.tapestry.html.BasePage";
    public static final String DEFAULT_COMPONENT_CLASS = "org.apache.tapestry.BaseComponent";

    public static final String ANNOTATIONS = "org.apache.tapestry.annotations.";
    public static final String ANNOTATION_COMPONENT = ANNOTATIONS + "Component";
    public static final String ANNOTATION_PARAMETER = ANNOTATIONS + "Parameter";
    public static final String ANNOTATION_ASSET = ANNOTATIONS + "Asset";
    public static final String ANNOTATION_BEAN = ANNOTATIONS + "Bean";
    public static final String ANNOTATION_COMPONENT_CLASS = ANNOTATIONS + "ComponentClass";
    public static final String ANNOTATION_META = ANNOTATIONS + "Meta";
    public static final String ANNOTATION_INJECT_PAGE = ANNOTATIONS + "InjectPage";
    public static final String ANNOTATION_INJECT_COMPONENT = ANNOTATIONS + "InjectComponent";
    public static final String ANNOTATION_INJECT_ASSET = ANNOTATIONS + "InjectAsset";
    public static final String ANNOTATION_INJECT_SCRIPT = ANNOTATIONS + "InjectScript";
    public static final String ANNOTATION_MESSAGE = ANNOTATIONS + "Message";
    public static final String ANNOTATION_INITIAL_VALUE = ANNOTATIONS + "InitialValue";
    public static final String ANNOTATION_INJECT_STATE = ANNOTATIONS + "InjectState";
    public static final String ANNOTATION_INJECT_STATE_FLAG = ANNOTATIONS + "InjectStateFlag";

    public static final String PREFIX_OGNL = "ognl";
    public static final String PREFIX_LITERAL = "literal";
    public static final String PREFIX_LISTENER = "listener";
    public static final String PREFIX_MESSAGE = "message";
    public static final String PREFIX_ASSET = "asset";
    public static final String PREFIX_BEAN = "bean";
    public static final String PREFIX_COMPONENT = "component";
    public static final String PREFIX_VALIDATORS = "validators";
    public static final String PREFIX_STATE = "state";
    /** Gehört zum HiveMind-Plugin; hier nur ausgenommen. */
    public static final String PREFIX_HIVEMIND = "hivemind";

    /**
     * Dokumentierte Binding-Präfixe von Tapestry 4.0 und 4.1 (User's Guide, "Component Bindings").
     * Projekte können eigene Präfixe beitragen (tapestry.bindings.BindingFactories), siehe {@code BindingPrefixes}.
     */
    public static final List<String> BINDING_PREFIXES = List.of(
            "asset", "bean", "clientId", "component", "hivemind", "listener", "literal", "message", "meta",
            "ognl", "state", "translator", "validator", "validators");

    /** Boolesche Werte laut Spezifikations-Doku ("Boolean type values"). */
    public static final Set<String> TRUE_VALUES = Set.of("true", "yes", "on", "1", "t", "y", "aye");
    public static final Set<String> FALSE_VALUES = Set.of("false", "no", "off", "0", "f", "n", "nay");

    /**
     * Fallback, falls Framework.library nicht im Classpath liegt: Vereinigung der Komponenten-Referenz von
     * Tapestry 4.0.2 und der Framework.library von Tapestry 4.1.6.
     */
    public static final List<String> FRAMEWORK_COMPONENTS = List.of(
            "ActionLink", "Any", "Autocompleter", "Block", "Body", "Button", "Checkbox", "Conditional", "DatePicker",
            "Delegator", "Describe", "Dialog", "DirectLink", "DropdownDatePicker", "DropdownTimePicker", "Else",
            "ExceptionDisplay", "ExternalLink", "FieldLabel", "For", "Foreach", "Form", "Frame", "GTimePicker",
            "GenericLink", "Hidden", "If", "Image", "ImageSubmit", "InlineEditBox", "Insert", "InsertText",
            "InvokeListener", "LinkSubmit", "ListEdit", "Option", "PageLink", "PropertySelection", "Radio",
            "RadioGroup", "Relation", "RenderBlock", "RenderBody", "RequestDisplay", "Rollover", "Script",
            "ScriptIncludes", "Select", "ServiceLink", "Shell", "Style", "Submit", "Suggest", "TextArea",
            "TextField", "Upload", "ValidField");

    private TapestryConstants() {
    }
}
