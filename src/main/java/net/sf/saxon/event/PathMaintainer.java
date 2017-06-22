////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.event;

import net.sf.saxon.om.AbsolutePath;
import net.sf.saxon.om.NodeName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.Type;

import java.util.HashMap;
import java.util.Stack;


/**
  * This class sits in a receiver (push) pipeline and maintains the current path.
  * @author Michael H. Kay
  */


public class PathMaintainer extends ProxyReceiver {

    private Stack<AbsolutePath.PathElement> path = new Stack<AbsolutePath.PathElement>();
    private Stack<HashMap<NodeName, Integer>> siblingCounters =
            new Stack<HashMap<NodeName, Integer>>();

    public PathMaintainer(/*@NotNull*/ Receiver next) {
        super(next);
        siblingCounters.push(new HashMap<NodeName, Integer>());
    }


    public void startElement (NodeName elemName, SchemaType type, int locationId, int properties) throws XPathException
    {
    	// System.err.println("startElement " + nameCode);
        nextReceiver.startElement(elemName, type, locationId, properties);
        HashMap<NodeName, Integer> counters = siblingCounters.peek();
        int index = 1;
        Integer preceding = counters.get(elemName);
        if (preceding != null) {
            index = preceding + 1;
            counters.put(elemName, index);
        } else {
            counters.put(elemName, 1);
        }
        path.push(new AbsolutePath.PathElement(Type.ELEMENT, elemName, index));
        siblingCounters.push(new HashMap<NodeName, Integer>());
    }

    /**
    * Handle an end-of-element event
    */

    public void endElement () throws XPathException {
        nextReceiver.endElement();
        siblingCounters.pop();
        path.pop();
    }

    /**
     * Get the path to the current location in the stream
     * @param useURIs set to true if namespace URIs are to appear in the path;
     * false if prefixes are to be used instead. The prefix will be the one
     * that is used in the source document, and is potentially ambiguous.
     * @return the path to the current location, as a string.
     */

    public String getPath(boolean useURIs) {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.MEDIUM);
        for (AbsolutePath.PathElement pe : path) {
            fsb.append('/');
            if (useURIs) {
                String uri = pe.getName().getURI();
                if (uri.length()!=0) {
                    fsb.append('"');
                    fsb.append(uri);
                    fsb.append('"');
                }
            } else {
                String prefix = pe.getName().getPrefix();
                if (prefix.length()!=0) {
                    fsb.append(prefix);
                    fsb.append(':');
                }
            }
            fsb.append(pe.getName().getLocalPart());
            fsb.append('[');
            fsb.append(pe.getIndex()+"");
            fsb.append(']');
        }
        return fsb.toString();
    }

    public AbsolutePath getAbsolutePath() {
        return new AbsolutePath(path);
    }


}

