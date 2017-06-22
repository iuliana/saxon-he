////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.ReverseArrayIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;

import java.util.ArrayList;
import java.util.List;


/**
 * A sequence value implemented extensionally. That is, this class represents a sequence
 * by allocating memory to each item in the sequence.
 */

public class SequenceExtent<T extends Item> implements GroundedValue {
    private T[] value;
    private int start = 0;  // zero-based offset of the start
    private int end;        // the 0-based index of the first item that is NOT included
                            // If start=0 this is the length of the sequence
    /*@Nullable*/ private ItemType itemType = null;   // memoized

//    private static int instances = 0;

    /**
     * Construct an sequence from an array of items. Note, the array of items is used as is,
     * which means the caller must not subsequently change its contents.
     *
     * @param items the array of items to be included in the sequence
     */

    public SequenceExtent(/*@NotNull*/ T[] items) {
        value = items;
        end = items.length;
    }

    /**
     * Construct a SequenceExtent from part of an array of items
     * @param value The array
     * @param start zero-based offset of the first item in the array
     * that is to be included in the new SequenceExtent
     * @param length The number of items in the new SequenceExtent
     */

    public SequenceExtent(T[] value, int start, int length) {
        this.value = value;
        this.start = start;
        end = this.start + length;
    }


    /**
     * Construct a SequenceExtent as a view of another SequenceExtent
     * @param ext The existing SequenceExtent
     * @param start zero-based offset of the first item in the existing SequenceExtent
     * that is to be included in the new SequenceExtent
     * @param length The number of items in the new SequenceExtent
     */

    public SequenceExtent(/*@NotNull*/ SequenceExtent<T> ext, int start, int length) {
        value = ext.value;
        this.start = ext.start + start;
        end = this.start + length;
    }

    /**
     * Construct a SequenceExtent from a List. The members of the list must all
     * be Items
     *
     * @param list the list of items to be included in the sequence
     */

    public SequenceExtent(/*@NotNull*/ List<? extends T> list) {
        @SuppressWarnings({"unchecked"})
        T[] array = (T[])new Item[list.size()];
        value = list.toArray(array);
        end = value.length;
    }

    /**
     * Construct a sequence containing all the items in a SequenceIterator.
     *
     * @exception net.sf.saxon.trans.XPathException if reading the items using the
     *     SequenceIterator raises an error
     * @param iter The supplied sequence of items. This must be positioned at
     *     the start, so that hasNext() returns true if there are any nodes in
     *      the node-set, and next() returns the first node.
     */

    public SequenceExtent(/*@NotNull*/ SequenceIterator<T> iter) throws XPathException {
        if ((iter.getProperties() & SequenceIterator.LAST_POSITION_FINDER) == 0) {
            List<T> list = new ArrayList<T>(20);
            while (true) {
                T it = iter.next();
                if (it == null) {
                    break;
                }
                list.add(it);
            }
            @SuppressWarnings({"unchecked"})
            T[] array = (T[])new Item[list.size()];
            try {
                value = list.toArray(array);
            } catch (ArrayStoreException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
            end = value.length;
        } else {
            end = ((LastPositionFinder)iter).getLength();
            @SuppressWarnings({"unchecked"})
            T[] array = (T[])new Item[end];
            value = array;
            int i = 0;
            while (true) {
                T it = iter.next();
                if (it == null) {
                    break;
                }
                value[i++] = it;
            }
        }
    }

    /**
     * Factory method to make a Value holding the contents of any SequenceIterator
     * @param iter a Sequence iterator that will be consumed to deliver the items in the sequence
     * @return a ValueRepresentation holding the items delivered by the SequenceIterator. If the
     * sequence is empty the result will be an instance of {@link EmptySequence}. If it is of length
     * one, the result will be an {@link Item}. In all other cases, it will be an instance of
     * {@link SequenceExtent}.
     * @throws net.sf.saxon.trans.XPathException if an error occurs processing the values from
     * the iterator.
     */

    /*@NotNull*/
    public static <T extends Item> GroundedValue makeSequenceExtent(/*@NotNull*/ SequenceIterator<T> iter) throws XPathException {
        if ((iter.getProperties() & SequenceIterator.GROUNDED) != 0) {
            return ((GroundedIterator<T>)iter).materialize();
        }
        SequenceExtent<T> extent = new SequenceExtent<T>(iter);
        return extent.reduce();
    }

    /**
     * Factory method to make a Value holding the contents of any List of items
     * @param input a List containing the items in the sequence
     * @return a ValueRepresentation holding the items in the list. If the
     * sequence is empty the result will be an instance of {@link EmptySequence}. If it is of length
     * one, the result will be an {@link Item}. In all other cases, it will be an instance of
     * {@link SequenceExtent}.
     */

    public static <T extends Item> Sequence makeSequenceExtent(/*@NotNull*/ List<T> input) {
        int len = input.size();
        if (len==0) {
            return EmptySequence.getInstance();
        } else if (len==1) {
            return input.get(0);
        } else if (allAtomicLiterals(input)) {
            AtomicValue[] a = new AtomicValue[input.size()];
            input.toArray(a);
            return new AtomicArray(a);
        } else {
            return new SequenceExtent<T>(input);
        }
    }

    private static boolean allAtomicLiterals(List<? extends Item> input) {
        for (Item item : input) {
            if (!(item instanceof AtomicValue)) {
                return false;
            }
        }
        return true;
    }

    public String getStringValue() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    public CharSequence getStringValueCS() throws XPathException {
        return SequenceTool.getStringValue(this);
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
        return itemAt(0);
    }

    /**
     * Simplify this SequenceExtent
     * @return a Value holding the items delivered by the SequenceIterator. If the
     * sequence is empty the result will be an instance of {@link EmptySequence}. If it is of length
     * one, the result will be an {@link AtomicValue} or a {@link SingletonItem}.
     * In all other cases, the {@link SequenceExtent} will be returned unchanged.
     */

//    public Value<T> simplify() {
//        int n = getLength();
//        if (n == 0) {
//            return EmptySequence.getInstance();
//        } else if (n == 1) {
//            return SequenceTool.asValue(itemAt(0));
//        } else {
//            return this;
//        }
//    }

    /**
     * Reduce a value to its simplest form. If the value is a closure or some other form of deferred value
     * such as a FunctionCallPackage, then it is reduced to a SequenceExtent. If it is a SequenceExtent containing
     * a single item, then it is reduced to that item. One consequence that is exploited by class FilterExpression
     * is that if the value is a singleton numeric value, then the result will be an instance of NumericValue
     */

//    public Value<T> reduce() {
//        return simplify();
//    }

    /**
     * Get the number of items in the sequence
     *
     * @return the number of items in the sequence
     */

    public int getLength() {
        return end - start;
    }

    /**
     * Determine the cardinality
     *
     * @return the cardinality of the sequence, using the constants defined in
     *      net.sf.saxon.value.Cardinality
     * @see net.sf.saxon.value.Cardinality
     */

    public int getCardinality() {
        switch (end - start) {
            case 0:
                return StaticProperty.EMPTY;
            case 1:
                return StaticProperty.EXACTLY_ONE;
            default:
                return StaticProperty.ALLOWS_ONE_OR_MORE;
        }
    }

    /**
     * Get the (lowest common) item type
     *
     * @return integer identifying an item type to which all the items in this
     *      sequence conform
     * @param th the type hierarchy cache. If null, the returned value may be less precise
     */

    /*@NotNull*/
    public ItemType getItemType(/*@Nullable*/ TypeHierarchy th) {
        ItemType it = itemType;
        if (it != null) {
            // only calculate it the first time
            return it;
        } else if (end == start) {
            it = AnyItemType.getInstance();
        } else if (th == null) {
            for (int i=start; i<end; i++) {
                if (value[i] instanceof NodeInfo) {
                    return getItemType(((NodeInfo)value[i]).getConfiguration().getTypeHierarchy());
                }
            }
            it = Type.getItemType(value[start], null);
            for (int i=start+1; i<end; i++) {
                if (it == AnyItemType.getInstance()) {
                    // make a quick exit
                    return it;
                }
                it = Type.getCommonSuperType(it, Type.getItemType(value[i], null), th);
            }
        } else {
            it = Type.getItemType(value[start], th);
            for (int i=start+1; i<end; i++) {
                if (it == AnyItemType.getInstance()) {
                    // make a quick exit
                    return it;
                }
                it = Type.getCommonSuperType(it, Type.getItemType(value[i], th), th);
            }
        }
        return (itemType = it);
    }

    /**
     * Get the n'th item in the sequence (starting with 0 as the first item)
     *
     * @param n the position of the required item
     * @return the n'th item in the sequence
     */

    /*@Nullable*/ public T itemAt(int n) {
        if (n<0 || n>=getLength()) {
            return null;
        } else {
            return value[start+n];
        }
    }

    /**
     * Swap two items (needed to support sorting)
     *
     * @param a the position of the first item to be swapped
     * @param b the position of the second item to be swapped
     */

    public void swap(int a, int b) {
        T temp = value[start+a];
        value[start+a] = value[start+b];
        value[start+b] = temp;
    }

    /**
     * Return an iterator over this sequence.
     *
     * @return the required SequenceIterator, positioned at the start of the
     *     sequence
     */

    /*@NotNull*/ public ArrayIterator<T> iterate() {
        return new ArrayIterator<T>(value, start, end);
    }

    /**
     * Return an enumeration of this sequence in reverse order (used for reverse axes)
     *
     * @return an AxisIterator that processes the items in reverse order
     */

    /*@NotNull*/ public UnfailingIterator<T> reverseIterate() {
        return new ReverseArrayIterator<T>(value, start, end);
    }

    /**
     * Get the effective boolean value
     */

    public boolean effectiveBooleanValue() throws XPathException {
        int len = getLength();
        if (len == 0) {
            return false;
        } else if (value[start] instanceof NodeInfo) {
            return true;
        } else if (len > 1) {
            // this is a type error - reuse the error messages
            return ExpressionTool.effectiveBooleanValue(iterate());
        } else {
            return ((AtomicValue)value[start]).effectiveBooleanValue();
        }
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
     * @return the required subsequence. If min is
     */

    /*@NotNull*/ public GroundedValue subsequence(int start, int length) {
        int newStart;
        if (start < 0) {
            start = 0;
        } else if (start >= end) {
            return EmptySequence.getInstance();
        }
        newStart = this.start + start;
        int newEnd;
        if (length == Integer.MAX_VALUE) {
            newEnd = end;
        } else if (length < 0) {
            return EmptySequence.getInstance();
        } else {
            newEnd = newStart + length;
            if (newEnd > end) {
                newEnd = end;
            }
        }
        return new SequenceExtent<T>(value, newStart, newEnd - newStart);
    }

    /*@NotNull*/ public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
        fsb.append('(');
        for (int i=start; i<end; i++) {
            fsb.append(value[i].toString());
            if (i != end-1) {
                fsb.append(", ");
            }
        }
        fsb.append(')');
        return fsb.toString();
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
        int len = getLength();
        if (len == 0) {
            return EmptySequence.getInstance();
        } else if (len == 1) {
            Item item = itemAt(0);
            if (item instanceof GroundedValue) {
                return ((GroundedValue)item);
            } else {
                return new SingletonItem(item);
            }
        } else {
            return this;
        }
    }
}

