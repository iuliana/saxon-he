////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

/**
 * When tree construction is deferred, innocuous methods such as NodeInfo#getLocalName() may
 * trigger a dynamic error. Rather than make all such methods on NodeInfo throw a checked XPathException,
 * we instead throw an UncheckedXPathException, which is a simple wrapper for an XPathException.
 * Appropriate places in the client code must check for this condition and translate it back into an
 * XPathException.
 */

public class UncheckedXPathException extends RuntimeException {

    private XPathException cause;

    public UncheckedXPathException(XPathException cause) {
        this.cause = cause;
    }

    public XPathException getXPathException() {
        return cause;
    }
}
