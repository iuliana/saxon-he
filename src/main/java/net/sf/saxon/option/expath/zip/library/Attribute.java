////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       Attribute.java                                              */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

/**
 * An abstract representation of an attribute.
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public interface Attribute
{
    /**
     * Return the local part of the name of the attribute.
     */
    public String getLocalName();

    /**
     * Return the namespace URI part of the name of the attribute.
     *
     * Return the empty string if the name is in no namespace (never return
     * {@code null}).
     */
    public String getNamespaceUri();

    /**
     * Return the string value of the attribute.
     */
    public String getValue();
}

//This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
//If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
//This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.