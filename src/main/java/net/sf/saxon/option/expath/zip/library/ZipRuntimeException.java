////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       ZipRuntimeException.java                                    */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

/**
 * Runtime exception thrown by components in this module.
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public class ZipRuntimeException
        extends RuntimeException
{
    public ZipRuntimeException(String msg)
    {
        super(msg);
    }

    public ZipRuntimeException(String msg, Throwable cause)
    {
        super(msg, cause);
    }
}

//This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
//If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
//This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.