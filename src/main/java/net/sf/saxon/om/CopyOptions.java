////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import net.sf.saxon.event.ReceiverOptions;

/**
 * Non-instantiable class to define options for the {@link NodeInfo#copy} method
 */
public abstract class CopyOptions {

    public static final int LOCAL_NAMESPACES = 1;

    public static final int ALL_NAMESPACES = 2;

    public static final int SOME_NAMESPACES = LOCAL_NAMESPACES | ALL_NAMESPACES;

    public static final int TYPE_ANNOTATIONS = 4;

    public static final int FOR_UPDATE = 8;

    public static final int IS_XQUERY = 16; // either XSLT or XQUERY: only affects error codes

    public static boolean includes(int options, int option) {
        return (options & option) == option;
    }

    public static int getStartDocumentProperties(int copyOptions) {
        int properties = 0;
        if (CopyOptions.includes(copyOptions, CopyOptions.FOR_UPDATE)) {
            properties |= ReceiverOptions.MUTABLE_TREE;
        }
        return properties;
    }
}

