////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       Element.java                                                */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An abstract representation of an element.
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public interface Element
{
    /**
     * Return the element base URI.
     */
    public String getBaseUri()
            throws ZipException;

    /**
     * Return the local part of the name of the attribute.
     */
    public String getLocalName()
            throws ZipException;

    /**
     * Return the name of the element, as close as possible to the original name.
     */
    public String formatName()
            throws ZipException;

    /**
     * Iterate through the attributes.
     */
    public Iterable<Attribute> attributes()
            throws ZipException;

    /**
     * Iterate through the zip:entry child elements.
     *
     * Ignore withespace-only text nodes, PIs, comments, and elements in other
     * namespaces than the ZIP namespace.  This method or the returned iterable
     * object throws an error if another kind of node is encountered.
     */
    public Iterable<Element> entries()
            throws ZipException;

    /**
     * Factory method to return a serialization object.
     * 
     * There is a base implementation of Serialization, but this is the
     * opportunity for a specific implementation of the module to provide its
     * own one.  For instance to support non-standard serialization parameters.
     * The code in {@code net.sf.saxon.option.expath.library} does not instantiate such an object
     * itself, it always call this factory method.
     */
    public Serialization makeSerialization()
            throws ZipException;

    /**
     * Serialize this element to {@code out}, with the options in {@code serial}.
     */
    public void serialize(OutputStream out, Serialization serial)
            throws ZipException
                 , IOException;
}

