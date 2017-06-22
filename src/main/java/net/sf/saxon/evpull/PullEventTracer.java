////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.evpull;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.PrintStream;

/**
 * Diagnostic class to display the sequence of events reported by an EventIterator
 */
public class PullEventTracer implements EventIterator {

    private EventIterator base;
    private String label = ("PET" + hashCode()).substring(0, 8) + ": ";
    private PrintStream out;

    /**
     * Create a tracer for pull events
     * @param base the event iterator whose events are to be traced
     * @param config the Saxon configuration
     */
    public PullEventTracer(EventIterator base, Configuration config) {
        this.base = base;
        out = config.getStandardErrorOutput();
    }

    /**
     * Get the next event in the sequence
     *
     * @return the next event, or null when the sequence is exhausted. Note that since an EventIterator is
     *         itself a PullEvent, this method may return a nested iterator.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic evaluation error occurs
     */
	public PullEvent next() throws XPathException {
		PullEvent pe = base.next();
		if (pe == null) {
			return null;
		}
		if (pe instanceof StartDocumentEvent) {
			out.println(label + "StartDocument");
			label = "  " + label;
		} else if (pe instanceof StartElementEvent) {
			out.println(label + "StartElement " + ((StartElementEvent) pe).getElementName().getDisplayName());
			label = "  " + label;
		} else if (pe instanceof EndDocumentEvent) {
			label = label.substring(2);
			out.println(label + "EndDocument");
		} else if (pe instanceof EndElementEvent) {
			label = label.substring(2);
			out.println(label + "EndElement");
		} else if (pe instanceof NodeInfo) {
			FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
			fsb.append(label);
			int kind = ((NodeInfo) pe).getNodeKind();
			fsb.append(NodeKindTest.toString(kind));
			if (kind == Type.ELEMENT || kind == Type.ATTRIBUTE) {
				fsb.append(' ');
				fsb.append(((NodeInfo) pe).getDisplayName());
			}
			fsb.append(" \"");
			fsb.append(((NodeInfo) pe).getStringValueCS());
			fsb.append('"');
			out.println(fsb.toString());
		} else if (pe instanceof AtomicValue) {
			out.println(label + Type.displayTypeName((AtomicValue) pe) + ' ' + pe);
		} else if (pe instanceof EventIterator) {
			out.println(label + "** NESTED ITERATOR **");
		} else {
			out.println(label + pe.getClass().getName());
		}
		return pe;
	}

    /**
     * Determine whether the EventIterator returns a flat sequence of events, or whether it can return
     * nested event iterators
     *
     * @return true if the next() method is guaranteed never to return an EventIterator
     */
    public boolean isFlatSequence() {
        return base.isFlatSequence();
    }

    /**
     * Main method for testing only
     * @param args not used
     * @throws Exception
     */
	public static void main(String[] args) throws Exception {
		Configuration config = new Configuration();
		DocumentInfo doc = config.buildDocument(new StreamSource(new File("c:/MyJava/samples/data/books.xml")));
		PipelineConfiguration pipe = config.makePipelineConfiguration();
		pipe.setHostLanguage(Configuration.XQUERY);
		EventIterator e = new Decomposer(new SingletonEventIterator(doc), pipe);
		e = EventStackIterator.flatten(e);
		e = new PullEventTracer(e, config);
		while (true) {
			PullEvent pe = e.next();
			if (pe == null) {
				break;
			}
		}
	}
}
