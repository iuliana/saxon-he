////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.AtomicComparer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.FunctionItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * XDM 3.0 introduces a third kind of item, beyond nodes and atomic values: the function item. Function
 * items implement this marker interface. The actual implementation class is in Saxon-PE and Saxon-EE only.
 */

public interface FunctionItem extends Item, Callable, GroundedValue {

    /**
     * Get the item type of the function item
     * @param th the type hierarchy cache
     * @return the function item's type
     */

    public FunctionItemType getFunctionItemType(TypeHierarchy th);

    /**
     * Get the name of the function, or null if it is anonymous
     * @return the function name, or null for an anonymous inline function
     */

    public StructuredQName getFunctionName();

    /**
     * Get the arity of the function
     * @return the number of arguments in the function signature
     */

    public int getArity();

    /**
     * Invoke the function
     * @param context the XPath dynamic evaluation context
     * @param args the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws XPathException if a dynamic error occurs within the function
     */

    public Sequence call(XPathContext context, Sequence[] args) throws XPathException;

    /**
     * Test whether this FunctionItem is deep-equal to another function item,
     * under the rules of the deep-equal function
     *
     * @param other the other function item
     * @param context the dynamic evaluation context
     * @param comparer the object to perform the comparison
     * @param flags options for how the comparison is performed
     * @return true if the two function items are deep-equal
     * @throws XPathException if the comparison cannot be performed
     */

    public boolean deepEquals(FunctionItem other, XPathContext context, AtomicComparer comparer, int flags) throws XPathException;

}

