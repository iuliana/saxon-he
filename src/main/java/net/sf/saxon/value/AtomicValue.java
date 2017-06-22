////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.NoDynamicContextException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.type.*;

import static net.sf.saxon.functions.Sum.SUM_EX_CODE;
import static net.sf.saxon.util.ExceptionBuilder.newEx;

/**
 * The AtomicValue class corresponds to the concept of an atomic value in the
 * XPath 2.0 data model. Atomic values belong to one of the 19 primitive types
 * defined in XML Schema; or they are of type xs:untypedAtomic; or they are
 * "external objects", representing a Saxon extension to the XPath 2.0 type system.
 * <p/>
 * The AtomicValue class contains some methods that are suitable for applications
 * to use, and many others that are designed for internal use by Saxon itself.
 * These have not been fully classified. At present, therefore, none of the methods on this
 * class should be considered to be part of the public Saxon API.
 * <p/>
 *
 * @author Michael H. Kay
 */

public abstract class AtomicValue extends AbstractItem
        implements AtomicSequence, ConversionResult, IdentityComparable {

    protected AtomicType typeLabel;

    /**
     * To implement {@link net.sf.saxon.om.Sequence}, this method returns the item itself
     * @return this item
     */

    public final AtomicValue head() {
        return this;
    }

    /**
     * To implement {@link net.sf.saxon.om.Sequence}, this method returns a singleton iterator
     * that delivers this item in the form of a sequence
     * @return a singleton iterator that returns this item
     */

    public final SequenceIterator<AtomicValue> iterate() {
        return SingletonIterator.makeIterator(this);
    }

    /**
     * Set the type label on this atomic value. Note that this modifies the value, so it must only called
     * if the caller is confident that the value is not shared. In other cases,
     * use {@link #copyAsSubType(net.sf.saxon.type.AtomicType)}
     *
     * @param type the type label to be set
     */

    public void setTypeLabel(AtomicType type) {
        typeLabel = type;
    }


    /**
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * An implementation must be provided for all atomic types.
     * <p/>
     * <p>In the case of data types that are partially ordered, the returned Comparable extends the standard
     * semantics of the compareTo() method by returning the value {@link SequenceTool#INDETERMINATE_ORDERING} when there
     * is no defined order relationship between two given values. This value is also returned when two values
     * of different types are compared.</p>
     *
     * @return a Comparable that follows XML Schema comparison rules
     */

    public abstract Comparable getSchemaComparable();

    /**
     * Get an object value that implements the XPath equality and ordering comparison semantics for this value.
     * If the ordered parameter is set to true, the result will be a Comparable and will support a compareTo()
     * method with the semantics of the XPath lt/gt operator, provided that the other operand is also obtained
     * using the getXPathComparable() method. In all cases the result will support equals() and hashCode() methods
     * that support the semantics of the XPath eq operator, again provided that the other operand is also obtained
     * using the getXPathComparable() method. A context argument is supplied for use in cases where the comparison
     * semantics are context-sensitive, for example where they depend on the implicit timezone or the default
     * collation.
     * @param ordered true if an ordered comparison is required. In this case the result is null if the
     * type is unordered; in other cases the returned value will be a Comparable.
     * @param collator the collation to be used when comparing strings
     * @param context the XPath dynamic evaluation context, used in cases where the comparison is context
     * sensitive
     * @return an Object whose equals() and hashCode() methods implement the XPath comparison semantics
     *         with respect to this atomic value. If ordered is specified, the result will either be null if
     *         no ordering is defined, or will be a Comparable
     * @throws NoDynamicContextException if the comparison depends on dynamic context information that
     * is not available, for example implicit timezone
     */

    public abstract Object getXPathComparable(boolean ordered, StringCollator collator, XPathContext context)
            throws NoDynamicContextException;

    /**
     * The equals() methods on atomic values is defined to follow the semantics of eq when applied
     * to two atomic values. When the other operand is not an atomic value, the result is undefined
     * (may be false, may be an exception). When the other operand is an atomic value that cannot be
     * compared with this one, the method must throw a ClassCastException.
     *
     * <p>The hashCode() method is consistent with equals().</p>
     * @param o the other value
     * @return true if the other operand is an atomic value and the two values are equal as defined
     * by the XPath eq operator
     */

    public abstract boolean equals(Object o);

    /**
     * Determine whether two atomic values are identical, as determined by XML Schema rules. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * <p>Note that even this check ignores the type annotation of the value. The integer 3 and the short 3
     * are considered identical, even though they are not fully interchangeable. "Identical" means the
     * same point in the value space, regardless of type annotation.</p>
     * <p>NaN is identical to itself.</p>
     *
     *
     * @param v the other value to be compared with this one
     * @return true if the two values are identical, false otherwise.
     */

    public boolean isIdentical(/*@NotNull*/ AtomicValue v) {
        // default implementation
        return getSchemaComparable().equals(v.getSchemaComparable());
    }

    /**
     * Determine whether two IdentityComparable values are identical. This is a stronger
     * test than equality (even schema-equality); for example two dateTime values are not identical unless
     * they are in the same timezone.
     * @param other
     * @return true if the two values are identical, false otherwise
     */

    public boolean isIdentical(IdentityComparable other) {
        if(other instanceof AtomicValue) {
            return isIdentical((AtomicValue) other);
        } else {
            return false;
        }
    }

    /**
     * Get the value of the item as a CharSequence. This is in some cases more efficient than
     * the version of the method that returns a String.
     */

    public final CharSequence getStringValueCS() {
        CharSequence cs = getPrimitiveStringValue();
        try {
            return typeLabel.postprocess(cs);
        } catch (XPathException err) {
            // Ignore any XPath errors that occur during postprocessing
            return cs;
        }
    }

    /**
     * Get the canonical lexical representation as defined in XML Schema. This is not always the same
     * as the result of casting to a string according to the XPath rules.
     *
     * @return the canonical lexical representation if defined in XML Schema; otherwise, the result
     *         of casting to string according to the XPath 2.0 rules
     */
    public CharSequence getCanonicalLexicalRepresentation() {
        return getStringValueCS();
    }

    /**
     * Process the instruction, without returning any tail calls
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     * @throws XPathException if the current receiver fails for any reason, for example
     * with a serialization error due to invalid characters in the content
     */

    public void process(/*@NotNull*/ XPathContext context) throws XPathException {
        context.getReceiver().append(this, 0, NodeInfo.ALL_NAMESPACES);
    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * Values, but its real benefits come for a sequence Value stored extensionally
     * (or for a MemoClosure, once all the values have been read)
     *
     * @param n position of the required item, counting from zero.
     * @return the n'th item in the sequence, where the first item in the sequence is
     *         numbered zero. If n is negative or >= the length of the sequence, returns null.
     */

    /*@Nullable*/ public final AtomicValue itemAt(int n) {
        return (n == 0 ? this : null);
    }


    /**
     * Determine the data type of the value
     * @return the type annotation of the atomic value
     */

    /*@NotNull*/
    public final AtomicType getItemType() {
        return typeLabel;
    }

    /**
     * Determine the primitive type of the value. This delivers the same answer as
     * getItemType().getPrimitiveItemType(). The primitive types are
     * the 19 primitive types of XML Schema, plus xs:integer, xs:dayTimeDuration and xs:yearMonthDuration,
     * and xs:untypedAtomic. For external objects, the result is xs:anyAtomicType.
     *
     * @return the primitive type
     */

    public abstract BuiltInAtomicType getPrimitiveType();

    /**
     * Determine the static cardinality
     *
     * @return code identifying the cardinality
     * @see net.sf.saxon.value.Cardinality
     */

    public final int getCardinality() {
        return StaticProperty.EXACTLY_ONE;
    }

    /**
     * Create a copy of this atomic value, with a different type label
     *
     * @param typeLabel the type label of the new copy. The caller is responsible for checking that
     *                  the value actually conforms to this type.
     * @return the copied value
     */

    public abstract AtomicValue copyAsSubType(AtomicType typeLabel);

    /**
     * Test whether the value is the special value NaN
     * @return true if the value is float NaN or double NaN or precisionDecimal NaN; otherwise false
     */

    public boolean isNaN() {
        return false;
    }

    /**
     * Convert the value to a string, using the serialization rules.
     * For atomic values this is the same as a cast; for sequence values
     * it gives a space-separated list. This method is refined for AtomicValues
     * so that it never throws an Exception.
     */

    public final String getStringValue() {
        return getStringValueCS().toString();
    }

    /**
     * Convert the value to a string, using the serialization rules for the primitive type.
     * This is the result of conversion to a string except that postprocessing defined by the
     * saxon:preprocess facet is not (yet) applied.
     * @return the value converted to a string according to the rules for the primitive type
     */

    protected abstract CharSequence getPrimitiveStringValue();


    /**
     * Get the effective boolean value of the value
     *
     * @return true, unless the value is boolean false, numeric zero, or
     *         zero-length string
     * @throws XPathException if effective boolean value is not defined for this type (the default behaviour)
     */
    public boolean effectiveBooleanValue() throws XPathException {
        throw  newEx("Effective boolean value is not defined for an atomic value of type " +
                Type.displayTypeName(this), true, SUM_EX_CODE);
        // unless otherwise specified in a subclass
    }

    /**
     * Method to extract components of a value. Implemented by some subclasses,
     * but defined at this level for convenience
     *
     * @param component identifies the required component, as a constant defined in class
     *                  {@link net.sf.saxon.functions.Component}, for example {@link net.sf.saxon.functions.Component#HOURS}
     * @return the value of the requested component of this value
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
     * @throws UnsupportedOperationException if applied to a value of a type that has no components
     */

    public AtomicValue getComponent(int component) throws XPathException {
        throw new UnsupportedOperationException("Data type does not support component extraction");
    }

    /**
     * Check statically that the results of the expression are capable of constructing the content
     * of a given schema type.
     *
     * @param parentType The schema type
     * @param env        the static context
     * @param whole      true if this atomic value accounts for the entire content of the containing node
     * @throws net.sf.saxon.trans.XPathException
     *          if the expression doesn't match the required content type
     */

    public void checkPermittedContents(/*@NotNull*/ SchemaType parentType, /*@NotNull*/ StaticContext env, boolean whole) throws XPathException {
        if (whole) {
            SimpleType stype = null;
            if (parentType instanceof SimpleType) {
                stype = (SimpleType)parentType;
            } else if (parentType instanceof ComplexType && ((ComplexType)parentType).isSimpleContent()) {
                stype = ((ComplexType)parentType).getSimpleContentType();
            }
            if (stype != null && !stype.isNamespaceSensitive()) {
                // Can't validate namespace-sensitive content statically
                ValidationFailure err = stype.validateContent(
                        getStringValueCS(), null, env.getConfiguration().getConversionRules());
                if (err != null) {
                    throw err.makeException();
                }
                return;
            }
        }
        if (parentType instanceof ComplexType &&
                !((ComplexType)parentType).isSimpleContent() &&
                !((ComplexType)parentType).isMixedContent() &&
                !Whitespace.isWhite(getStringValueCS())) {
            XPathException err = new XPathException("Complex type " + parentType.getDescription() +
                    " does not allow text content " +
                    Err.wrap(getStringValueCS()));
            err.setIsTypeError(true);
            throw err;
        }
    }


    /**
     * Calling this method on a ConversionResult returns the AtomicValue that results
     * from the conversion if the conversion was successful, and throws a ValidationException
     * explaining the conversion error otherwise.
     * <p/>
     * <p>Use this method if you are calling a conversion method that returns a ConversionResult,
     * and if you want to throw an exception if the conversion fails.</p>
     *
     * @return the atomic value that results from the conversion if the conversion was successful
     */

    /*@NotNull*/ public AtomicValue asAtomic() {
        return this;
    }

    /**
     * Get string value. In general toString() for an atomic value displays the value as it would be
     * written in XPath: that is, as a literal if available, or as a call on a constructor function
     * otherwise.
     */

    public String toString() {
        return typeLabel.toString() + " (\"" + getStringValueCS() + "\")";
    }

}

