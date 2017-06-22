////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.RangeIterator;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * This class represents a sequence of consecutive ascending integers, for example 1 to 50.
 * The integers must be within the range of a Java long.
 */

public class IntegerRange implements AtomicSequence, GroundedValue {

    public long start;
    public long end;

    /**
     * Construct an integer range expression
     * @param start the first integer in the sequence (inclusive)
     * @param end the last integer in the sequence (inclusive). Must be >= start
     */

    public IntegerRange(long start, long end) {
        if (end < start) {
            throw new IllegalArgumentException("end < start in IntegerRange");
        }
        if (end - start > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Maximum length of sequence in Saxon is " + Integer.MAX_VALUE);
        }
        this.start = start;
        this.end = end;
    }

    /**
     * Get the first integer in the sequence (inclusive)
     * @return the first integer in the sequence (inclusive)
     */

    public long getStart() {
        return start;
    }

   /**
     * Get the last integer in the sequence (inclusive)
     * @return the last integer in the sequence (inclusive)
     */

    public long getEnd() {
        return end;
    }



    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/ public SequenceIterator<IntegerValue> iterate() {
        try {
            return new RangeIterator(start, end);
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Determine the data type of the items in the expression, if possible
     *
     * @return AnyItemType (not known)
     * @param th the type hierarchy cache
     */

    /*@NotNull*/ public ItemType getItemType(TypeHierarchy th) {
        return BuiltInAtomicType.INTEGER;
    }

    /**
     * Determine the cardinality
     */

    public int getCardinality() {
        return StaticProperty.ALLOWS_MANY;
    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * Values, but its real benefits come for a sequence Value stored extensionally
     * (or for a MemoClosure, once all the values have been read)
     */

    /*@Nullable*/ public IntegerValue itemAt(int n) {
        if (n < 0 || n > (end-start)) {
            return null;
        }
        return Int64Value.makeIntegerValue(start + n);
    }


    /**
     * Get a subsequence of the value
     *
     *
     * @param start  the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence.
     */

    /*@NotNull*/ public GroundedValue subsequence(int start, int length) {
        if (length <= 0) {
            return EmptySequence.getInstance();
        }
        long newStart = this.start + (start > 0 ? start : 0);
        long newEnd = newStart + length - 1;
        if (newEnd > end) {
            newEnd = end;
        }
        if (newEnd >= newStart) {
            return new IntegerRange(newStart, newEnd);
        } else {
            return EmptySequence.getInstance();
        }
    }

    /**
     * Get the length of the sequence
     */

    public int getLength() {
        return (int)(end - start + 1);
    }

    public AtomicValue head() {
        return new Int64Value(start);
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
     * Get a Comparable value that implements the XML Schema ordering comparison semantics for this value.
     * The default implementation is written to compare sequences of atomic values.
     * This method is overridden for AtomicValue and its subclasses.
     * <p/>
     * <p>In the case of data types that are partially ordered, the returned Comparable extends the standard
     * semantics of the compareTo() method by returning the value {@link net.sf.saxon.om.SequenceTool#INDETERMINATE_ORDERING} when there
     * is no defined order relationship between two given values.</p>
     *
     * @return a Comparable that follows XML Schema comparison rules
     */
    public Comparable getSchemaComparable() {
        try {
            return new AtomicArray(iterate()).getSchemaComparable();
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    public CharSequence getStringValueCS() {
        try {
            return SequenceTool.getStringValue(this);
        } catch (XPathException err) {
            throw new AssertionError(err);
        }
    }

    public String getStringValue() {
        return getStringValueCS().toString();
    }

    public boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(iterate());
    }

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of SingletonItem. Otherwise, the result will typically be unchanged.
     *
     * @return the simplified sequence
     */
    public GroundedValue reduce() {
        if (start == end) {
            return itemAt(0);
        } else {
            return this;
        }
    }
}

