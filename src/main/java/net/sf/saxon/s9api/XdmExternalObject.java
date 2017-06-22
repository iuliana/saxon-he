////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.type.ValidationException;
import net.sf.saxon.value.ObjectValue;

/**
 * The class XdmExternalObject represents an XDM item that wraps an external (Java or .NET) object.
 * As such, it is outside the scope of the XDM specification (but permitted as an extension).
 *
 * <p>In releases prior to 9.5, external objects in Saxon were represented as atomic values. From
 * 9.5 they are represented as a fourth kind of item, alongside nodes, atomic values, and functions.</p>
 *
 */
public class XdmExternalObject extends XdmItem {

    protected XdmExternalObject(Object value) {
        super(new ObjectValue(value));
    }


    /**
     * Construct an atomic value given its lexical representation and the name of the required
     * built-in atomic type.
     * <p>This method cannot be used to construct values that are namespace-sensitive (QNames and Notations)</p>
     * @param lexicalForm the value in the lexical space of the target data type. More strictly, the input
     * value before the actions of the whitespace facet for the target data type are applied.
     * @param type the required atomic type. This must either be one of the built-in
     * atomic types defined in XML Schema, or a user-defined type whose definition appears
     * in a schema that is known to the Processor. It must not be an abstract type.
     * @throws net.sf.saxon.s9api.SaxonApiException if the type is unknown, or is not atomic, or is namespace-sensitive;
     * or if the value supplied in <tt>lexicalForm</tt> is not in the lexical space of the specified atomic
     * type.
     */

    public XdmExternalObject(String lexicalForm, ItemType type) throws SaxonApiException {
        net.sf.saxon.type.ItemType it = type.getUnderlyingItemType();
        if (!it.isPlainType()) {
            throw new SaxonApiException("Requested type is not atomic");
        }
        if (((AtomicType)it).isAbstract()) {
            throw new SaxonApiException("Requested type is an abstract type");
        }
        if (((AtomicType)it).isNamespaceSensitive()) {
            throw new SaxonApiException("Requested type is namespace-sensitive");
        }
        try {
            StringConverter converter = type.getConversionRules().getStringConverter((AtomicType)it);
            setValue(converter.convertString(lexicalForm).asAtomic());
        } catch (ValidationException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Get the wrapped Java object
     * @return the wrapped object
     */

    public Object getExternalObject() {
        return ((ObjectValue)getUnderlyingValue()).getObject();
    }

    /**
     * Get the result of converting the external value to a string.
     */

    public String toString() {
        return getExternalObject().toString();
    }


}

