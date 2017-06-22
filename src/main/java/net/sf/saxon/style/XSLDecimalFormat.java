////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.NamespaceException;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.DecimalSymbols;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

/**
* Handler for xsl:decimal-format elements in stylesheet. <br>
*/

public class XSLDecimalFormat extends StyleElement {

    boolean prepared = false;

    String name;
    String decimalSeparator;
    String groupingSeparator;
    String infinity;
    String minusSign;
    String NaN;
    String percent;
    String perMille;
    String zeroDigit;
    String digit;
    String patternSeparator;

    DecimalSymbols symbols;

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

        if (prepared) {
            return;
        }
        prepared = true;

		AttributeCollection atts = getAttributeList();

        for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.NAME)) {
        		name = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.DECIMAL_SEPARATOR)) {
        		decimalSeparator = atts.getValue(a);
        	} else if (f.equals(StandardNames.GROUPING_SEPARATOR)) {
        		groupingSeparator = atts.getValue(a);
        	} else if (f.equals(StandardNames.INFINITY)) {
        		infinity = atts.getValue(a);
        	} else if (f.equals(StandardNames.MINUS_SIGN)) {
        		minusSign = atts.getValue(a);
        	} else if (f.equals(StandardNames.NAN)) {
        		NaN = atts.getValue(a);
        	} else if (f.equals(StandardNames.PERCENT)) {
        		percent = atts.getValue(a);
        	} else if (f.equals(StandardNames.PER_MILLE)) {
        		perMille = atts.getValue(a);
        	} else if (f.equals(StandardNames.ZERO_DIGIT)) {
        		zeroDigit = atts.getValue(a);
        	} else if (f.equals(StandardNames.DIGIT)) {
        		digit = atts.getValue(a);
        	} else if (f.equals(StandardNames.PATTERN_SEPARATOR)) {
        		patternSeparator = atts.getValue(a);
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }
    }

    public void validate(Declaration decl) throws XPathException {
        checkTopLevel("XTSE0010");
        checkEmpty();
        int precedence = decl.getPrecedence();

        if (symbols == null) {
            return; // error already reported
        }

        if (decimalSeparator!=null) {
            symbols.setProperty(DecimalSymbols.DECIMAL_SEPARATOR, decimalSeparator, precedence);
        }
        if (groupingSeparator!=null) {
            symbols.setProperty(DecimalSymbols.GROUPING_SEPARATOR, groupingSeparator, precedence);
        }
        if (infinity!=null) {
            symbols.setProperty(DecimalSymbols.INFINITY, infinity, precedence);
        }
        if (minusSign!=null) {
            symbols.setProperty(DecimalSymbols.MINUS_SIGN, minusSign, precedence);
        }
        if (NaN!=null) {
            symbols.setProperty(DecimalSymbols.NAN, NaN, precedence);
        }
        if (percent!=null) {
            symbols.setProperty(DecimalSymbols.PERCENT, percent, precedence);
        }
        if (perMille!=null) {
            symbols.setProperty(DecimalSymbols.PER_MILLE, perMille, precedence);
        }
        if (zeroDigit!=null) {
            symbols.setProperty(DecimalSymbols.ZERO_DIGIT, zeroDigit, precedence);
        }
        if (digit!=null) {
            symbols.setProperty(DecimalSymbols.DIGIT, digit, precedence);
        }
        if (patternSeparator!=null) {
            symbols.setProperty(DecimalSymbols.PATTERN_SEPARATOR, patternSeparator, precedence);
        }
    }

    /**
     * Method supplied by declaration elements to add themselves to a stylesheet-level index
     * @param decl the Declaration being indexed. (This corresponds to the StyleElement object
     * except in cases where one module is imported several times with different precedence.)
     * @param top  the outermost XSLStylesheet element
     */

    public void index(Declaration decl, PrincipalStylesheetModule top) throws XPathException
    {
        prepareAttributes();
        DecimalFormatManager dfm = getPreparedStylesheet().getDecimalFormatManager();
        if (name==null) {
            symbols = dfm.getDefaultDecimalFormat();
        } else {
            try {
                StructuredQName formatName = makeQName(name);
                symbols = dfm.obtainNamedDecimalFormat(formatName);
                symbols.setHostLanguage(Configuration.XSLT);
            } catch (XPathException err) {
                compileError("Invalid decimal format name. " + err.getMessage(), "XTSE0020");
            } catch (NamespaceException err) {
                compileError("Invalid decimal format name. " + err.getMessage(), "XTSE0280");
            }
        }
    }

    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {
        // no action
    }

}
