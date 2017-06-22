////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/****************************************************************************/
/*  File:       TreeBuilder.java                                            */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2011-02-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2011 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package net.sf.saxon.option.expath.zip.library;

/**
 * An abstract tree builder, to be implemented for each specific processor.
 *
 * TODO: Review the javadoc in case this interface does not allow pushing text
 * in element content (as this is not needed in this project).
 *
 * @author Florent Georges
 * @date   2011-02-21
 */
public interface TreeBuilder
{
    /**
     * Start a new element, in the zip namespace (see {@link ZipConstants}).
     */
    public void startElement(String local_name)
            throws ZipException;

    /**
     * End the currently opened element.
     */
    public void endElement()
            throws ZipException;

    /**
     * Start element content.
     *
     * After a call to {@link #startElement(String)}, you can push attributes.
     * Before pushing some text or child elements, you have to call this method
     * (in order to close the opening tag and start pushing content).  So the
     * last call on this object must have been {@link #startElement(String)},
     * optionally followed by some attributes.
     */
    public void startContent()
            throws ZipException;

    /**
     * Create a new attribute on the current element, in no namespace.
     *
     * A call to this method must follow a call to {@link #startElement(String)}
     * or another call to this method.  After all attributes have been added to
     * the element, the start tag must be closed by calling the method {@link
     * #startContent()}, in order to push text, other element or to close the
     * current element.
     */
    public void attribute(String name, String value)
            throws ZipException;
}

//This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
//If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
//This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
