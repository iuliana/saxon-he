////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StructuredQName;

import java.util.*;

/**
 * LanguageFeature represents a named XQuery 3.0 language feature as used in the require/prohibit feature declaration.
 */

public class LanguageFeature {

    public static final int ALWAYS = 0;     // feature is always supported in Saxon
    public static final int NEVER = 1;      // feature is never supported in Saxon
    public static final int OPTIONAL = 2;   // feature can be enabled or disabled in Saxon

    public static final StructuredQName ALL_OPTIONAL_FEATURES =
            new StructuredQName("", NamespaceConstant.XQUERY, "all-optional-features");

    public final static LanguageFeature TYPED_DATA = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "typed-data"), null, OPTIONAL);
    public final static LanguageFeature TYPED_DATA_SCHEMAS = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "typed-data-schemas"), TYPED_DATA, OPTIONAL);
    public final static LanguageFeature TYPED_DATA_ALL_OPTIONAL_FEATURES = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "typed-data-all-optional-features"), TYPED_DATA, OPTIONAL);
    public final static LanguageFeature STATIC_TYPING = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "static-typing"), null, NEVER);
    public final static LanguageFeature STATIC_TYPING_ALL_OPTIONAL_FEATURES = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "static-typing-all-optional-features"), STATIC_TYPING, NEVER);
    public final static LanguageFeature SERIALIZATION = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "serialization"), null, ALWAYS);
    public final static LanguageFeature SERIALIZATION_ALL_OPTIONAL_FEATURES = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "serialization-all-optional-features"), SERIALIZATION, ALWAYS);
    public final static LanguageFeature MODULE = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "module"), null, OPTIONAL);
    public final static LanguageFeature MODULE_ALL_OPTIONAL_FEATURES = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "module-all-optional-features"), MODULE, OPTIONAL);
    public final static LanguageFeature HIGHER_ORDER_FUNCTION = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "higher-order-function"), null, ALWAYS);
    public final static LanguageFeature HIGHER_ORDER_FUNCTION_ALL_OPTIONAL_FEATURES = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "higher-order-function-all-optional-features"), HIGHER_ORDER_FUNCTION, ALWAYS);
    public final static LanguageFeature ALL_EXTENSIONS = new LanguageFeature(
            new StructuredQName("", NamespaceConstant.XQUERY, "all-extensions"), null, OPTIONAL);

    // Map containing all features, indexed by name
    private static Map<StructuredQName, LanguageFeature> features = new HashMap<StructuredQName, LanguageFeature>();

    private static void add(LanguageFeature f) {
        features.put(f.getName(), f);
    }

    static {
        add(TYPED_DATA);
        add(TYPED_DATA_SCHEMAS);
        add(TYPED_DATA_ALL_OPTIONAL_FEATURES);
        add(STATIC_TYPING);
        add(STATIC_TYPING_ALL_OPTIONAL_FEATURES);
        add(SERIALIZATION);
        add(SERIALIZATION_ALL_OPTIONAL_FEATURES);
        add(MODULE);
        add(MODULE_ALL_OPTIONAL_FEATURES);
        add(HIGHER_ORDER_FUNCTION);
        add(HIGHER_ORDER_FUNCTION_ALL_OPTIONAL_FEATURES);
    }

    /**
     * Get the list of all recognized optional features
     * @return the collection of optional features defined in the XQuery specification
     */

    public static Collection<LanguageFeature> getAllOptionalFeatures() {
        return features.values();
    }

    private StructuredQName name;
    private LanguageFeature parent;
    private int availability;
    private Set<LanguageFeature> children = new HashSet<LanguageFeature>();

    public LanguageFeature(StructuredQName name, LanguageFeature parent, int availability) {
        this.name = name;
        if (parent != null) {
            this.parent = parent;
            parent.children.add(this);
        }
        this.availability = availability;
    }

    /**
     * Get the name of the feature
     * @return the name of the feature
     */
    public StructuredQName getName() {
        return name;
    }

    /**
     * Get the parent (container) of the feature
     * @return the parent (container) of the feature, or null if there is no parent
     */
    public LanguageFeature getParent() {
        return parent;
    }

    /**
     * Get the children of this feature
     * @return the features of which this is the parent
     */
    public Set<LanguageFeature> getChildren() {
        return children;
    }

    /**
     * Determine the availability of the feature in Saxon
     * @return whether the feature is always supported in Saxon, never supported in Saxon, or can be enabled/disabled
     * in Saxon
     */

    public int getAvailability() {
        return availability;
    }

    /**
     * Get the feature with a given name
     * @param name the name of the feature
     * @return the corresponding feature, or null if the name is unknown
     */

    public static LanguageFeature getFeature(StructuredQName name) {
        return features.get(name);
    }

}

