////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
* An EmptySequence object represents a sequence containing no members.
*/


public final class EmptySequence<T extends Item> implements GroundedValue {

    // This class has a single instance
    /*@NotNull*/ private static EmptySequence THE_INSTANCE = new EmptySequence();


    /**
    * Private constructor: only the predefined instances of this class can be used
    */

    private EmptySequence() {}

    /**
    * Get the implicit instance of this class
     * @return the singular instances of this class: an empty sequence
     */

    /*@NotNull*/ public static <T extends Item> EmptySequence<T> getInstance() {
        return THE_INSTANCE;
    }

    public String getStringValue() throws XPathException {
        return "";
    }

    public CharSequence getStringValueCS() throws XPathException {
        return "";
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     * @throws net.sf.saxon.trans.XPathException
     *          in the situation where the sequence is evaluated lazily, and
     *          evaluation of the first item causes a dynamic error.
     */
    public Item head() throws XPathException {
        return null;
    }

    /**
    * Return an iteration over the sequence
    */

    /*@NotNull*/ public SequenceIterator<T> iterate() {
        return EmptyIterator.emptyIterator();
    }

    /**
     * Return the value in the form of an Item
     * @return the value in the form of an Item
     */

    /*@Nullable*/ public Item asItem() {
        return null;
    }

    /**
     * Determine the item type
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(/*@NotNull*/ TypeHierarchy th) {
        return ErrorType.getInstance();
    }

    /**
    * Determine the static cardinality
    */

    public int getCardinality() {
        return StaticProperty.EMPTY;
    }

    /**
     * Get the length of the sequence
     * @return always 0 for an empty sequence
     */

    public final int getLength() {
        return 0;
    }
    /**
    * Is this expression the same as another expression?
    * @throws ClassCastException if the values are not comparable
    */

    public boolean equals(/*@NotNull*/ Object other) {
        if (other != null && !(other instanceof EmptySequence)) {
            throw new ClassCastException("Cannot compare " + other.getClass() + " to empty sequence");
        }
        return true;
    }

    public int hashCode() {
        return 42;
    }

    /**
    * Get the effective boolean value - always false
    */

    public boolean effectiveBooleanValue() {
        return false;
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

    /*@Nullable*/ public T itemAt(int n) {
        return null;
    }

    /**
     * Get a subsequence of the value
     *
     *
     * @param min    the index of the first item to be included in the result, counting from zero.
     *               A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     *               sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     *               get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     *               is returned. If the value goes off the end of the sequence, the result returns items up to the end
     *               of the sequence
     * @return the required subsequence. If min is
     */

    /*@NotNull*/ public GroundedValue subsequence(int min, int length) {
        return this;
    }

    /**
     * Returns a string representation of the object.
     */
    
    /*@NotNull*/ public String toString() {
        return "()";
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
        return this;
    }
}

