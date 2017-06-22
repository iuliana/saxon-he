////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.pattern.NodeTest;

/**
 * This is a marker interface that acts as a surrogate for an object representing
 * a global element or attribute declaration.
 * The real implementation of these declarations is available in the schema-aware
 * version of the Saxon product.
 */
public interface SchemaDeclaration {

    /**
     * Get the name of the schema component
     * @return the fingerprint of the component name
     */

    public int getFingerprint();

    /**
     * Get the simple or complex type associated with the element or attribute declaration
     * @return the simple or complex type
     */

    public SchemaType getType();

    /**
     * Create a NodeTest that implements the semantics of schema-element(name) or
     * schema-attribute(name) applied to this element or attribute declaration.
     */

    public NodeTest makeSchemaNodeTest();

    /**
     * Determine, in the case of an Element Declaration, whether it is nillable.
     */

    public boolean isNillable();

    /**
     * Determine, in the case of an Element Declaration, whether the declaration is abstract
     */

    public boolean isAbstract();

}

