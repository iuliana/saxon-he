////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import javax.xml.transform.SourceLocator;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.URI;

/**
* Class UnparsedTextIterator, iterates over a file line by line
*/
public class UnparsedTextIterator extends TextLinesIterator {

    XPathContext context;
    String encoding = null;

    /**
     * Create a UnparsedTextIterator over a given file
     * @param absoluteURI the URI identifying the file
     * @param context the dynamic evaluation context
     * @param encoding the expected encoding of the file
     * @param location the location of the instruction being executed
     * @throws XPathException if a dynamic error occurs
     */

    public UnparsedTextIterator(URI absoluteURI, /*@NotNull*/ XPathContext context, String encoding, SourceLocator location) throws XPathException {
    	Configuration config = context.getConfiguration();
   	 	Reader reader = context.getController().getUnparsedTextURIResolver().resolve(absoluteURI, encoding, config);
        // TODO: does LineNumberReader use the correct definition of line endings?
        
    	this.reader = new LineNumberReader(reader);
        this.uri = absoluteURI;
        this.context = context;
        this.checker = context.getConfiguration().getNameChecker();
        this.encoding = encoding;
        this.location = location;
    }

    /*@NotNull*/
    public SequenceIterator<StringValue> getAnother() throws XPathException {
         return new UnparsedTextIterator(uri, context, encoding, location);
    }

}

