////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.serialize;

import net.sf.saxon.event.ReceiverOptions;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import java.io.StringWriter;


/**
 * MessageWarner is a user-selectable receiver for XSLT xsl:message output. It causes xsl:message output
 * to be notified to the warning() method of the JAXP ErrorListener, or to the error() method if
 * terminate="yes" is specified. This behaviour is specified in recent versions of the JAXP interface
 * specifications, but it is not the default behaviour, for backwards compatibility reasons.
 *
 * <p>The text of the message that is sent to the ErrorListener is an XML serialization of the actual
 * message content.</p>
  */
  
public class MessageWarner extends XMLEmitter {

    boolean abort = false;

    public void startDocument(int properties) throws XPathException {
        setWriter(new StringWriter());
        abort = (properties & ReceiverOptions.TERMINATE) != 0;
        super.startDocument(properties);
    }

    public void endDocument() throws XPathException {
        ErrorListener listener = getPipelineConfiguration().getErrorListener();
        XPathException de = new XPathException(getWriter().toString());
        de.setErrorCode("XTMM9000");
        try {
            if (abort) {
                listener.error(de);
            } else {
                listener.warning(de);
            }
        } catch (TransformerException te) {
            throw XPathException.makeXPathException(te);
        }
    }

    public void close() {
        // do nothing
    }

}

