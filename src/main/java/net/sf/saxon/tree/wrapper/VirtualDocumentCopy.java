////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.wrapper;

import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NodeInfo;

import java.util.HashMap;
import java.util.Iterator;

/**
 * A virtual copy of a document node
 *
 */

public class VirtualDocumentCopy extends VirtualCopy implements DocumentInfo {

    private HashMap<String, Object> userData;

    public VirtualDocumentCopy(/*@NotNull*/ DocumentInfo base) {
        super(base);
    }

    /**
     * Ask whether the document contains any nodes whose type annotation is anything other than
     * UNTYPED
     *
     * @return true if the document contains elements whose type is other than UNTYPED
     */
    public boolean isTyped() {
        return ((DocumentInfo)original).isTyped();
    }

    /**
     * Get the element with a given ID, if any
     *
     * @param id the required ID value
     * @param getParent
     * @return the element with the given ID, or null if there is no such ID
     *         present (or if the parser has not notified attributes as being of
     *         type ID)
     */

    /*@Nullable*/ public NodeInfo selectID(String id, boolean getParent) {
        NodeInfo n = ((DocumentInfo)original).selectID(id, false);
        if (n == null) {
            return null;
        }
        VirtualCopy vc = makeVirtualCopy(n, original);
        vc.documentNumber = documentNumber;
        return vc;
    }

    /**
     * Get the list of unparsed entities defined in this document
     * @return an Iterator, whose items are of type String, containing the names of all
     *         unparsed entities defined in this document. If there are no unparsed entities or if the
     *         information is not available then an empty iterator is returned
     */

    public Iterator<String> getUnparsedEntityNames() {
        return ((DocumentInfo)original).getUnparsedEntityNames();
    }

    /**
     * Get the unparsed entity with a given name
     *
     * @param name the name of the entity
     * @return if the entity exists, return an array of two Strings, the first
     *         holding the system ID of the entity, the second holding the public
     *         ID if there is one, or null if not. If the entity does not exist,
     *         return null.
     */

    public String[] getUnparsedEntity(String name) {
        return ((DocumentInfo)original).getUnparsedEntity(name);
    }

    /**
     * Set user data on the document node. The user data can be retrieved subsequently
     * using {@link #getUserData}
     * @param key   A string giving the name of the property to be set. Clients are responsible
     *              for choosing a key that is likely to be unique. Must not be null. Keys used internally
     *              by Saxon are prefixed "saxon:".
     * @param value The value to be set for the property. May be null, which effectively
     *              removes the existing value for the property.
     */

    public void setUserData(String key, /*@Nullable*/ Object value) {
        if (userData == null) {
            userData = new HashMap(4);
        }
        if (value == null) {
            userData.remove(key);
        } else {
            userData.put(key, value);
        }
    }

    /**
     * Get user data held in the document node. This retrieves properties previously set using
     * {@link #setUserData}
     * @param key A string giving the name of the property to be retrieved.
     * @return the value of the property, or null if the property has not been defined.
     */

    /*@Nullable*/ public Object getUserData(String key) {
        if (userData == null) {
            return null;
        } else {
            return userData.get(key);
        }
    }

}
