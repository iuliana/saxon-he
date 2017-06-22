////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       ZipException.java                                           */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

import net.sf.saxon.trans.XPathException;

/**
 * General exception thrown by components in this module.
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public class ZipException
        extends XPathException
{
    public ZipException(String msg)
    {
        super(msg);
    }

    public ZipException(String msg, Throwable cause)
    {
        //TODO set error code
        super(msg, cause);
    }
}

