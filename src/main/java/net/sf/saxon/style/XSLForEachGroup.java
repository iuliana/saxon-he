////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.ForEachGroup;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.expr.sort.SortKeyDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.pattern.PatternSponsor;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

/**
* Handler for xsl:for-each-group elements in stylesheet. This is a new instruction
* defined in XSLT 2.0
*/

public final class XSLForEachGroup extends StyleElement {

    /*@Nullable*/ private Expression select = null;
    private Expression groupBy = null;
    private Expression groupAdjacent = null;
    private Pattern starting = null;
    private Pattern ending = null;
    private Expression collationName;
    private SourceBinding groupSourceBinding = null;
    private SourceBinding keySourceBinding = null;
    private boolean composite = false;

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction
    */

    public boolean isInstruction() {
        return true;
    }

    /**
     * Specify that xsl:sort is a permitted child
     */

    protected boolean isPermittedChild(StyleElement child) {
        return (child instanceof XSLSort);
    }

    /**
    * Determine whether this type of element is allowed to contain a template-body
    * @return true: yes, it may contain a template-body
    */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();

		String selectAtt = null;
		String groupByAtt = null;
		String groupAdjacentAtt = null;
		String startingAtt = null;
		String endingAtt = null;
        String collationAtt = null;

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
            if (f.equals(StandardNames.SELECT)) {
        		selectAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.GROUP_BY)) {
        		groupByAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.GROUP_ADJACENT)) {
        		groupAdjacentAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.GROUP_STARTING_WITH)) {
        		startingAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.GROUP_ENDING_WITH)) {
        		endingAtt = atts.getValue(a);
        	} else if (f.equals(StandardNames.COLLATION)) {
        		collationAtt = Whitespace.trim(atts.getValue(a));
            } else if (f.equals("bind-group")) {
                try {
                    StructuredQName groupingVar = makeQName(atts.getValue(a));
                    groupSourceBinding = new SourceBinding(this);
                    groupSourceBinding.setVariableQName(groupingVar);
                    groupSourceBinding.setProperty(SourceBinding.GROUP, true);
                } catch (NamespaceException e) {
                    compileError(e.getMessage());
                }
            } else if (f.equals("bind-grouping-key")) {
                try {
                    StructuredQName keyVar = makeQName(atts.getValue(a));
                    keySourceBinding = new SourceBinding(this);
                    keySourceBinding.setVariableQName(keyVar);
                } catch (NamespaceException e) {
                    compileError(e.getMessage());
                }
            } else if (f.equals("composite")) {
                if (!isXslt30Processor()) {
                    compileError("The 'composite' attribute requires XSLT 3.0");
                }
                String val = Whitespace.trim(atts.getValue(a));
                if (val.equals("yes")) {
                    composite = true;
                } else if (val.equals("no")) {
                    composite = false;
                } else {
                    compileError("The value of the 'composite' attribute must be 'yes' or 'no'");
                }
            } else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (selectAtt==null) {
            reportAbsence("select");
            select = Literal.makeEmptySequence(); // for error recovery
        } else {
            select = makeExpression(selectAtt);
        }

        int c = (groupByAtt==null ? 0 : 1) +
                (groupAdjacentAtt==null ? 0 : 1) +
                (startingAtt==null ? 0 : 1) +
                (endingAtt==null ? 0 : 1);
        if (c!=1) {
            compileError("Exactly one of the attributes group-by, group-adjacent, group-starting-with, " +
                    "and group-ending-with must be specified", "XTSE1080");
        }

        if (groupByAtt != null) {
            groupBy = makeExpression(groupByAtt);
        }

        if (groupAdjacentAtt != null) {
            groupAdjacent = makeExpression(groupAdjacentAtt);
        }

        if (startingAtt != null) {
            starting = makePattern(startingAtt);
        }

        if (endingAtt != null) {
            ending = makePattern(endingAtt);
        }

        if (collationAtt != null) {
            if (groupBy==null && groupAdjacent==null) {
                compileError("A collation may be specified only if group-by or group-adjacent is specified", "XTSE1090");
            } else {
                collationName = makeAttributeValueTemplate(collationAtt);
                if (collationName instanceof StringLiteral) {
                    String collation = ((StringLiteral)collationName).getStringValue();
                    URI collationURI;
                    try {
                        collationURI = new URI(collation);
                        if (!collationURI.isAbsolute()) {
                            URI base = new URI(getBaseURI());
                            collationURI = base.resolve(collationURI);
                            collationName = new StringLiteral(collationURI.toString());
                        }
                    } catch (URISyntaxException err) {
                        compileError("Collation name '" + collationName + "' is not a valid URI", "XTDE1110");
                        collationName = new StringLiteral(NamespaceConstant.CODEPOINT_COLLATION_URI);
                    }
                }
            }
        } else {
            String defaultCollation = getDefaultCollationName();
            if (defaultCollation != null) {
                collationName = new StringLiteral(defaultCollation);
            }
        }

        if ((groupSourceBinding != null || keySourceBinding != null) &&
                !isXslt30Processor()) {
            compileError("for-each-group binding variables are allowed only with XSLT 3.0");
        }

        if (keySourceBinding != null && groupBy==null && groupAdjacent==null) {
            compileError("A grouping-key binding can be specified only with group-by or group-adjacent");
        }

        if (keySourceBinding != null && groupSourceBinding != null &&
                keySourceBinding.getVariableQName().equals(groupSourceBinding.getVariableQName())) {
            compileError("The group variable and grouping-key variable must have different names");
        }

        if (composite && (starting != null || ending != null)) {
            compileError("The value composite='yes' cannot be used with " +
                    (starting==null ? "grouping-ending-with" : "group-starting-with"));
        }
    }

    public void validate(Declaration decl) throws XPathException {
        checkSortComesFirst(false);
        select = typeCheck("select", select);

        ExpressionVisitor visitor = makeExpressionVisitor();
        if (groupBy != null) {
            groupBy = typeCheck("group-by", groupBy);
            try {
                RoleLocator role =
                    new RoleLocator(RoleLocator.INSTRUCTION, "xsl:for-each-group/group-by", 0);
                groupBy = TypeChecker.staticTypeCheck(groupBy,
                        SequenceType.ATOMIC_SEQUENCE,
                        false, role, visitor);
            } catch (XPathException err) {
                compileError(err);
            }
        } else if (groupAdjacent != null) {
            groupAdjacent = typeCheck("group-adjacent", groupAdjacent);
            try {
                RoleLocator role =
                    new RoleLocator(RoleLocator.INSTRUCTION, "xsl:for-each-group/group-adjacent", 0);
                role.setErrorCode("XTTE1100");
                groupAdjacent = TypeChecker.staticTypeCheck(groupAdjacent,
                        (composite ? SequenceType.ATOMIC_SEQUENCE : SequenceType.SINGLE_ATOMIC),
                        false, role, visitor);
            } catch (XPathException err) {
                compileError(err);
            }
        }

        starting = typeCheck("starting", starting);
        ending = typeCheck("ending", ending);

        if ((starting != null || ending != null) && !visitor.getStaticContext().getXPathLanguageLevel().equals(DecimalValue.THREE)) {
            try {
                RoleLocator role =
                    new RoleLocator(RoleLocator.INSTRUCTION, "xsl:for-each-group/select", 0);
                role.setErrorCode("XTTE1120");
                select = TypeChecker.staticTypeCheck(select,
                        SequenceType.NODE_SEQUENCE,
                        false, role, visitor);
            } catch (XPathException err) {
                String prefix = (starting != null ?
                        "With group-starting-with attribute: " :
                        "With group-ending-with attribute: ");
                compileError(prefix + err.getMessage(), err.getErrorCodeQName());
            }
        }
        if (!hasChildNodes()) {
            compileWarning("An empty xsl:for-each-group instruction has no effect", SaxonErrorCode.SXWN9009);
        }

    }

    @Override
    public void postValidate() throws XPathException {
        // Check that the variables bound in the xsl:for-each-group are not used inappropriately in any child xsl:sort
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while (true) {
			NodeInfo child = kids.next();
			if (child == null) {
				break;
			}
			if (child instanceof XSLSort) {
				if (groupSourceBinding != null) {
                    SortKeyDefinition sk = ((XSLSort)child).getSortKeyDefinition();
                    checkNoDependency(sk.getLanguage(), groupSourceBinding);
                    checkNoDependency(sk.getOrder(), groupSourceBinding);
                    checkNoDependency(sk.getCaseOrder(), groupSourceBinding);
                    checkNoDependency(sk.getDataTypeExpression(), groupSourceBinding);
                    checkNoDependency(sk.getStable(), groupSourceBinding);
                    checkNoDependency(sk.getCollationNameExpression(), groupSourceBinding);
                }
                if (keySourceBinding != null) {
                    SortKeyDefinition sk = ((XSLSort)child).getSortKeyDefinition();
                    checkNoDependency(sk.getLanguage(), keySourceBinding);
                    checkNoDependency(sk.getOrder(), keySourceBinding);
                    checkNoDependency(sk.getCaseOrder(), keySourceBinding);
                    checkNoDependency(sk.getDataTypeExpression(), keySourceBinding);
                    checkNoDependency(sk.getStable(), keySourceBinding);
                    checkNoDependency(sk.getCollationNameExpression(), keySourceBinding);
                }
			}
		}
    }

    private static void checkNoDependency(Expression exp, SourceBinding binding) throws XPathException {
        if (exp != null) {
            if (exp instanceof VariableReference) {
                for (BindingReference ref : binding.getReferences()) {
                    if (ref == exp) {
                        throw new XPathException("Variable bound by xsl:for-each-group " +
                                "must not be referenced in an AVT attribute of a contained xsl:sort element", "XTSE3220");
                    }
                }
            } else {
                for (Iterator<Expression> sub = exp.iterateSubExpressions(); sub.hasNext();) {
                    checkNoDependency(sub.next(), binding);
                }
            }
        }
    }

    public Expression compile(Executable exec, Declaration decl) throws XPathException {

        StringCollator collator = null;
        if (collationName instanceof StringLiteral) {
            // if the collation name is constant, then we've already resolved it against the base URI
            final String uri = ((StringLiteral)collationName).getStringValue();
            collator = getPrincipalStylesheetModule().findCollation(uri, getBaseURI());
            if (collator==null) {
                compileError("The collation name '" + collationName + "' has not been defined", "XTDE1110");
            }
        }

        byte algorithm = 0;
        Expression key = null;
        if (groupBy != null) {
            algorithm = ForEachGroup.GROUP_BY;
            key = groupBy;
        } else if (groupAdjacent != null) {
            algorithm = ForEachGroup.GROUP_ADJACENT;
            key = groupAdjacent;
        } else if (starting != null) {
            algorithm = ForEachGroup.GROUP_STARTING;
            key = new PatternSponsor(starting);
        } else if (ending != null) {
            algorithm = ForEachGroup.GROUP_ENDING;
            key = new PatternSponsor(ending);
        }

        Expression action = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), true);
        if (action == null) {
            // body of for-each is empty: it's a no-op.
            return Literal.makeEmptySequence();
        }
        try {
            LocalVariableBinding groupBinding = null;
            LocalVariableBinding keyBinding = null;
            if (groupSourceBinding != null) {
                groupBinding = new LocalVariableBinding(groupSourceBinding.getVariableQName(), SequenceType.ANY_SEQUENCE);
                groupSourceBinding.fixupBinding(groupBinding);
            }
            if (keySourceBinding != null) {
                keyBinding = new LocalVariableBinding(keySourceBinding.getVariableQName(), SequenceType.ANY_SEQUENCE);
                keySourceBinding.fixupBinding(keyBinding);
            }
            ForEachGroup instr = new ForEachGroup(    select,
                                        makeExpressionVisitor().simplify(action),
                                        algorithm,
                                        key,
                                        collator,
                                        collationName,
                                        ExpressionTool.getBaseURI(staticContext, action, true),
                                        makeSortKeys(decl) );

            instr.setGroupBinding(groupBinding);
            instr.setKeyBinding(keyBinding);
            instr.setComposite(composite);
            return instr;
        } catch (XPathException e) {
            compileError(e);
            return null;
        }

    }

    public SourceBinding getGroupSourceBinding() {
        return groupSourceBinding;
    }

    public SourceBinding getKeyBinding() {
        return keySourceBinding;
    }

    public StructuredQName getGroupBindingName() {
        return (groupSourceBinding == null ? null : groupSourceBinding.getVariableQName());
    }

    public StructuredQName getKeyBindingName() {
        return (keySourceBinding == null ? null : keySourceBinding.getVariableQName());
    }

}

