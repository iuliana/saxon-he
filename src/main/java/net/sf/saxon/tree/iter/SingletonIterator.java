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
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.value.SingletonItem;


/**
* SingletonIterator: an iterator over a sequence of zero or one values
*/

public class SingletonIterator<T extends Item> implements SequenceIterator<T>, UnfailingIterator<T>,
        ReversibleIterator<T>, LastPositionFinder<T>, GroundedIterator<T>, LookaheadIterator<T> {

    private T item;
    private int position = 0;

    /**
     * Private constructor: external classes should use the factory method
     * @param value the item to iterate over
     */

    private SingletonIterator(T value) {
        this.item = value;
    }

   /**
    * Factory method.
    * @param item the item to iterate over
    * @return a SingletonIterator over the supplied item, or an EmptyIterator
    * if the supplied item is null.
    */

    /*@NotNull*/ public static <T extends Item> UnfailingIterator<T> makeIterator(/*@Nullable*/ T item) {
       if (item==null) {
           return EmptyIterator.emptyIterator();
       } else {
           return new SingletonIterator<T>(item);
       }
   }

    /**
     * Determine whether there are more items to come. Note that this operation
     * is stateless and it is not necessary (or usual) to call it before calling
     * next(). It is used only when there is an explicit need to tell if we
     * are at the last element.
     *
     * @return true if there are more items
     */

    public boolean hasNext() {
        return position == 0;
    }

    /*@Nullable*/ public T next() {
        if (position == 0) {
            position = 1;
            return item;
        } else if (position == 1) {
            position = -1;
            return null;
        } else {
            return null;
        }
    }

    /*@Nullable*/ public T current() {
        if (position == 1) {
            return item;
        } else {
            return null;
        }
    }

    /**
     * Return the current position in the sequence.
     * @return 0 before the first call on next(); 1 before the second call on next(); -1 after the second
     * call on next().
     */
    public int position() {
       return position;
    }

    public int getLength() {
        return 1;
    }

    public void close() {
    }

    /*@NotNull*/ public SingletonIterator<T> getAnother() {
        return new SingletonIterator<T>(item);
    }

    /*@NotNull*/ public SingletonIterator<T> getReverseIterator() {
        return new SingletonIterator<T>(item);
    }

    public T getValue() {
        return item;
    }

    /**
     * Return a Value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding Value. If the value is a closure or a function call package, it will be
     * evaluated and expanded.
     */

    /*@NotNull*/ public GroundedValue materialize() {
        if (item instanceof GroundedValue) {
            return (GroundedValue)item;
        } else {
            return new SingletonItem<T>(item);
        }
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

}

