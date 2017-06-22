////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.AnalyzeString;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.DecimalValue;

import java.util.ArrayList;
import java.util.List;

/**
* An xsl:analyze-string elements in the stylesheet. New at XSLT 2.0<BR>
*/

public class XSLAnalyzeString extends StyleElement {

    /*@Nullable*/ private Expression select;
    private Expression regex;
    private Expression flags;
    private StyleElement matching;
    private StyleElement nonMatching;
    private RegularExpression pattern;

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction
    */

    public boolean isInstruction() {
        return true;
    }

    /**
    * Determine whether this type of element is allowed to contain an xsl:fallback
    * instruction
    */

    public boolean mayContainFallback() {
        return true;
    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     * @return the item type returned
     */

    protected ItemType getReturnedItemType() {
        return getCommonChildItemType();
    }

     public void prepareAttributes() throws XPathException {
		String selectAtt = null;
		String regexAtt = null;
		String flagsAtt = null;

		AttributeCollection atts = getAttributeList();

		for (int a=0; a<atts.getLength(); a++) {
            String f = atts.getQName(a);
			if (f.equals(StandardNames.REGEX)) {
        		regexAtt = atts.getValue(a);
			} else if (f.equals(StandardNames.SELECT)) {
        		selectAtt = atts.getValue(a);
			} else if (f.equals(StandardNames.FLAGS)) {
        		flagsAtt = atts.getValue(a); // not trimmed, see bugzilla 4315
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (selectAtt==null) {
            reportAbsence("select");
            selectAtt = ".";    // for error recovery
        }
        select = makeExpression(selectAtt);

        if (regexAtt==null) {
            reportAbsence("regex");
            regexAtt = "xxx";      // for error recovery
        }
        regex = makeAttributeValueTemplate(regexAtt);

        if (flagsAtt==null) {
            flagsAtt = "";
        }
        flags = makeAttributeValueTemplate(flagsAtt);

        if (regex instanceof StringLiteral && flags instanceof StringLiteral) {
            try {
                final String regex = ((StringLiteral)this.regex).getStringValue();
                final String flagstr = ((StringLiteral)flags).getStringValue();

                List<String> warnings = new ArrayList<String>();
                pattern = Configuration.getPlatform().compileRegularExpression(
                        regex, flagstr, (getEffectiveVersion().equals(DecimalValue.THREE)? "XP30" : "XP20"), warnings);
                for (String w : warnings) {
                    issueWarning(w, this);
                }
                if (pattern.matches("")) {
                    invalidRegex("The regular expression must not be one that matches a zero-length string", "XTDE1150");
                }
            } catch (XPathException err) {
                if ("FORX0001".equals(err.getErrorCodeLocalPart())) {
                    invalidRegex("Error in regular expression flags: " + err, "XTDE1145");
                } else {
                    invalidRegex("Error in regular expression: " + err, "XTDE1140");
                }
            }
        }

    }

    private void invalidRegex(String message, String errorCode) throws XPathException {
        compileError(message, errorCode);
        // prevent it being reported more than once
        pattern = Configuration.getPlatform().compileRegularExpression("x", "", "XP20", null);
    }

    public void validate(Declaration decl) throws XPathException {
        //checkWithinTemplate();

        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        boolean foundFallback = false;
        while(true) {
            NodeInfo curr = kids.next();
            if (curr == null) {
                break;
            }
            if (curr instanceof XSLFallback) {
                foundFallback = true;
            } else if (curr instanceof XSLMatchingSubstring) {
                boolean b = curr.getLocalPart().equals("matching-substring");
                if (b) {
                    if (matching!=null) {
                        compileError("xsl:matching-substring element must only appear once", "XTSE0010");
                    }
                    if (nonMatching != null || foundFallback) {
                        compileError("xsl:matching-substring element must precede xsl:non-matching-substring and xsl:fallback", "XTSE0010");
                    }
                    matching = (StyleElement)curr;
                } else {
                    if (nonMatching!=null) {
                        compileError("xsl:non-matching-substring element must only appear once", "XTSE0010");
                    }
                    if (foundFallback) {
                        compileError("xsl:non-matching-substring element must precede xsl:fallback", "XTSE0010");
                    }
                    nonMatching = (StyleElement)curr;
                }
            } else {
                compileError("Only xsl:matching-substring and xsl:non-matching-substring are allowed here", "XTSE0010");
            }
        }

        if (matching==null && nonMatching==null) {
            compileError("At least one xsl:matching-substring or xsl:non-matching-substring element must be present",
                    "XTSE1130");
        }

        select = typeCheck("select", select);
        regex = typeCheck("regex", regex);
        flags = typeCheck("flags", flags);

        // following code removed because it's done again within AnalyzeString.typeCheck()
//        try {
//            ExpressionVisitor visitor = makeExpressionVisitor();
//
//            RoleLocator role =
//                new RoleLocator(RoleLocator.INSTRUCTION, "xsl:analyze-string/select", 0);
//            select = TypeChecker.staticTypeCheck(select, SequenceType.SINGLE_STRING, false, role,
//                    visitor);
//
//            role =
//                new RoleLocator(RoleLocator.INSTRUCTION, "xsl:analyze-string/regex", 0);
//            regex = TypeChecker.staticTypeCheck(regex, SequenceType.SINGLE_STRING, false, role, visitor);
//
//            role =
//                new RoleLocator(RoleLocator.INSTRUCTION, "xsl:analyze-string/flags", 0);
//            flags = TypeChecker.staticTypeCheck(flags, SequenceType.SINGLE_STRING, false, role, visitor);
//        } catch (XPathException err) {
//            compileError(err);
//        }

    }

    public Expression compile(Executable exec, Declaration decl) throws XPathException {
        Expression matchingBlock = null;
        if (matching != null) {
            matchingBlock = matching.compileSequenceConstructor(exec, decl, matching.iterateAxis(AxisInfo.CHILD), false);
        }

        Expression nonMatchingBlock = null;
        if (nonMatching != null) {
            nonMatchingBlock = nonMatching.compileSequenceConstructor(exec, decl, nonMatching.iterateAxis(AxisInfo.CHILD), false);
        }

        try {
            ExpressionVisitor visitor = makeExpressionVisitor();
            return new AnalyzeString(select,
                                     regex,
                                     flags,
                                     (matchingBlock==null ? null : matchingBlock.simplify(visitor)),
                                     (nonMatchingBlock==null ? null : nonMatchingBlock.simplify(visitor)),
                                     pattern );
        } catch (XPathException e) {
            compileError(e);
            return null;
        }
    }



}

