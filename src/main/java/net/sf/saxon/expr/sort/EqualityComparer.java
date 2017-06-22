////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.value.AtomicValue;

/**
 * A comparer for comparing two atomic values where (a) equals is defined, and is implemented
 * using the Java equals() method, and (b) ordering is not defined, and results in a dynamic error.
 */
public class EqualityComparer implements AtomicComparer {

    public static final EqualityComparer THE_INSTANCE = new EqualityComparer();

    /**
     * Get the singleton instance of this class
     * @return  the singleton instance of this class
     */
    public static EqualityComparer getInstance() {
        return THE_INSTANCE;
    }

    private EqualityComparer() {}

    public StringCollator getCollator() {
        return null;
    }

    /**
     * Supply the dynamic context in case this is needed for the comparison
     * @param context the dynamic evaluation context
     * @return either the original AtomicComparer, or a new AtomicComparer in which the context
     * is known. The original AtomicComparer is not modified
     */
    public AtomicComparer provideContext(XPathContext context) {
        return this;
    }

    /**
     * Compare two AtomicValue objects according to the rules for their data type. UntypedAtomic
     * values are compared as if they were strings; if different semantics are wanted, the conversion
     * must be done by the caller.
     *
     * @param a the first object to be compared. It is intended that this should be an instance
     *          of AtomicValue, though this restriction is not enforced. If it is a StringValue, the
     *          collator is used to compare the values, otherwise the value must implement the java.util.Comparable
     *          interface.
     * @param b the second object to be compared. This must be comparable with the first object: for
     *          example, if one is a string, they must both be strings.
     * @return <0 if a<b, 0 if a=b, >0 if a>b
     * @throws ClassCastException if the objects are not comparable
     */
    public int compareAtomicValues(AtomicValue a, AtomicValue b) {
        throw new ClassCastException("Values are not comparable");
    }

    /**
     * Compare two AtomicValue objects for equality according to the rules for their data type. UntypedAtomic
     * values are compared by converting to the type of the other operand.
     * @param a the first object to be compared. This must be an AtomicValue and it must implement
     * equals() with context-free XPath comparison semantics
     * @param b the second object to be compared. This must be an AtomicValue and it must implement
     * equals() with context-free XPath comparison semantics
     * @return true if the values are equal, false if not
     * @throws ClassCastException if the objects are not comparable
     */
    public boolean comparesEqual(AtomicValue a, AtomicValue b) {
        return a.equals(b);
    }

    /**
     * Get a comparison key for an object. This must satisfy the rule that if two objects are equal,
     * then their comparison keys are equal, and vice versa. There is no requirement that the
     * comparison keys should reflect the ordering of the underlying objects.
     */
    public ComparisonKey getComparisonKey(AtomicValue a) {
        return new ComparisonKey(a.getPrimitiveType().getFingerprint(), a);
    }
}
