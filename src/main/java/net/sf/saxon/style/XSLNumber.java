////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.Literal;
import net.sf.saxon.expr.StringLiteral;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.NumberInstruction;
import net.sf.saxon.expr.instruct.ValueOf;
import net.sf.saxon.expr.number.NumberFormatter;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.Numberer;
import net.sf.saxon.om.AttributeCollection;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.StringConverter;
import net.sf.saxon.type.ValidationFailure;
import net.sf.saxon.value.*;

/**
* An xsl:number element in the stylesheet. <br>
*/

public class XSLNumber extends StyleElement {

    private static final int SINGLE = 0;
    private static final int MULTI = 1;
    private static final int ANY = 2;
    private static final int SIMPLE = 3;

    private int level;
    /*@Nullable*/ private Pattern count = null;
    private Pattern from = null;
    private Expression select = null;
    private Expression value = null;
    private Expression format = null;
    private Expression groupSize = null;
    private Expression groupSeparator = null;
    private Expression letterValue = null;
    private Expression lang = null;
    private Expression ordinal = null;
    private Expression startAt = null;
    private NumberFormatter formatter = null;
    private Numberer numberer = null;
    private boolean hasVariablesInPatterns = false;

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction
    */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     * @return the item type returned
     */

    protected ItemType getReturnedItemType() {
        return NodeKindTest.TEXT;
    }

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();

		String selectAtt = null;
        String valueAtt = null;
		String countAtt = null;
		String fromAtt = null;
		String levelAtt = null;
		String formatAtt = null;
		String gsizeAtt = null;
		String gsepAtt = null;
		String langAtt = null;
		String letterValueAtt = null;
        String ordinalAtt = null;
        String startAtAtt = null;

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.SELECT)) {
        		selectAtt = atts.getValue(a);
            } else if (f.equals(StandardNames.VALUE)) {
        		valueAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.COUNT)) {
        		countAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.FROM)) {
        		fromAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.LEVEL)) {
        		levelAtt = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.FORMAT)) {
        		formatAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.LANG)) {
        		langAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.LETTER_VALUE)) {
        		letterValueAtt = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.GROUPING_SIZE)) {
        		gsizeAtt = Whitespace.trim(atts.getValue(a));
        	} else if (f.equals(StandardNames.GROUPING_SEPARATOR)) {
        		gsepAtt = atts.getValue(a);
            } else if (f.equals(StandardNames.ORDINAL)) {
                ordinalAtt = atts.getValue(a);
            } else if (f.equals(StandardNames.START_AT)) {
                startAtAtt = atts.getValue(a);
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (selectAtt != null) {
            select = makeExpression(selectAtt);
        }

        if (valueAtt!=null) {
            value = makeExpression(valueAtt);
            if (selectAtt != null) {
                compileError("The select attribute and value attribute must not both be present", "XTSE0975");
            }
            if (countAtt != null) {
                compileError("The count attribute and value attribute must not both be present", "XTSE0975");
            }
            if (fromAtt != null) {
                compileError("The from attribute and value attribute must not both be present", "XTSE0975");
            }
            if (levelAtt != null) {
                compileError("The level attribute and value attribute must not both be present", "XTSE0975");
            }
        }

        if (countAtt!=null) {
            count = makePattern(countAtt);
            // the following test is a very crude way of testing if the pattern might
            // contain variables, but it's good enough...
            if (countAtt.indexOf('$')>=0) {
                hasVariablesInPatterns = true;
            }
        }

        if (fromAtt!=null) {
            from = makePattern(fromAtt);
            if (fromAtt.indexOf('$')>=0) {
                hasVariablesInPatterns = true;
            }
        }

        if (levelAtt==null) {
            level = SINGLE;
        } else if (levelAtt.equals("single")) {
            level = SINGLE;
        } else if (levelAtt.equals("multiple")) {
            level = MULTI;
        } else if (levelAtt.equals("any")) {
            level = ANY;
        } else {
            compileError("Invalid value for level attribute", "XTSE0020");
        }

        if (level==SINGLE && from==null && count==null) {
            level=SIMPLE;
        }

        if (formatAtt != null) {
            format = makeAttributeValueTemplate(formatAtt);
            if (format instanceof StringLiteral) {
                formatter = new NumberFormatter();
                formatter.prepare(((StringLiteral)format).getStringValue());
            }
            // else we'll need to allocate the formatter at run-time
        } else {
            formatter = new NumberFormatter();
            formatter.prepare("1");
        }

        if (gsepAtt!=null && gsizeAtt!=null) {
            // the spec says that if only one is specified, it is ignored
            groupSize = makeAttributeValueTemplate(gsizeAtt);
            groupSeparator = makeAttributeValueTemplate(gsepAtt);
        }

        if (langAtt==null) {
            numberer = getConfiguration().makeNumberer(null, null);
        } else {
            lang = makeAttributeValueTemplate(langAtt);
            if (lang instanceof StringLiteral) {
                String language = ((StringLiteral)lang).getStringValue();
                if (language.length() != 0) {
                    ValidationFailure vf = StringConverter.STRING_TO_LANGUAGE.validate(language);
                    if (vf != null) {
                        compileError("The lang attribute must be a valid language code", "XTDE0030");
                        lang = new StringLiteral(StringValue.EMPTY_STRING);
                    }
                }
                numberer = getConfiguration().makeNumberer(language, null);
            }   // else we allocate a numberer at run-time
        }

        if (letterValueAtt != null) {
            letterValue = makeAttributeValueTemplate(letterValueAtt);
        }

        if (ordinalAtt != null) {
            ordinal = makeAttributeValueTemplate(ordinalAtt);
        }

        if (startAtAtt != null) {
            if (!getProcessorVersion().equals(DecimalValue.THREE)) {
                compileWarning("xsl:number/@start-at is ignored as XSLT 3.0 is not enabled", "XTSE0010");
            } else {
                startAt = makeAttributeValueTemplate(startAtAtt);
            }
        } else {
            startAt = Literal.makeLiteral(Int64Value.PLUS_ONE);
        }

    }

    public void validate(Declaration decl) throws XPathException {
        checkEmpty();

        select = typeCheck("select", select);
        value = typeCheck("value", value);
        format = typeCheck("format", format);
        groupSize = typeCheck("group-size", groupSize);
        groupSeparator = typeCheck("group-separator", groupSeparator);
        letterValue = typeCheck("letter-value", letterValue);
        ordinal = typeCheck("ordinal", ordinal);
        lang = typeCheck("lang", lang);
        from = typeCheck("from", from);
        count = typeCheck("count", count);
        startAt = typeCheck("start-at", startAt);

        if (select != null) {
            try {
                RoleLocator role =
                    new RoleLocator(RoleLocator.INSTRUCTION, "xsl:number/select", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                role.setErrorCode("XTTE1000");
                select = TypeChecker.staticTypeCheck(select,
                        SequenceType.SINGLE_NODE,
                        false, role, makeExpressionVisitor());
            } catch (XPathException err) {
                compileError(err);
            }
        }
    }

    public Expression compile(Executable exec, Declaration decl) throws XPathException {
        NumberInstruction expr = new NumberInstruction (exec.getConfiguration(),
                                        select,
                                        level,
                                        count,
                                        from,
                                        value,
                                        format,
                                        groupSize,
                                        groupSeparator,
                                        letterValue,
                                        ordinal,
                                        startAt,
                                        lang,
                                        formatter,
                                        numberer,
                                        hasVariablesInPatterns,
                                        xPath10ModeIsEnabled());
        int loc = getStaticContext().getLocationMap().allocateLocationId(getSystemId(), getLineNumber());
        expr.setLocationId(loc);
        ValueOf inst = new ValueOf(expr, false, false);
        inst.setLocationId(allocateLocationId(getSystemId(), getLineNumber()));
        inst.setIsNumberingInstruction();
        return inst;
    }

}
