////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Count;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.GroundedIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.wrapper.VirtualNode;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

/**
 * Utility class for manipulating sequences. Some of these methods should be regarded
 * as temporary scaffolding while the model is in transition.
 */
public class SequenceTool {

    /**
     * Constant returned by compareTo() method to indicate an indeterminate ordering between two values
     */

    public static final int INDETERMINATE_ORDERING = Integer.MIN_VALUE;

    /**
     * Produce a GroundedValue containing the same values as a supplied sequence.
     * @param sequence the supplied sequence
     * @return a GroundedValue containing the same items
     * @throws XPathException
     */

    public static GroundedValue toGroundedValue(Sequence sequence) throws XPathException {
        if (sequence instanceof GroundedValue) {
            return (GroundedValue)sequence;
        } else {
            return new SequenceExtent(sequence.iterate()).reduce();
        }
    }

    /**
     * Produce a GroundedValue containing the same values as a supplied sequence.
     * @param iterator the supplied sequence. The iterator may or may not be consumed as a result of
     * passing it to this method.
     * @return a GroundedValue containing the same items
     * @throws XPathException if a failure occurs reading the input iterator
     */

    public static GroundedValue toGroundedValue(SequenceIterator iterator) throws XPathException {
        if ((iterator.getProperties() & SequenceIterator.GROUNDED) != 0) {
            return ((GroundedIterator)iterator).materialize();
        } else {
            return new SequenceExtent(iterator).reduce();
        }
    }

    /**
     * Produce a Sequence containing the same values as a supplied sequence; the input is
     * read progressively as required, and saved in a buffer as it is read in case it is needed
     * again. But if the iterator is already backed by a grounded value, we return an iterator
     * over that value.
     * @param iterator the supplied sequence. The iterator may or may not be consumed as a result of
     * passing it to this method.
     * @return a GroundedValue containing the same items
     * @throws XPathException if a failure occurs reading the input iterator
     */

    public static Sequence toMemoSequence(SequenceIterator iterator) throws XPathException {
        if ((iterator.getProperties() & SequenceIterator.GROUNDED) != 0) {
            return ((GroundedIterator)iterator).materialize();
        } else {
            return new MemoSequence(iterator);
        }
    }


    /**
     * Construct a sequence capable of returning the same items as an iterator,
     * without incurring the cost of evaluating the iterator and storing all
     * the items.
     * @param iterator the supplied sequence. The iterator may or may not be consumed as a result of
     * passing it to this method.
     * @return a Sequence containing the same items as the supplied iterator
     * @throws XPathException if a failure occurs reading the input iterator
     */

    public static Sequence toLazySequence(SequenceIterator iterator) throws XPathException {
        if ((iterator.getProperties() & SequenceIterator.GROUNDED) != 0) {
            return ((GroundedIterator)iterator).materialize();
        } else {
            return new LazySequence(iterator);
        }
    }

    /**
     * Get the length of a sequence (the number of items it contains)
     * @param sequence the sequence
     * @return the length in items
     * @throws XPathException
     */

    public static int getLength(Sequence sequence) throws XPathException {
        if (sequence instanceof Item) {
            return 1;
        }
        if (sequence instanceof GroundedValue) {
            return ((GroundedValue)sequence).getLength();
        }
        return Count.count(sequence.iterate());
    }

    /**
     * Get the item at a given offset in a sequence. Uses zero-base indexing
     * @param sequence the input sequence
     * @param index the 0-based subscript
     * @return the n'th item if it exists, or null otherwise
     */

    public static Item itemAt(Sequence sequence, int index) throws XPathException {
        if (sequence instanceof Item && index==0) {
            return (Item)sequence;
        }
        return toGroundedValue(sequence).itemAt(index);
    }

    /**
     * Static method to make an Item from a Value
     * @param sequence the value to be converted
     * @return null if the value is an empty sequence; or the only item in the value
     * if it is a singleton sequence
     * @throws net.sf.saxon.trans.XPathException if the supplied Sequence contains multiple items
     */

    /*@Nullable*/
    public static Item asItem(/*@NotNull*/ Sequence sequence) throws XPathException {
        if (sequence instanceof Item) {
            return (Item)sequence;
        }
        SequenceIterator iter = sequence.iterate();
        Item first = iter.next();
        if (first == null) {
            return null;
        }
        if (iter.next() != null) {
            throw new XPathException("Sequence contains more than one item");
        }
        return first;
    }

    /**
     * Convert an XPath value to a Java object.
     * An atomic value is returned as an instance
     * of the best available Java class. If the item is a node, the node is "unwrapped",
     * to return the underlying node in the original model (which might be, for example,
     * a DOM or JDOM node).
     * @param item the item to be converted
     * @return the value after conversion
     * @throws net.sf.saxon.trans.XPathException if an error occurs: for example, if the XPath value is
     * an integer and is too big to fit in a Java long
    */

    public static Object convertToJava(/*@NotNull*/ Item item) throws XPathException {
        if (item instanceof NodeInfo) {
            Object node = item;
            while (node instanceof VirtualNode) {
                // strip off any layers of wrapping
                node = ((VirtualNode)node).getRealNode();
            }
            return node;
        } else if (item instanceof FunctionItem) {
            return item;
        } else if (item instanceof ObjectValue) {
            return ((ObjectValue)item).getObject();
        } else {
            AtomicValue value = (AtomicValue)item;
            switch (value.getItemType().getPrimitiveType()) {
                case StandardNames.XS_STRING:
                case StandardNames.XS_UNTYPED_ATOMIC:
                case StandardNames.XS_ANY_URI:
                case StandardNames.XS_DURATION:
                    return value.getStringValue();
                case StandardNames.XS_BOOLEAN:
                    return (((BooleanValue)value).getBooleanValue() ? Boolean.TRUE : Boolean.FALSE );
                case StandardNames.XS_DECIMAL:
                    return ((DecimalValue)value).getDecimalValue();
                case StandardNames.XS_INTEGER:
                    return ((NumericValue) value).longValue();
                case StandardNames.XS_DOUBLE:
                    return ((DoubleValue) value).getDoubleValue();
                case StandardNames.XS_FLOAT:
                    return ((FloatValue) value).getFloatValue();
                case StandardNames.XS_DATE_TIME:
                    return ((DateTimeValue)value).getCalendar().getTime();
                case StandardNames.XS_DATE:
                    return ((DateValue)value).getCalendar().getTime();
                case StandardNames.XS_TIME:
                    return value.getStringValue();
                case StandardNames.XS_BASE64_BINARY:
                    return ((Base64BinaryValue)value).getBinaryValue();
                case StandardNames.XS_HEX_BINARY:
                    return ((HexBinaryValue)value).getBinaryValue();
                default:
                    return item;
            }
        }
    }

    public static String getStringValue(Sequence sequence) throws XPathException {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
        SequenceIterator iter = sequence.iterate();
        boolean first = true;
        while (true) {
            Item item = iter.next();
            if (item == null) {
                break;
            }
            if (first) {
                first = false;
            } else {
                fsb.append(' ');
            }
            fsb.append(item.getStringValueCS());
        }
        return fsb.toString();
    }

    public static ItemType getItemType(Sequence sequence, TypeHierarchy th) {
        if (sequence instanceof Item) {
            return Type.getItemType(((Item)sequence), th);
        } else if (sequence instanceof EmptySequence) {
            return ErrorType.getInstance();
        } else if (sequence instanceof GroundedValue) {
            try {
                ItemType type = null;
                SequenceIterator iter = sequence.iterate();
                while (true) {
                    Item item = iter.next();
                    if (item == null) {
                        break;
                    }
                    if (type == null) {
                        type = Type.getItemType(item, th);
                    } else {
                        type = Type.getCommonSuperType(type, Type.getItemType(item, th), th);
                    }
                    if (type == AnyItemType.getInstance()) {
                        break;
                    }
                }
                return (type == null ? ErrorType.getInstance() : type);
            } catch (XPathException err) {
                return AnyItemType.getInstance();
            }
        } else {
            return AnyItemType.getInstance();
        }

//            it = Type.getItemType(value[start], th);
//            for (int i=start+1; i<end; i++) {
//                if (it == AnyItemType.getInstance()) {
//                    // make a quick exit
//                    return it;
//                }
//                it = Type.getCommonSuperType(it, Type.getItemType(value[i], th), th);
//            }
    }

    public static int getCardinality(Sequence sequence) {
        if (sequence instanceof Item) {
            return StaticProperty.EXACTLY_ONE;
        }
        if (sequence instanceof EmptySequence || sequence instanceof EmptyAtomicSequence) {
            return StaticProperty.ALLOWS_ZERO;
        }
        try {
            SequenceIterator iter = sequence.iterate();
            Item item = iter.next();
            if (item == null) {
                return StaticProperty.ALLOWS_ZERO;
            }
            item = iter.next();
            return (item == null ? StaticProperty.EXACTLY_ONE : StaticProperty.ALLOWS_ONE_OR_MORE);
        } catch (XPathException err) {
            return StaticProperty.ALLOWS_ONE_OR_MORE;
        }
    }

    /**
      * Process the value as an instruction, without returning any tail calls
      * @param context The dynamic context, giving access to the current node,
      * the current variables, etc.
     * @throws net.sf.saxon.trans.XPathException if an error occurs (for example if the value is
     * a closure that needs to be evaluated)
      */

    public static void process(Sequence value, XPathContext context, int locationId) throws XPathException {
        SequenceIterator iter = value.iterate();
        SequenceReceiver out = context.getReceiver();
        while (true) {
            Item it = iter.next();
            if (it==null) break;
            out.append(it, locationId, NodeInfo.ALL_NAMESPACES);
        }
    }
}

