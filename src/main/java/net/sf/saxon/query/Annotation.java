////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.AtomicValue;

import java.util.List;

/**
  * This class represents an annotation that appears in a function or variable declarations
 */
public abstract class Annotation {

    public static final StructuredQName UPDATING = new StructuredQName("", NamespaceConstant.XQUERY, "updating");
    public static final StructuredQName SIMPLE = new StructuredQName("", NamespaceConstant.XQUERY, "simple");
    public static final StructuredQName PRIVATE = new StructuredQName("", NamespaceConstant.XQUERY, "private");
    public static final StructuredQName PUBLIC = new StructuredQName("", NamespaceConstant.XQUERY, "public");

    /**
     * Get the name of the annotation (a QName)
     * @return the annotation name
     */

    public abstract StructuredQName getAnnotationQName();

    /**
     * Add a value to the list of annotation parameters
     * @param value the value to be added. This will always be a string or number,
     * but Saxon enforces this only at the level of the query parser
     */

    public abstract void addAnnotationParameter(AtomicValue value);

    /**
     * Get the list of annotation parameters
     * @return the list of parameters
     */

    public abstract List<AtomicValue> getAnnotationParameters();

    /**
     * Determine whether two annotations are mutually exclusive
     * @param one the first annotation
     * @param two the second annotation
     * @return true if these two annotations cannot appear together on the same function or variable declaration
     */

    public static boolean mutuallyExclusive(Annotation one, Annotation two) {
        StructuredQName a = one.getAnnotationQName();
        StructuredQName b = two.getAnnotationQName();
        return (
                (a.equals(PRIVATE) && b.equals(PUBLIC)) ||
                (a.equals(PUBLIC) && b.equals(PRIVATE)) ||
                (a.equals(UPDATING) && b.equals(SIMPLE)) ||
                (a.equals(SIMPLE) && b.equals(UPDATING))
               );

    }
}

