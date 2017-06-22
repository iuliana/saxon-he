////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An value in the XDM data model. A value is a sequence of zero or more items,
 * each item being either an atomic value or a node.
 *
 * <p>An XdmValue is immutable.</p>
 *
 * <p>A sequence consisting of a single item may be represented as an instance of {@link XdmItem},
 * which is a subtype of XdmValue. However, there is no guarantee that a sequence of length one
 * will always be an instance of XdmItem.</p>
 *
 * <p>Similarly, a zero-length sequence may be represented as an instance of {@link XdmEmptySequence},
 * but there is no guarantee that every sequence of length zero will always be an instance of
 * XdmEmptySequence.</p>
 *
 * @since 9.0
 */

public class XdmValue implements Iterable<XdmItem> {

    private Sequence value;    // this must be a materialized value

    protected XdmValue() {
        // must be followed by setValue()
    }

    /**
     * Create an XdmValue as a sequence of XdmItem objects
     * @param items a sequence of XdmItem objects. Note that if this is supplied as a list or similar
     * collection, subsequent changes to the list/collection will have no effect on the XdmValue.
     * @since 9.0.0.4
     */

    public XdmValue(Iterable<XdmItem> items) {
        List<Item> values = new ArrayList<Item>();
        for (XdmItem item : items) {
            values.add((Item)item.getUnderlyingValue());
        }
        value = new SequenceExtent<Item>(values);
    }

    protected XdmValue(Sequence value) {
        setValue(value);
    }

    protected void setValue(Sequence value) {
        this.value = value;
    }

    /**
     * Create an XdmValue that wraps an existing Saxon Sequence
     * @param value the supplied Sequence (which may be a singleton Item),
     * @return an XdmValue corresponding to the supplied Sequence. If the
     * supplied value is null, an empty sequence is returned. If the supplied
     * value is an atomic value, the result will be an instance of XdmAtomicValue.
     * If the supplied value is a node, the result will be an instance of XdmNode.
     * If the supplied value is a function item (including map items), the result will be an instance of
     * XdmFunctionItem.
     * @throws SaxonApiUncheckedException if the supplied Sequence is not yet fully evaluated, and evaluation
     * of the underlying expression fails with a dynamic error.
     * @since 9.5 (previously a protected method)
     */

    public static XdmValue wrap(Sequence value)  {
        if (value == null) {
            return XdmEmptySequence.getInstance();
        }
        if (value instanceof NodeInfo) {
            return new XdmNode((NodeInfo)value);
        }
        if (value instanceof AtomicValue) {
            return new XdmAtomicValue((AtomicValue)value);
        }
        if (value instanceof ObjectValue) {
            return new XdmExternalObject(((ObjectValue)value).getObject());
        }
        if (value instanceof SingletonItem) {
            Item item = ((SingletonItem)value).asItem();
            if (item instanceof NodeInfo) {
                return new XdmNode((NodeInfo)item);
            } else if (item instanceof AtomicValue) {
                return new XdmAtomicValue((AtomicValue)item);
            } else if (item instanceof FunctionItem) {
                return new XdmFunctionItem((FunctionItem)item);
            } else if (item instanceof ObjectValue) {
                return new XdmExternalObject(((ObjectValue)item).getObject());
            } else {
                throw new AssertionError("Unknown kind of item");
            }
        }
        try {
            value = SequenceTool.toGroundedValue(value);
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
        if (value == null) {
            return XdmEmptySequence.getInstance();
        } else if (value instanceof NodeInfo) {
            return new XdmNode((NodeInfo)value);
        } else if (value instanceof AtomicValue) {
            return new XdmAtomicValue((AtomicValue)value);
        } else if (value instanceof EmptySequence) {
            return XdmEmptySequence.getInstance();
        } else if (value instanceof FunctionItem) {
            return new XdmFunctionItem((FunctionItem)value);
        } else if (value instanceof SingletonItem) {
            return wrap(((SingletonItem)value).asItem());
        } else {
            return new XdmValue(value);
        }
    }

    /**
     * Create a new XdmValue by concatenating the contents of this XdmValue and another
     * XdmValue. The two input XdmValue objects are unaffected by this operation.
     * <p>Note: creating a sequence of N values by successive calls on this method
     * takes time proportional to N-squared.</p>
     * @param otherValue the value to be appended
     * @return a new XdmValue containing the concatenation of the two input XdmValue objects
     * @since 9.2
     */

    public XdmValue append(XdmValue otherValue) {
        List<Item> values = new ArrayList<Item>();
        for (XdmItem item : this) {
            values.add((Item)item.getUnderlyingValue());
        }
        for (XdmItem item : otherValue) {
            values.add((Item)item.getUnderlyingValue());
        }
        return new XdmValue(new SequenceExtent<Item>(values));
    }

    /**
     * Get the number of items in the sequence
     * @return the number of items in the value
     * @throws SaxonApiUncheckedException if the value is lazily evaluated and the delayed
     * evaluation fails with a dynamic error.
     */

    public int size() {
        try {
            return SequenceTool.getLength(value);
        } catch (XPathException err) {
            throw new SaxonApiUncheckedException(err);
        }
    }

    /**
     * Get the n'th item in the value, counting from zero.
     * @param n the item that is required, counting the first item in the sequence as item zero
     * @return the n'th item in the sequence making up the value, counting from zero
     * @throws IndexOutOfBoundsException if n is less than zero or greater than or equal to the number
     * of items in the value
     * @throws SaxonApiUncheckedException if the value is lazily evaluated and the delayed
     * evaluation fails with a dynamic error.
     */

    public XdmItem itemAt(int n) throws IndexOutOfBoundsException, SaxonApiUncheckedException {
        if (n < 0 || n >= size()) {
            throw new IndexOutOfBoundsException(Integer.toString(n));
        }
        try {
            Item item = SequenceTool.itemAt(value, n);
            return (XdmItem)XdmItem.wrap(item);
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Get an iterator over the items in this value.
     * @return an Iterator over the items in this value.
     * @throws SaxonApiUncheckedException if the value is lazily evaluated and the delayed
     * evaluation fails with a dynamic error.
     */
    public XdmSequenceIterator iterator() throws SaxonApiUncheckedException {
        try {
            Sequence v = getUnderlyingValue();
            return new XdmSequenceIterator(v.iterate());
        } catch (XPathException e) {
            throw new SaxonApiUncheckedException(e);
        }
    }

    /**
     * Get the underlying implementation object representing the value. This method allows
     * access to lower-level Saxon functionality, including classes and methods that offer
     * no guarantee of stability across releases.
     * @return the underlying implementation object representing the value
     */

    public Sequence getUnderlyingValue() {
        return value;
    }



}

