////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

/**
* Exception indicating that an attribute or namespace node has been written when
* there is no open element to write it to
*/

public class NoOpenStartTagException extends XPathException {

    /**
     * Static factory method to create the exception
     * @param nodeKind the kind of node being created (attribute or namespace)
     * @param name the name of the node being created
     * @param hostLanguage XSLT or XQuery (error codes are different in the two cases)
     * @param parentIsDocument true if the nodes are being added to a document node (rather than an element)
     * @param isSerializing true if the document is being created in the process of serialization
     * @param locationProvider Object to provide location information for diagnostics
     * @param startElementLocationId integer that can be passed to the location provider to get the location
     * of the offending instruction that created the element node
     * @return the constructed exception object
     */

    public static NoOpenStartTagException makeNoOpenStartTagException(
            int nodeKind, String name, int hostLanguage, boolean parentIsDocument, boolean isSerializing,
            /*@Nullable*/ final LocationProvider locationProvider, int startElementLocationId) {
        String message;
        String errorCode;
        if (parentIsDocument) {
            if (isSerializing) {
                String kind = (nodeKind == Type.ATTRIBUTE ? "attribute" : "namespace");
                message = "Cannot serialize a free-standing " + kind + " node (" + name + ')';
                errorCode = "SENR0001";
            } else {
                String kind = (nodeKind == Type.ATTRIBUTE ? "an attribute" : "a namespace");
                message = "Cannot create " + kind + " node (" + name + ") whose parent is a document node";
                errorCode = (hostLanguage == Configuration.XSLT ? "XTDE0420" : "XPTY0004");
            }
        } else {
            String kind = (nodeKind == Type.ATTRIBUTE ? "An attribute" : "A namespace");
            message = kind + " node (" + name + ") cannot be created after a child of the containing element";
            errorCode = (hostLanguage == Configuration.XSLT ? "XTDE0410" : "XQTY0024");
        }
        if (locationProvider != null && startElementLocationId > 0) {
            message += ". Most recent element start tag was output at line " +
                    locationProvider.getLineNumber(startElementLocationId) + " of module " +
                    StandardErrorListener.abbreviatePath(locationProvider.getSystemId(startElementLocationId));
        }
        NoOpenStartTagException err = new NoOpenStartTagException(message);
        err.setErrorCode(errorCode);
        return err;
    }

    public NoOpenStartTagException(String message) {
        super(message);
    }

//    public NoOpenStartTagException(int nodeKind, String name, int hostLanguage, boolean topLevel, boolean isSerializing) {
//        // The contorted conditional here is because super() has to be at the start of the method
//        super((topLevel ?
//                (isSerializing ?
//                   "Cannot serialize ")
//                ("Cannot create " +
//                    (nodeKind==Type.ATTRIBUTE ? "an attribute" : "a namespace") +
//                    " node (" + name + ") whose parent is a document node")
//                :
//                (nodeKind==net.sf.saxon.type.Type.ATTRIBUTE ? "An attribute" : "A namespace") +
//                    " node (" + name + ") cannot be created after the children of the containing element"
//                ));
//        if (hostLanguage == Configuration.XSLT) {
//            setErrorCode(topLevel ? "XTDE0420" : "XTDE0410");
//        } else {
//            setErrorCode(topLevel ? "XPTY0004" : "XQTY0024");
//        }
//    }

}

