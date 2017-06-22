////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.EmptySequence;

/**
 * EmptyIterator: an iterator over an empty sequence. Since such an iterator has no state,
 * only one instance is required; therefore a singleton instance is available via the static
 * getInstance() method.
 */

public class EmptyIterator<T extends Item> implements SequenceIterator<T>,
        ReversibleIterator<T>, LastPositionFinder<T>, GroundedIterator<T>,
        LookaheadIterator<T>, UnfailingIterator<T> {

    /*@NotNull*/ private static EmptyIterator<Item> theInstance = new EmptyIterator<Item>();

    /**
     * Get an EmptyIterator, an iterator over an empty sequence.
     * @return an EmptyIterator (in practice, this always returns the same
     *     one)
     */
    /*@NotNull*/ public static EmptyIterator getInstance() {
        return theInstance;
    }


    public static <T extends Item> EmptyIterator<T> emptyIterator() {
        return (EmptyIterator<T>) theInstance;
    }

    /**
     * Protected constructor
     */

    protected EmptyIterator() {}




    /**
     * Get the next item.
     * @return the next item. For the EmptyIterator this is always null.
     */
    /*@Nullable*/ public T next() {
        return null;
    }

    /**
     * Get the current item, that is, the item returned by the most recent call of next().
     * @return the current item. For the EmptyIterator this is always null.
     */
    /*@Nullable*/ public T current() {
        return null;
    }

    /**
     * Get the position of the current item.
     * @return the position of the current item. For the EmptyIterator this is always zero
     * (whether or not the next() method has been called).
     */
    public int position() {
        return 0;
    }

    /**
     * Get the position of the last item in the sequence.
     * @return the position of the last item in the sequence, always zero in
     *     this implementation
     */
    public int getLength() {
        return 0;
    }

    public void close() {
    }

    /**
     * Return the atomized value of the current node.
     *
     * @return the atomized value.
     * @throws NullPointerException always, because there is no current node
     */

    /*@NotNull*/ public Sequence atomize() {
        throw new NullPointerException();
    }

    /**
     * Return the string value of the current node.
     *
     * @return the string value, as an instance of CharSequence.
     * @throws NullPointerException if there is no current node
     */

    /*@NotNull*/ public CharSequence getStringValue() {
        throw new NullPointerException();
    }

    /**
     * Get another iterator over the same items, positioned at the start.
     * @return another iterator over an empty sequence (in practice, it
     *     returns the same iterator each time)
     */
    /*@NotNull*/ public EmptyIterator<T> getAnother() {
        return this;
    }

    /**
     * Indicate that any nodes returned in the sequence will be atomized. This
     * means that if it wishes to do so, the implementation can return the typed
     * values of the nodes rather than the nodes themselves. The implementation
     * is free to ignore this hint.
     * @param atomizing true if the caller of this iterator will atomize any
     * nodes that are returned, and is therefore willing to accept the typed
     * value of the nodes instead of the nodes themselves.
     */

    //public void setIsAtomizing(boolean atomizing) {}

    /**
     * Get another iterator over the same items, in reverse order.
     * @return a reverse iterator over an empty sequence (in practice, it
     *     returns the same iterator each time)
     */
    /*@NotNull*/ public EmptyIterator<T> getReverseIterator() {
        return this;
    }

    /**
     * Get properties of this iterator, as a bit-significant integer.
     *
     * @return the properties of this iterator. This will be some combination of
     *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
     *         and {@link #LOOKAHEAD}. It is always
     *         acceptable to return the value zero, indicating that there are no known special properties.
     *         It is acceptable for the properties of the iterator to change depending on its state.
     */

    public int getProperties() {
        return GROUNDED | LAST_POSITION_FINDER | LOOKAHEAD;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator. This should be an "in-memory" value, not a Closure.
     *
     * @return the corresponding Value
     */

    public GroundedValue materialize() {
        return EmptySequence.getInstance();
    }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more nodes
     */

    public boolean hasNext() {
        return false;
    }

}

