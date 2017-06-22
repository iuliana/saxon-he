////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.AtomicValue;

/**
 * Interface for atomic types (these are either built-in atomic types
 * or user-defined atomic types). An AtomicType is both an ItemType (a possible type
 * for items in a sequence) and a SchemaType (a possible type for validating and
 * annotating nodes).
 */
public interface AtomicType extends SimpleType, PlainType {

    /**
     * Validate that a primitive atomic value is a valid instance of a type derived from the
     * same primitive type.
     * @param primValue the value in the value space of the primitive type.
     * @param lexicalValue the value in the lexical space. If null, the string value of primValue
     * is used. This value is checked against the pattern facet (if any)
     * @param rules
     * @return null if the value is valid; otherwise, a ValidationFailure object indicating
     * the nature of the error.
     * @throws UnsupportedOperationException in the case of an external object type
     */

    public ValidationFailure validate(AtomicValue primValue, CharSequence lexicalValue, ConversionRules rules);

    /**
     * Determine whether the atomic type is ordered, that is, whether less-than and greater-than comparisons
     * are permitted
     * @param optimistic if true, the function takes an optimistic view, returning true if ordering comparisons
     * are available for some subtype. This mainly affects xs:duration, where the function returns true if
     * optimistic is true, false if it is false.
     * @return true if ordering operations are permitted
     */

    public boolean isOrdered(boolean optimistic);

    /**
     * Determine whether the type is abstract, that is, whether it cannot have instances that are not also
     * instances of some concrete subtype
     */

    public boolean isAbstract();

    /**
     * Determine whether the atomic type is a primitive type.  The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration;
     * xs:untypedAtomic; and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     * @return true if the type is considered primitive under the above rules
     */

    public boolean isPrimitiveType();

    /**
     * Determine whether the atomic type is a built-in type. The built-in atomic types are the 41 atomic types
     * defined in XML Schema, plus xs:dayTimeDuration and xs:yearMonthDuration,
     * xs:untypedAtomic, and all supertypes of these (xs:anyAtomicType, xs:numeric, ...)
     */

    public boolean isBuiltInType();

    /**
     * Get the name of this type as a StructuredQName, unless the type is anonymous, in which case
     * return null
     * @return the name of the atomic type, or null if the type is anonymous.
     */

    public StructuredQName getTypeName();
}

