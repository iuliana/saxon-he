////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

/**
 * A Comparer used for comparing sort keys when data-type="text". The items to be
 * compared are converted to strings, and the strings are then compared using an
 * underlying collator
 *
 * @author Michael H. Kay
 *
 */

public class TextComparer implements AtomicComparer, java.io.Serializable {

    private AtomicComparer baseComparer;

    public TextComparer(AtomicComparer baseComparer) {
        this.baseComparer = baseComparer;
    }

    /**
     * Get the underlying comparer (which doesn't do conversion to string)
     */

    public AtomicComparer getBaseComparer() {
        return baseComparer;
    }

    public StringCollator getCollator() {
        return baseComparer.getCollator();
    }

    /**
     * Supply the dynamic context in case this is needed for the comparison
     * @param context the dynamic evaluation context
     * @return either the original AtomicComparer, or a new AtomicComparer in which the context
     * is known. The original AtomicComparer is not modified
     * @throws net.sf.saxon.trans.NoDynamicContextException if the context is an "early evaluation" (compile-time) context
     */

    public AtomicComparer provideContext(XPathContext context) {
        AtomicComparer newBase = baseComparer.provideContext(context);
        if (newBase != baseComparer) {
            return new TextComparer(newBase);
        } else {
            return this;
        }
    }


    /**
    * Compare two Items by converting them to strings and comparing the string values.
    * @param a the first Item to be compared.
    * @param b the second Item to be compared.
    * @return <0 if a<b, 0 if a=b, >0 if a>b
    * @throws ClassCastException if the objects are not Items, or are items that cannot be convered
    * to strings (e.g. QNames)
    */

    public int compareAtomicValues(AtomicValue a, AtomicValue b) throws ClassCastException, NoDynamicContextException {
        return baseComparer.compareAtomicValues(toStringValue(a), toStringValue(b));
    }

    private StringValue toStringValue(AtomicValue a) {
        if (a instanceof StringValue) {
            return ((StringValue)a);
        } else {
            return new StringValue((a == null ? "" : a.getStringValue()));
        }
    }

    /**
     * Compare two AtomicValue objects for equality according to the rules for their data type. UntypedAtomic
     * values are compared by converting to the type of the other operand.
     *
     * @param a the first object to be compared.
     * @param b the second object to be compared.
     * @return true if the values are equal, false if not
     * @throws ClassCastException if the objects are not comparable
     */

    public boolean comparesEqual(AtomicValue a, /*@NotNull*/ AtomicValue b) throws NoDynamicContextException {
        return compareAtomicValues(a, b) == 0;
    }

    /**
     * Get a comparison key for an object. This must satisfy the rule that if two objects are equal
     * according to the XPath eq operator, then their comparison keys are equal according to the Java
     * equals() method, and vice versa. There is no requirement that the
     * comparison keys should reflect the ordering of the underlying objects.
     */

    public ComparisonKey getComparisonKey(AtomicValue a) throws NoDynamicContextException{
        return baseComparer.getComparisonKey(toStringValue(a));
    }

}

