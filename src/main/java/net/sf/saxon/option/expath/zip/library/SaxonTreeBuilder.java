////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       SaxonTreeBuilder.java                                       */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

import net.sf.saxon.event.Builder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NoNamespaceName;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.Untyped;


/**
 * Implementation for Saxon of the processor-independent TreeBuilder.
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public class SaxonTreeBuilder
        implements TreeBuilder
{
    public SaxonTreeBuilder(XPathContext ctxt)
            throws ZipException
    {
        myBuilder = ctxt.getController().makeBuilder();
        myBuilder.open();
    }

    public void startElement(String local_name)
            throws ZipException
    {
        final String prefix = ZipConstants.ZIP_NS_PREFIX;
        final String uri    = ZipConstants.ZIP_NS_URI;
        NodeName name = new FingerprintedQName(prefix, uri, local_name);
        try {
            myBuilder.startElement(name, Untyped.getInstance(), 0, 0);
        }
        catch ( XPathException ex ) {
            throw new ZipException("Error starting element on the Saxon tree builder", ex);
        }
    }

    public void endElement()
            throws ZipException
    {
        try {
            myBuilder.endElement();
        }
        catch ( XPathException ex ) {
            throw new ZipException("Error ending element on the Saxon tree builder", ex);
        }
    }

    public void startContent()
            throws ZipException
    {
        try {
            myBuilder.startContent();
        }
        catch ( XPathException ex ) {
            throw new ZipException("Error starting content on the Saxon tree builder", ex);
        }
    }

    public void attribute(String local_name, String value)
            throws ZipException
    {
        NodeName name = new NoNamespaceName(local_name);
        try {
            myBuilder.attribute(name, BuiltInAtomicType.UNTYPED_ATOMIC, value, 0, 0);
        }
        catch ( XPathException ex ) {
            throw new ZipException("Error starting content on the Saxon tree builder", ex);
        }
    }

    public NodeInfo getRoot()
            throws XPathException
    {
        myBuilder.close();
        return myBuilder.getCurrentRoot();
    }

    private Builder myBuilder;
}

