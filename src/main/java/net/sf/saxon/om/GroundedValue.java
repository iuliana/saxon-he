////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.trans.XPathException;

/**
 * A value that exists in memory and that can be directly addressed
 */
public interface GroundedValue extends Sequence {

    /**
     * Get the n'th item in the value, counting from 0
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */

    public Item itemAt(int n);

    /**
     * Get a subsequence of the value
     *
     * @param start the index of the first item to be included in the result, counting from zero.
     * A negative value is taken as zero. If the value is beyond the end of the sequence, an empty
     * sequence is returned
     * @param length the number of items to be included in the result. Specify Integer.MAX_VALUE to
     * get the subsequence up to the end of the base sequence. If the value is negative, an empty sequence
     * is returned. If the value goes off the end of the sequence, the result returns items up to the end
     * of the sequence
     * @return the required subsequence.
     */

    public GroundedValue subsequence(int start, int length);

    /**
     * Get the size of the value (the number of items)
     * @return the number of items in the sequence
     */

    public int getLength();

    /**
     * Get the effective boolean value of this sequence
     * @return the effective boolean value
     * @throws XPathException if the sequence has no effective boolean value (for example a sequence of two integers)
     */

    public boolean effectiveBooleanValue() throws XPathException;

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     * @return the string value of the sequence.
     * @throws XPathException if the sequence contains items that have no string value (for example, function items)
     */

    public String getStringValue() throws XPathException;

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     * @return the string value of the sequence.
     * @throws XPathException if the sequence contains items that have no string value (for example, function items)
     */

    public CharSequence getStringValueCS() throws XPathException;

    /**
     * Reduce the sequence to its simplest form. If the value is an empty sequence, the result will be
     * EmptySequence.getInstance(). If the value is a single atomic value, the result will be an instance
     * of AtomicValue. If the value is a single item of any other kind, the result will be an instance
     * of SingletonItem. Otherwise, the result will typically be unchanged.
     * @return the simplified sequence
     */

    public GroundedValue reduce();




}

