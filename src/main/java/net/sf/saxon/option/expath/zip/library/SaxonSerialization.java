////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       SaxonSerialization.java                                     */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-06-16                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

import net.sf.saxon.lib.SaxonOutputKeys;


/**
 * Support for Saxon-specific serialization parameters.
 * 
 * TODO: Actually, for now, it also has to support standard serialization params
 * which have no constants in JAXP (which supports only the version 1.0 of the
 * serial spec).  For now it even only supports those actually...
 *
 * @author Florent Georges
 * @date   2011-06-16
 */
public class SaxonSerialization
        extends Serialization
{
    @Override
    public void setOutputParam(String name, String value)
       throws ZipException
    {
        if ( "byte-order-mark".equals(name) ) {
            setProperty(SaxonOutputKeys.BYTE_ORDER_MARK, value);
        }
        else if ( "escape-uri-uttributes".equals(name) ) {
            setProperty(SaxonOutputKeys.ESCAPE_URI_ATTRIBUTES, value);
        }
        else if ( "normalization-form".equals(name) ) {
            setProperty(SaxonOutputKeys.NORMALIZATION_FORM, value);
        }
        else if ( "suppress-indentation".equals(name) ) {
            setProperty(SaxonOutputKeys.SUPPRESS_INDENTATION, value);
        }
        else if ( "undeclare-prefixes".equals(name) ) {
            setProperty(SaxonOutputKeys.UNDECLARE_PREFIXES, value);
        }
        else {
            super.setOutputParam(name, value);
        }
    }
}

