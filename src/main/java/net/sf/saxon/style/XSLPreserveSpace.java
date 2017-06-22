////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.event.Stripper;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

import java.util.StringTokenizer;

/**
* An xsl:preserve-space or xsl:strip-space elements in stylesheet. <br>
*/

public class XSLPreserveSpace extends StyleElement {

    private String elements;

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.ELEMENTS)) {
        		elements = atts.getValue(a);
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }
        if (elements==null) {
            reportAbsence("elements");
            elements="*";   // for error recovery
        }
    }

    public void validate(Declaration decl) throws XPathException {
        checkEmpty();
        checkTopLevel("XTSE0010");
    }

    public void compileDeclaration(Executable exec, /*@NotNull*/ Declaration decl) throws XPathException
    {
        Stripper.StripRuleTarget preserve =
                (getFingerprint() == StandardNames.XSL_PRESERVE_SPACE ? Stripper.PRESERVE : Stripper.STRIP);
        SpaceStrippingRule stripperRules = getPreparedStylesheet().getStripperRules();
        if (!(stripperRules instanceof SelectedElementsSpaceStrippingRule)) {
            stripperRules = new SelectedElementsSpaceStrippingRule(isXslt30Processor());
            getPreparedStylesheet().setStripperRules(stripperRules);
        }

        SelectedElementsSpaceStrippingRule rules = (SelectedElementsSpaceStrippingRule)stripperRules;

        // elements is a space-separated list of element names

        StringTokenizer st = new StringTokenizer(elements, " \t\n\r", false);
        try {
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                NodeTest nt;
                if (s.equals("*")) {
                    nt = NodeKindTest.ELEMENT;
                    rules.addRule(nt, preserve, decl.getModule(), decl.getSourceElement().getLineNumber());

                } else if (s.endsWith(":*")) {
                    if (s.length()==2) {
                        compileError("No prefix before ':*'");
                    }
                    String prefix = s.substring(0, s.length()-2);
                    String uri = getURIForPrefix(prefix, false);
                    if (uri == null) {
                        undeclaredNamespaceError(prefix, "XTSE0280");
                    }
                    nt = new NamespaceTest(getNamePool(), Type.ELEMENT, uri);
                    rules.addRule(nt, preserve, decl.getModule(), decl.getSourceElement().getLineNumber());

                } else if (s.startsWith("*:")) {
                    if (s.length()==2) {
                        compileError("No local name after '*:'");
                    }
                    String localname = s.substring(2);
                    nt = new LocalNameTest(getNamePool(), Type.ELEMENT, localname);
                    rules.addRule(nt, preserve, decl.getModule(), decl.getSourceElement().getLineNumber());

                } else {
                    String prefix;
                    String localName;
                    String uri;
                    try {
                        String[] parts = getConfiguration().getNameChecker().getQNameParts(s);
                        prefix = parts[0];
                        if (parts[0].equals("")) {
                            uri = getDefaultXPathNamespace();
                        } else {
                            uri = getURIForPrefix(prefix, false);
                            if (uri == null) {
                                undeclaredNamespaceError(prefix, "XTSE0280");
                            }
                        }
                        localName = parts[1];
                    } catch (QNameException err) {
                        compileError("Element name " + s + " is not a valid QName", "XTSE0280");
                        return;
                    }
                    NamePool target = getNamePool();
                    int nameCode = target.allocate("", uri, localName);
                    nt = new NameTest(Type.ELEMENT, nameCode, getNamePool());
                    rules.addRule(nt, preserve, decl.getModule(), decl.getSourceElement().getLineNumber());
                }

            }
        } catch (XPathException e) {
            e.maybeSetLocation(this);
            compileError(e);
        }
    }


}

