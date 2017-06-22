////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.JPConverter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ExternalObjectType;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.ObjectValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.UntypedAtomicValue;

import java.util.Collection;
import java.util.HashMap;


/**
 * A GlobalParameterSet is a set of parameters supplied when invoking a stylesheet or query.
 * It is a collection of name-value pairs, the names being represented by StructuredQName objects.
 * The values are objects, as supplied by the caller: conversion of the object
 * to a required type takes place when the parameter is actually used.
 */

public class GlobalParameterSet
{
	private HashMap<StructuredQName, Object> params = new HashMap<StructuredQName, Object>(10);

    /**
     * Create an empty parameter set
     */

    public GlobalParameterSet() {}

    /**
     * Add a parameter to the ParameterSet
     *
     * @param qName The fingerprint of the parameter name.
     * @param value The value of the parameter, or null if the parameter is to be removed
     */

    public void put (StructuredQName qName, /*@Nullable*/ Object value) {
        if (value == null) {
            params.remove(qName);
        } else {
            params.put(qName, value);
        }
    }

    /**
     * Get a parameter
     *
     * @param qName The parameter name.
     * @return The value of the parameter, or null if not defined
     */

    public Object get (StructuredQName qName) {
        return params.get(qName);
    }

    public boolean containsKey (StructuredQName qName) {
        return params.containsKey(qName);
    }

    /**
     * Get the value of a parameter, converted to a Sequence object
     * @param qName the name of the parameter
     * @param requiredType the required type of the parameter
     * @param convert set to true if function conversion rules are to be applied, or to false if the value
     * is simply to be checked against the required type
     * @param context dynamic evaluation context
     * @return the value after conversion and type checking; or null if there is no value for this parameter
     * @throws XPathException if the value is of the wrong type
     */

    public Sequence convertParameterValue(
            StructuredQName qName, SequenceType requiredType, boolean convert, XPathContext context)
            throws XPathException {
        Sequence val;
        Object obj = get(qName);
        if (obj == null) {
            return null;
        }
        if (obj instanceof Sequence) {
            val = (Sequence)obj;
        } else if (obj instanceof String && convert) {
            val = new UntypedAtomicValue((String)obj);
        } else if (requiredType.getPrimaryType() instanceof ExternalObjectType) {
            val = new ObjectValue(obj);
        } else {
            JPConverter converter = JPConverter.allocate(obj.getClass(), context.getConfiguration());
            val = converter.convert(obj, context);
        }
        if (val==null) {
            val = EmptySequence.getInstance();
        }

        if (requiredType != null) {
            if (convert) {
                RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, qName.getDisplayName(), -1);
                Configuration config = context.getConfiguration();
                val = config.getTypeHierarchy().applyFunctionConversionRules(val, requiredType, role, null);
            } else {
                XPathException err = TypeChecker.testConformance(val, requiredType, context);
                if (err != null) {
                    throw err;
                }
            }
        }
        return val;
    }


    /**
     * Clear all values
     */

    public void clear() {
        params.clear();
    }

    /**
     * Get all the keys that have been allocated
     * @return the names of the parameter keys (QNames)
     */

    public Collection<StructuredQName> getKeys() {
        return params.keySet();
    }

    /**
     * Get the number of entries in the result of getKeys() that are significant
     * @return the number of entries
     */

    public int getNumberOfKeys() {
        return params.size();
    }

}