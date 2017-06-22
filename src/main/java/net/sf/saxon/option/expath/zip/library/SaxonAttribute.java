////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       SaxonAttribute.java                                         */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

import net.sf.saxon.om.NodeInfo;

/**
 * The implementation for Saxon of an abstract attribute.
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public class SaxonAttribute
        implements Attribute
{
    public SaxonAttribute(NodeInfo node)
    {
        // TODO: Should we perform some checks (is it non-null?, is it an
        // element node?, etc.)  Or is it guaranteed by construction?
        myNode = node;
    }

    public String getLocalName()
    {
        return myNode.getLocalPart();
    }


    public String getNamespaceUri()
    {
        return myNode.getURI();
    }

    public String getValue()
    {
        return myNode.getStringValue();
    }

    private NodeInfo myNode;
}

