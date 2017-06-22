////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.om.Item;
import net.sf.saxon.value.AtomicValue;

/**
 * The class XdmItem represents an item in a sequence, as defined by the XDM data model.
 * An item is either an atomic value or a node.
 *
 * <p>An item is a member of a sequence, but it can also be considered as a sequence
 *  (of length one) in its own right. <tt>XdmItem</tt> is a subtype of <tt>XdmValue</tt> because every
 *  Item in the XDM data model is also a value.</p>
 *
 * <p>It cannot be assumed that every sequence of length one will be represented by
 * an <tt>XdmItem</tt>. It is quite possible for an <tt>XdmValue</tt> that is not an <tt>XdmItem</tt> to hold
 * a singleton sequence.</p>
 *
 * <p>Saxon provides two concrete subclasses of <code>XdmItem</code>, namely
 * {@link XdmNode} and {@link XdmAtomicValue}. Users must not attempt to create
 * additional subclasses.</p>
 */

public abstract class XdmItem extends XdmValue {

    // internal protected constructor

    protected XdmItem() { }

    /**
     * Construct an XdmItem as a wrapper around an existing Saxon Item object
     * @param item the Item object to be wrapped. This can be retrieved using the
     * {@link #getUnderlyingValue} method.
     * @throws NullPointerException if item is null
     * @since 9.5 (previously a protected constructor)
     */

    public XdmItem(Item item) {
        super(item);
    }

    // internal factory mathod to wrap an Item

    /*@Nullable*/ protected static XdmItem wrapItem(Item item) {
        return item == null ? null : (XdmItem)XdmValue.wrap(item);
    }


    /**
     * Factory method to construct an atomic value given its lexical representation and the
     * required item type
     * @param value the lexical representation of the required value
     * @param type the item type of the required value
     * @return the constructed item
     * @throws SaxonApiException if the supplied string is not in the lexical space of the target type, or
     * if the target type is not atomic
     * @deprecated since 9.1. This factory method duplicates the constructor
     * {@link XdmAtomicValue#XdmAtomicValue(String, ItemType)} which should be used in preference
     */

    public static XdmItem newAtomicValue(String value, ItemType type) throws SaxonApiException {
        return new XdmAtomicValue(value, type);
    }

    /**
     * Get the string value of the item. For a node, this gets the string value
     * of the node. For an atomic value, it has the same effect as casting the value
     * to a string. In all cases the result is the same as applying the XPath string()
     * function.
     *
     * <p>For atomic values, the result is the same as the result of calling
     * <code>toString</code>. This is not the case for nodes, where <code>toString</code>
     * returns an XML serialization of the node.</p>
     *
     * @return the result of converting the item to a string.
     */

    public String getStringValue() {
        //noinspection RedundantCast
        return ((Item)getUnderlyingValue()).getStringValue();
    }

    /**
     * Determine whether the item is an atomic value or a node
     * @return true if the item is an atomic value, false if it is a node
     */

    public boolean isAtomicValue() {
        return ((Item)getUnderlyingValue()) instanceof AtomicValue;
    }

    /**
     * Get the number of items in the sequence
     * @return the number of items in the value - always one
     */

    @Override
    public int size() {
        return 1;
    }

}

