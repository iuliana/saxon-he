////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.type.Type;

/**
 * Enumeration class defining the seven kinds of node defined in the XDM model
 */
public enum XdmNodeKind {
    DOCUMENT (Type.DOCUMENT),
    ELEMENT (Type.ELEMENT),
    ATTRIBUTE (Type.ATTRIBUTE),
    TEXT (Type.TEXT),
    COMMENT (Type.COMMENT),
    PROCESSING_INSTRUCTION (Type.PROCESSING_INSTRUCTION),
    NAMESPACE (Type.NAMESPACE);

    private int number;
    private XdmNodeKind(int number) {
        this.number = number;
    }
    protected int getNumber() {
        return number;
    }
}

