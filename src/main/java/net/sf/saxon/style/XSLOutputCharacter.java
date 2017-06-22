////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.XPathException;


/**
* An xsl:output-character element in the stylesheet. <br>
*/

public class XSLOutputCharacter extends StyleElement {

    private int codepoint = -1;
        // the character to be substituted, as a Unicode codepoint (may be > 65535)
    private String replacementString = null;

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.CHARACTER)) {
                String s = atts.getValue(a);
                switch (s.length()) {
                    case 0:
                        compileError("character attribute must not be zero-length", "XTSE0020");
                        codepoint = 256; // for error recovery
                        break;
                    case 1:
                        codepoint = s.charAt(0);
                        break;
                    case 2:
                        if (UTF16CharacterSet.isHighSurrogate(s.charAt(0)) &&
                                UTF16CharacterSet.isLowSurrogate(s.charAt(1))) {
                            codepoint = UTF16CharacterSet.combinePair(s.charAt(0), s.charAt(1));
                        } else {
                            compileError("character attribute must be a single XML character", "XTSE0020");
                            codepoint = 256; // for error recovery
                        }
                        break;
                    default:
                        compileError("character attribute must be a single XML character", "XTSE0020");
                        codepoint = 256; // for error recovery
                }
        	} else if (f.equals(StandardNames.STRING)) {
        		replacementString = atts.getValue(a);
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }
        if (codepoint==-1) {
            reportAbsence("character");
            return;
        }

        if (replacementString==null) {
            reportAbsence("string");
        }

    }

    public void validate(Declaration decl) throws XPathException {
        if (!(getParent() instanceof XSLCharacterMap)) {
            compileError("xsl:output-character may appear only as a child of xsl:character-map", "XTSE0010");
        }
    }

    public Expression compile(Executable exec, Declaration decl) throws XPathException {
        return null;
    }

    public int getCodePoint() {
        return codepoint;
    }

    public String getReplacementString() {
        return replacementString;
    }

}

