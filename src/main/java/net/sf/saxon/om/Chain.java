////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SingletonItem;

import java.util.*;

/**
 * A chain is an implementation of Sequence that represents the concatenation of
 * a number of subsequences.
 *
 * <p>The most common use case is a recursive function that appends one item to a
 * sequence each time it is called. Each call of this function will create a Chain
 * with two subsequences, the first being a Chain and the second an individual item.
 * The design of the class is constrained by the need to handle this extreme case.</p>
 *
 * <p>Firstly, the iterator for the class cannot use simple recursion to navigate the
 * tree because it will often be too deep, causing StackOverflow. So it maintains its
 * own Stack (on the Java heap).</p>
 *
 * <p>Secondly, even using the heap will run out of space at about a million entries.
 * To prevent this, any Chains of size less than thirty items are amalgamated into
 * chunks of 30. Building larger chunks than this would cause insertion operations
 * to have linear performance (and thus the total cost of sequence construction
 * would be quadratic. The figure of 30 was chosen because elapsed time is almost
 * as good as with smaller chunks, and memory use during navigation is substantially
 * reduced.</p>
 */
public class Chain implements GroundedValue {

    private List<GroundedValue> children = new ArrayList<GroundedValue>();
    private Item[] extent = null;

    public Chain(List<GroundedValue> children) {
        this.children = children;

        int size = 0;
        boolean copy = false;
        for (GroundedValue gv : children) {
            if (gv instanceof Chain) {
                if (((Chain)gv).children.size() < 30) {
                    size += ((Chain)gv).children.size();
                    copy = true;
                } else {
                    size++;
                }
            } else {
                size++;
            }
        }
        if (copy) {
            this.children = new ArrayList<GroundedValue>(size);
            for (GroundedValue gv : children) {
                if (gv instanceof Chain) {
                    if (((Chain)gv).children.size() < 30) {
                        this.children.addAll(((Chain)gv).children);
                    } else {
                        this.children.add(gv);
                    }
                } else {
                    this.children.add(gv);
                }
            }
        } else {
            this.children = children;
        }
    }

    public Item head() throws XPathException {
        for (Sequence seq : children) {
            Item head = seq.head();
            if (head != null) {
                return head;
            }
        }
        return null;
    }

    public SequenceIterator<Item> iterate() {
        if (extent != null) {
            return new ArrayIterator<Item>(extent);
        } else {
            return new ChainIterator();
        }
    }

    /**
     * Add more items to the end of this sequence. This method must only be called while the value
     * is being constructed, since the sequence thereafter is immutable.
     * @param sequence the items to be added
     */

    public void append(GroundedValue sequence) {
        if (extent != null) {
            throw new IllegalStateException();
        }
        children.add(sequence);
    }

    /**
     * Add a single item to the end of this sequence. This method must only be called while the value
     * is being constructed, since the sequence thereafter is immutable.
     * @param item the item to be added
     */


    public void append(Item item) {
        if (extent != null) {
            throw new IllegalStateException();
        }
        if (item instanceof GroundedValue) {
            children.add((GroundedValue) item);
        } else {
            children.add(new SingletonItem(item));
        }
    }

    /**
     * Consolidate the sequence. This reduces it to a form in which the chain wraps a single sequenceExtent,
     * making it easy to perform operations such as subsequence() and itemAt() efficiently.
     */

    private void consolidate() {
        if (extent == null) {
            try {
                List<Item> content = new ArrayList<Item>();
                SequenceIterator<Item> iter = iterate();
                Item item;
                while ((item = iter.next()) != null) {
                    content.add(item);
                }
                Item[] array = new Item[content.size()];
                extent = content.toArray(array);
            } catch (XPathException e) {
                throw new AssertionError(e);
            }
        }
    }

    /**
     * Get the n'th item in the value, counting from 0
     *
     * @param n the index of the required item, with 0 representing the first item in the sequence
     * @return the n'th item if it exists, or null otherwise
     */
    public Item itemAt(int n) {
        consolidate();
        if (n >= 0 && n < extent.length) {
            return extent[n];
        } else {
            return null;
        }
    }

    /**
     * Get a subsequence of the value
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
    public GroundedValue subsequence(int start, int length) {
        consolidate();
        int newStart;
        if (start < 0) {
            start = 0;
        } else if (start >= extent.length) {
            return EmptySequence.getInstance();
        }
        newStart = start;
        int newEnd;
        if (length == Integer.MAX_VALUE) {
            newEnd = extent.length;
        } else if (length < 0) {
            return EmptySequence.getInstance();
        } else {
            newEnd = newStart + length;
            if (newEnd > extent.length) {
                newEnd = extent.length;
            }
        }
        return new SequenceExtent<Item>(extent, newStart, newEnd - newStart);
    }

    /**
     * Get the size of the value (the number of items)
     *
     * @return the number of items in the sequence
     */
    public int getLength() {
        if (extent != null) {
            return extent.length;
        }
        int n = 0;
        for (GroundedValue v : children) {
            n += v.getLength();
        }
        return n;
    }

    /**
     * Get the effective boolean value of this sequence
     *
     * @return the effective boolean value
     * @throws net.sf.saxon.trans.XPathException
     *          if the sequence has no effective boolean value (for example a sequence of two integers)
     */
    public boolean effectiveBooleanValue() throws XPathException {
        return ExpressionTool.effectiveBooleanValue(iterate());
    }

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * @return the string value of the sequence.
     * @throws net.sf.saxon.trans.XPathException
     *          if the sequence contains items that have no string value (for example, function items)
     */
    public String getStringValue() throws XPathException {
        return SequenceTool.getStringValue(this);
    }

    /**
     * Get the string value of this sequence. The string value of an item is the result of applying the string()
     * function. The string value of a sequence is the space-separated result of applying the string-join() function
     * using a single space as the separator
     *
     * @return the string value of the sequence.
     * @throws net.sf.saxon.trans.XPathException
     *          if the sequence contains items that have no string value (for example, function items)
     */
    public CharSequence getStringValueCS() throws XPathException {
        return SequenceTool.getStringValue(this);
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
        consolidate();
        return new SequenceExtent(extent);
    }

    private class ChainIterator implements SequenceIterator<Item>, GroundedIterator<Item> {

        private class ChainPosition {
            Chain chain;
            int offset;
            public ChainPosition(Chain chain, int offset) {
                this.chain = chain;
                this.offset = offset;
            }
        }

        private Queue<SequenceIterator> queue = new LinkedList<SequenceIterator>();

        private Stack<ChainPosition> stack;
        private Item current;
        private int position = 0;

        public ChainIterator() {
            stack = new Stack<ChainPosition>();
            stack.push(new ChainPosition(Chain.this, 0));
        }


        /**
         * Get the next item in the sequence.
         * <p>The coding of this method is designed to avoid recursion, since it is not uncommon for the tree
         * of Chain objects to be as deep as the length of the sequence it represents, and this inevitably
         * leads to stack overflow. So the method maintains its own stack (on the Java heap).</p>
         *
         * @return the next item, or null if there are no more items.
         */

        public Item next() throws XPathException {

            // If there are iterators on the queue waiting to be processed, then take the first
            // item from the first iterator on the queue.
            while (!queue.isEmpty()) {
                SequenceIterator ui = queue.peek();
                while (ui != null) {
                    current = ui.next();
                    if (current != null) {
                        position++;
                        return current;
                    } else {
                        queue.remove();
                        ui = queue.peek();
                    }
                }
            }

            // Otherwise, or after attempting to process the iterators on the queue, look at the
            // stack of Chain objects and get the next item from the top-most Chain. If this is itself
            // a Chain, then add it to the stack and repeat. If this Chain is exhausted, then pop it off the
            // stack and repeat.

            while (!stack.isEmpty()) {
                ChainPosition cp = stack.peek();
                if (cp != null) {
                    GroundedValue gv = cp.chain.children.get(cp.offset);
                    if (++cp.offset >= cp.chain.children.size()) {
                        stack.pop();
                    }
                    if (gv instanceof Chain) {
                        stack.push(new ChainPosition((Chain)gv, 0));
                    } else {
                        try {
                            queue.offer(gv.iterate());
                        } catch (XPathException e) {
                            throw new AssertionError(e);
                        }
                        return next();
                    }
                }
            }

            // If we get here, there is no more data available

            current = null;
            position = -1;
            return null;
        }

        /**
         * Get the current value in the sequence (the one returned by the
         * most recent call on next()). This will be null before the first
         * call of next().
         *
         * @return the current item, the one most recently returned by a call on
         *         next(); or null, if next() has not been called, or if the end
         *         of the sequence has been reached.
         */

        public Item current() {
            return current;
        }

        /**
         * Get the current position. This will be zero before the first call
         * on next(), otherwise it will be the number of times that next() has
         * been called.
         *
         * @return the current position, the position of the item returned by the
         *         most recent call of next()
         */

        public int position() {
            return position;
        }

        public void close() {

        }

        /**
         * Get another SequenceIterator that iterates over the same items as the original,
         * but which is repositioned at the start of the sequence.
         *
         * @return a SequenceIterator that iterates over the same items,
         *         positioned before the first item
         */

        /*@NotNull*/
        public ChainIterator getAnother() {
            return new ChainIterator();
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link SequenceIterator#GROUNDED}, {@link SequenceIterator#LAST_POSITION_FINDER},
         *         and {@link SequenceIterator#LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        public int getProperties() {
            return SequenceIterator.GROUNDED;
        }

        /**
         * Return a GroundedValue containing all the items in the sequence returned by this
         * SequenceIterator. This should be an "in-memory" value, not a Closure.
         *
         * @return the corresponding Value
         */
        public GroundedValue materialize() throws XPathException {
            return Chain.this;
        }
    }


}

