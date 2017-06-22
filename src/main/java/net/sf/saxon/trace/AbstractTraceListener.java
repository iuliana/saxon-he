////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Controller;
import net.sf.saxon.Version;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.CodeInjector;
import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.value.Whitespace;

import java.io.PrintStream;
import java.util.Iterator;

/**
 * This is the standard trace listener used when the -T option is specified on the command line.
 * There are two variants, represented by subclasses: one for XSLT, and one for XQuery. The two variants
 * differ in that they present the trace output in terms of constructs used in the relevant host language.
 */
public abstract class AbstractTraceListener implements TraceListener {
    private int indent = 0;
    private PrintStream out = System.err;
    private static StringBuilder spaceBuffer = new StringBuilder("                ");

    /**
     * Get the associated CodeInjector to be used at compile time to generate the tracing calls
     */
    public CodeInjector getCodeInjector() {
        return new TraceCodeInjector();
    }

    /**
     * Called at start
     */
	public void open(Controller controller) {
		out.println("<trace saxon-version=\"" + Version.getProductVersion() + "\" " + getOpeningAttributes() + '>');
		indent++;
	}

	protected abstract String getOpeningAttributes();

    /**
     * Called at end
     */
    public void close() {
        indent--;
        out.println("</trace>");
    }

    /**
     * Called when an instruction in the stylesheet gets processed
     */
	public void enter(InstructionInfo info, XPathContext context) {
		int infotype = info.getConstructType();
		StructuredQName qName = info.getObjectName();
		String tag = tag(infotype);
		if (tag == null) {
			// this TraceListener ignores some events to reduce the volume of output
			return;
		}
		String file = StandardErrorListener.abbreviatePath(info.getSystemId());
		StringBuilder msg = new StringBuilder(AbstractTraceListener.spaces(indent)).append('<').append(tag);
		String name = (String) info.getProperty("name");
		if (name != null) {
			msg.append(" name=\"").append(escape(name)).append('"');
		} else if (qName != null) {
			msg.append(" name=\"").append(escape(qName.getDisplayName())).append('"');
		}
		Iterator props = info.getProperties();
		while (props.hasNext()) {
			String prop = (String) props.next();
			Object val = info.getProperty(prop);
			if (prop.startsWith("{")) {
				// It's a QName in Clark notation: we'll strip off the namespace
				int rcurly = prop.indexOf('}');
				if (rcurly > 0) {
					prop = prop.substring(rcurly + 1);
				}
			}
			if (val != null && !prop.equals("name") && !prop.equals("expression")) {
				msg.append(' ').append(prop).append("=\"").append((val.toString())).append('"');
			}
		}

        msg.append(" line=\"").append(info.getLineNumber()).append('"');

        int col = info.getColumnNumber();
        if (col >= 0) {
            msg.append(" column=\"").append(info.getColumnNumber()).append('"');
        }

        msg.append(" module=\"").append(escape(file)).append("\">");
        out.println(msg);
        indent++;
    }

    /**
     * Escape a string for XML output (in an attribute delimited by double quotes).
     * This method also collapses whitespace (since the value may be an XPath expression that
     * was originally written over several lines).
     */
    public String escape(String in) {
		if (in == null) {
			return "";
		}
        CharSequence collapsed = Whitespace.collapseWhitespace(in);
        FastStringBuffer sb = new FastStringBuffer(collapsed.length() + 10);
        for (int i=0; i<collapsed.length(); i++) {
            char c = collapsed.charAt(i);
            if (c=='<') {
                sb.append("&lt;");
            } else if (c=='>') {
                sb.append("&gt;");
            } else if (c=='&') {
                sb.append("&amp;");
            } else if (c=='\"') {
                sb.append("&#34;");
            } else if (c=='\n') {
                sb.append("&#xA;");
            } else if (c=='\r') {
                sb.append("&#xD;");
            } else if (c=='\t') {
                sb.append("&#x9;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Called after an instruction of the stylesheet got processed
     */
    public void leave(InstructionInfo info) {
        int infotype = info.getConstructType();
        String tag = tag(infotype);
		if (tag == null) {
			// this TraceListener ignores some events to reduce the volume of output
			return;
		}
        indent--;
        out.println(AbstractTraceListener.spaces(indent) + "</" + tag + '>');
    }

    protected abstract String tag(int construct);

    /**
    * Called when an item becomes the context item
    */
   public void startCurrentItem(Item item) {
       if (item instanceof NodeInfo) {
           NodeInfo curr = (NodeInfo) item;
			out.println(AbstractTraceListener.spaces(indent) + "<source node=\"" + Navigator.getPath(curr) +
				"\" line=\"" + curr.getLineNumber() + "\" file=\"" +
				StandardErrorListener.abbreviatePath(curr.getSystemId()) + "\">");
       }
       indent++;
   }

    /**
     * Called after a node of the source tree got processed
     */
    public void endCurrentItem(Item item) {
        indent--;
        if (item instanceof NodeInfo) {
            NodeInfo curr = (NodeInfo) item;
            out.println(AbstractTraceListener.spaces(indent) + "</source><!-- " + Navigator.getPath(curr) + " -->");
        }
    }

    /**
     * Get n spaces
     */
    private static String spaces(int n) {
        while (spaceBuffer.length() < n) {
            spaceBuffer.append(AbstractTraceListener.spaceBuffer);
        }
        return spaceBuffer.substring(0, n);
    }

    /**
     * Set the output destination (default is System.err)
     * @param stream the output destination for tracing output
     */
    public void setOutputDestination(PrintStream stream) {
        out = stream;
    }

    /**
     * Get the output destination
     */
    public PrintStream getOutputDestination() {
        return out;
    }
}
