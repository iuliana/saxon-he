////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.SourceLocator;

/**
 * A mapping function for use in conjunction with an {@link ItemMappingIterator} that checks that
 * all the items in a sequence are instances of a given item type
 *
 * @param <T> Defines a subtype of item to be checked
 */
public class ItemTypeCheckingFunction<T extends Item> implements ItemMappingFunction<T, T> {

    private ItemType requiredItemType;
    private SourceLocator locator;
    private RoleLocator role;
    private Configuration config = null;

    /**
     * Create the type-checking function
     *
     * @param requiredItemType the item type that all items in the sequence must conform to
     * @param role             information for error messages
     * @param locator the location of the expression for error messages
     * @param config the Saxon configuration
     */

    public ItemTypeCheckingFunction(ItemType requiredItemType, RoleLocator role, SourceLocator locator, Configuration config) {
        this.requiredItemType = requiredItemType;
        this.role = role;
        this.locator = locator;
        this.config = config;
    }

    public T mapItem(T item) throws XPathException {
    	if((item instanceof StringValue) && (((StringValue)item).getStringValue().contains(";")) && (requiredItemType instanceof BuiltInAtomicType) &&(((BuiltInAtomicType)requiredItemType).isNumericType())  )
        item=(T) new StringValue(((StringValue)item).getStringValue().split(";")[0],BuiltInAtomicType.DOUBLE);
    	testConformance(item, config);
        return item;
    }

    private void testConformance(T item, Configuration config) throws XPathException {
        if (!requiredItemType.matchesItem(item, true, config)) {
            String message;

            final NamePool pool = config.getNamePool();
            final TypeHierarchy th = config.getTypeHierarchy();
            message = role.composeErrorMessage(requiredItemType, Type.getItemType(item, th));

            String errorCode = role.getErrorCode();
            if ("XPDY0050".equals(errorCode)) {
                // error in "treat as" assertion
                XPathException te = new XPathException(message, errorCode);
                te.setLocator(locator);
                te.setIsTypeError(false);
                throw te;
            } else {
                XPathException te = new XPathException(message, errorCode);
                te.setLocator(locator);
                te.setIsTypeError(true);
                throw te;
            }
        }
    }

}

