////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.pattern;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;

import java.io.Serializable;

/**
 * This interface enables a client to find all nodes in a document that match a particular pattern.
 * In fact, it allows any subset of nodes in a document to be located. It is used specifically by the
 * internal implementation of keys. In XSLT, the criterion for including nodes in a key is that they
 * match an XSLT pattern. Internally, however, keys are used for a wider range of purposes, and the
 * nodes indexed by the key are defined by a PatternFinder
 */

public interface PatternFinder extends Serializable {

    /**
     * Select nodes in a document using this PatternFinder.
     *
     * @param doc the document node at the root of a tree
     * @param context the dynamic evaluation context
     * @return an iterator over the selected nodes in the document.
     * @throws XPathException if a dynamic error is encountered
     */

    /*@Nullable*/ public SequenceIterator<? extends NodeInfo> selectNodes(DocumentInfo doc, XPathContext context) throws XPathException;

}

