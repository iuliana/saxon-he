////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;
import net.sf.saxon.trans.XPathException;


/**
  * MessageEmitter is the default Receiver for xsl:message output.
  * It is the same as XMLEmitter except for an extra newline at the end of the message
  */
  
public class MessageEmitter extends XMLEmitter
{
    public void endDocument() throws XPathException {
        try {
            writer.write('\n');
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
        super.close();
    }

    public void close() throws XPathException {
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (java.io.IOException err) {
            throw new XPathException(err);
        }
        super.close();
    }

}

