////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.Controller;
import net.sf.saxon.event.SequenceOutputter;
import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.event.TeeOutputter;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.iter.SingletonIterator;

import java.util.List;

/**
 * A MemoClosure represents a value that has not yet been evaluated: the value is represented
 * by an expression, together with saved values of all the context variables that the
 * expression depends on.
 * <p/>
 * <p>The MemoClosure is designed for use when the value is only read several times. The
 * value is saved on the first evaluation and remembered for later use.</p>
 * <p/>
 * <p>The MemoClosure maintains a reservoir containing those items in the value that have
 * already been read. When a new iterator is requested to read the value, this iterator
 * first examines and returns any items already placed in the reservoir by previous
 * users of the MemoClosure. When the reservoir is exhausted, it then uses an underlying
 * Input Iterator to read further values of the underlying expression. If the value is
 * not read to completion (for example, if the first user did exists($expr), then the
 * Input Iterator is left positioned where this user abandoned it. The next user will read
 * any values left in the reservoir by the first user, and then pick up iterating the
 * base expression where the first user left off. Eventually, all the values of the
 * expression will find their way into the reservoir, and future users simply iterate
 * over the reservoir contents. Alternatively, of course, the values may be left unread.</p>
 * <p/>
 * <p>Delayed evaluation is used only for expressions with a static type that allows
 * more than one item, so the evaluateItem() method will not normally be used, but it is
 * supported for completeness.</p>
 * <p/>
 * <p>The expression may depend on local variables and on the context item; these values
 * are held in the saved XPathContext object that is kept as part of the Closure, and they
 * will always be read from that object. The expression may also depend on global variables;
 * these are unchanging, so they can be read from the Bindery in the normal way. Expressions
 * that depend on other contextual information, for example the values of position(), last(),
 * current(), current-group(), should not be evaluated using this mechanism: they should
 * always be evaluated eagerly. This means that the Closure does not need to keep a copy
 * of these context variables.</p>
 * <p/>
 * <p>In Saxon-EE, a for-each loop can be multithreaded. If a variable declared outside
 * the loop is evaluated as a MemoClosure, then a reference to the variable within the
 * loop can result in concurrent attempts to evaluate the variable incrementally. This
 * is prevented by synchronizing the evaluation methods.</p>
 */

public class MemoClosure<T extends Item> extends Closure<T> {

    /*@Nullable*/ private T[] reservoir = null;
    private int used;
    protected int state;

    // State in which no items have yet been read
    private static final int UNREAD = 0;

    // State in which zero or more items are in the reservoir and it is not known
    // whether more items exist
    private static final int MAYBE_MORE = 1;

    // State in which all the items are in the reservoir
    private static final int ALL_READ = 3;

    // State in which we are getting the base iterator. If the closure is called in this state,
    // it indicates a recursive entry, which is only possible on an error path
    private static final int BUSY = 4;

    // State in which we know that the value is an empty sequence
    protected static final int EMPTY = 5;

    /**
     * Constructor should not be called directly, instances should be made using the make() method.
     */

    //private static int closureCount = 0;
    public MemoClosure() {
        //System.err.println("************** Creating MemoClosure " + closureCount);
        //closureCount++; if ((closureCount % 1000) == 0) System.err.println("MemoClosures: " + closureCount);
    }

    /**
     * Evaluate the expression in a given context to return an iterator over a sequence
     *
     */

    /*@NotNull*/
    public synchronized SequenceIterator<T> iterate() throws XPathException {

        switch (state) {
        case UNREAD:
            state = BUSY;
            inputIterator = (SequenceIterator<T>)expression.iterate(savedXPathContext);
            if (inputIterator instanceof EmptyIterator) {
                state = EMPTY;
                return inputIterator;
            }
            reservoir = (T[])new Item[50];
            used = 0;
            state = MAYBE_MORE;
            return new ProgressiveIterator();

        case MAYBE_MORE:
            return new ProgressiveIterator();

        case ALL_READ:
            switch (used) {
            case 0:
                state = EMPTY;
                return EmptyIterator.emptyIterator();
            case 1:
                assert reservoir != null;
                return SingletonIterator.makeIterator(reservoir[0]);
            default:
                return new ArrayIterator<T>(reservoir, 0, used);
            }

        case BUSY:
            // recursive entry: can happen if there is a circularity involving variable and function definitions
            // Can also happen if variable evaluation is attempted in a debugger, hence the cautious message
            XPathException de = new XPathException("Attempt to access a variable while it is being evaluated");
            de.setErrorCode("XTDE0640");
            //de.setXPathContext(context);
            throw de;

        case EMPTY:
            return EmptyIterator.emptyIterator();

        default:
            throw new IllegalStateException("Unknown iterator state");

        }
    }

    /**
     * Process the expression by writing the value to the current Receiver
     *
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public synchronized void process(/*@NotNull*/ XPathContext context) throws XPathException {
        // To evaluate the closure in push mode, we need to use the original context of the
        // expression for everything except the current output destination, which is taken from the
        // context supplied at evaluation time
        if (state == EMPTY) {
            return;     // we know there is nothing to do
        } else if (state == BUSY) {
            // recursive entry: can happen if there is a circularity involving variable and function definitions
            XPathException de = new XPathException("Attempt to access a variable while it is being evaluated");
            de.setErrorCode("XTDE0640");
            de.setXPathContext(context);
            throw de;
        }
        if (reservoir != null) {
            SequenceIterator iter = iterate();
            SequenceReceiver out = context.getReceiver();
            while (true) {
                Item it = iter.next();
                if (it==null) break;
                out.append(it, 0, NodeInfo.ALL_NAMESPACES);
            }
        } else {
            state = BUSY;
            Controller controller = context.getController();
            XPathContextMajor c2 = savedXPathContext.newContext();
            // Fork the output: one copy goes to a SequenceOutputter which remembers the contents for
            // use next time the variable is referenced; another copy goes to the current output destination.
            SequenceOutputter seq = controller.allocateSequenceOutputter(20);
            seq.open();
            TeeOutputter tee = new TeeOutputter(context.getReceiver(), seq);
            tee.setPipelineConfiguration(controller.makePipelineConfiguration());
            c2.setReceiver(tee);
            c2.setTemporaryOutputState(true);

            expression.process(c2);

            seq.close();
            List list = seq.getList();
            if (list.isEmpty()) {
                state = EMPTY;
            } else {
                reservoir = (T[])new Item[list.size()];
                reservoir = (T[])list.toArray(reservoir);
                used = list.size();
                state = ALL_READ;
            }
            // give unwanted stuff to the garbage collector
            savedXPathContext = null;
            seq.reset();
        }

    }

    /**
     * Get the n'th item in the sequence (starting from 0). This is defined for all
     * SequenceValues, but its real benefits come for a SequenceValue stored extensionally
     */

    /**
     * Append an item to the reservoir
     * @param item the item to be added
     */

    private void append(T item) {
        assert reservoir != null;
        if (used >= reservoir.length) {
            T[] r2 = (T[])new Item[used*2];
            System.arraycopy(reservoir, 0, r2, 0, used);
            reservoir = r2;
        }
        reservoir[used++] = item;
    }

    /**
     * Release unused space in the reservoir (provided the amount of unused space is worth reclaiming)
     */

    private void condense() {
        if (reservoir != null && reservoir.length - used > 30) {
            T[] r2 = (T[])new Item[used];
            System.arraycopy(reservoir, 0, r2, 0, used);
            reservoir = r2;
        }
        // give unwanted stuff to the garbage collector
        savedXPathContext = null;
//        inputIterator = null;
//        expression = null;
    }

    /**
     * Determine whether the contents of the MemoClosure have been fully read
     * @return true if the contents have been fully read
     */

    public boolean isFullyRead() {
        return state==EMPTY || state==ALL_READ;
    }

    /**
     * Return a value containing all the items in the sequence returned by this
     * SequenceIterator
     *
     * @return the corresponding value
     * @throws net.sf.saxon.trans.XPathException if a failure occurs reading the input
     */

    /*@Nullable*/ public GroundedValue materialize() throws XPathException {
        if (state == ALL_READ) {
            return new SequenceExtent<T>(reservoir, 0, used);
        } else if (state == EMPTY) {
            return EmptySequence.getInstance();
        }
        return new SequenceExtent<T>(iterate());
    }

    /**
     * A ProgressiveIterator starts by reading any items already held in the reservoir;
     * when the reservoir is exhausted, it reads further items from the inputIterator,
     * copying them into the reservoir as they are read.
     */

    public final class ProgressiveIterator implements SequenceIterator<T>, LastPositionFinder<T>, GroundedIterator<T> {

        int position = -1;  // zero-based position in the reservoir of the
        // item most recently read

        /**
         * Create a ProgressiveIterator
         */

        public ProgressiveIterator() {
        }

        /*@Nullable*/ public T next() throws XPathException {
            synchronized (MemoClosure.this) {
                // synchronized for the case where a multi-threaded xsl:for-each is reading the variable
                if (position == -2) {   // means we've already returned null once, keep doing so if called again.
                    return null;
                }
                if (++position < used) {
                    assert reservoir != null;
                    return reservoir[position];
                } else if (state == ALL_READ) {
                    // someone else has read the input to completion in the meantime
                    position = -2;
                    return null;
                } else {
                    assert inputIterator != null;
                    T i = inputIterator.next();
                    if (i == null) {
                        state = ALL_READ;
                        condense();
                        position = -2;
                        return null;
                    }
                    position = used;
                    append(i);
                    state = MAYBE_MORE;
                    return i;
                }
            }
        }

        /*@Nullable*/ public T current() {
            if (position < 0) {
                return null;
            }
            assert reservoir != null;
            return reservoir[position];
        }

        public int position() {
            return position + 1;    // return one-based position
        }

        public void close() {
        }

        /*@NotNull*/ public ProgressiveIterator getAnother() {
            return new ProgressiveIterator();
        }

        /**
         * Get the last position (that is, the number of items in the sequence)
         */

        public int getLength() throws XPathException {
            if (state == ALL_READ) {
                return used;
            } else if (state == EMPTY) {
                return 0;
            } else {
                // save the current position
                int savePos = position;
                // fill the reservoir
                while (true) {
                    Item item = next();
                    if (item == null) {
                        break;
                    }
                }
                // reset the current position
                position = savePos;
                // return the total number of items
                return used;
            }
        }

        /**
         * Return a value containing all the items in the sequence returned by this
         * SequenceIterator
         *
         * @return the corresponding value
         */

        /*@Nullable*/ public GroundedValue materialize() throws XPathException {
            if (state == ALL_READ) {
                assert reservoir != null;
                return new SequenceExtent<Item>(reservoir, 0, used);
            } else if (state == EMPTY) {
                return EmptySequence.getInstance();
            } else {
                return new SequenceExtent<T>(iterate());
                //throw new IllegalStateException("Progressive iterator is not grounded until all items are read");
            }
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED} and {@link #LAST_POSITION_FINDER}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         */

        public int getProperties() {
            // bug 1740 shows that it is better to report the iterator as grounded even though this
            // may trigger eager evaluation of the underlying sequence.
            //if (state == EMPTY || state == ALL_READ) {
                return GROUNDED | LAST_POSITION_FINDER;
            //} else {
            //    return 0;
            //}
        }

        /**
         * Get the n'th item in the sequence, zero-based
         */

        public T itemAt(int n) throws XPathException {
            synchronized (MemoClosure.this) {
                if (n < 0) {
                    return null;
                }
                if (reservoir != null && n < used) {
                    return reservoir[n];
                }
                if (state == ALL_READ || state == EMPTY) {
                    return null;
                }
                if (state == UNREAD) {
                    T item = inputIterator.next();
                    state = MAYBE_MORE;
                    if (item == null) {
                        state = EMPTY;
                        return null;
                    } else {
                        state = MAYBE_MORE;
                        reservoir = (T[])new Item[50];
                        used = 0;
                        append(item);
                        if (n == 0) {
                            return item;
                        }
                    }
                }
                // We have read some items from the input sequence but not enough. Read as many more as are needed.
                int diff = n - used + 1;
                while (diff-- > 0) {
                    T i = inputIterator.next();
                    if (i == null) {
                        state = ALL_READ;
                        condense();
                        return null;
                    }
                    append(i);
                    state = MAYBE_MORE;
                }
                //noinspection ConstantConditions
                return reservoir[n];
            }
        }

    }

}

