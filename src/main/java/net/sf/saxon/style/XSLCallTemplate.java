////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.Template;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

/**
* An xsl:call-template element in the stylesheet
*/

public class XSLCallTemplate extends StyleElement {

    private StructuredQName calledTemplateName;   // the name of the called template
    /*@Nullable*/ private XSLTemplate template = null;
    private boolean useTailRecursion = false;
    private Expression calledTemplateExpression;    // allows name to be an AVT

    /**
     * Determine whether the called template can be specified as an AVT
     * @return true if the template name can be specified at run-time, that is, if this is a saxon:call-template
     * instruction
     */

    protected boolean allowAVT() {
        return false;
    }

    /**
    * Determine whether this node is an instruction.
    * @return true - it is an instruction
    */

    public boolean isInstruction() {
        return true;
    }

    private boolean gettingReturnedItemType = false;

    /**
     * Determine the type of item returned by this instruction (only relevant if
     * it is an instruction).
     * @return the item type returned
     */
    
    protected ItemType getReturnedItemType() {
        if (template==null || gettingReturnedItemType) {
            return AnyItemType.getInstance();
        } else {
            // protect against infinite recursion
            gettingReturnedItemType = true;
            ItemType result = template.getReturnedItemType();
            gettingReturnedItemType = false;
            return result;
        }
    }

    public void prepareAttributes() throws XPathException {

		AttributeCollection atts = getAttributeList();

        String nameAttribute = null;

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.NAME)) {
        		nameAttribute = Whitespace.trim(atts.getValue(a));
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (nameAttribute==null) {
            calledTemplateName = new StructuredQName("saxon", NamespaceConstant.SAXON, "error-template");
            reportAbsence("name");
            return;
        }

        if (allowAVT() && nameAttribute.indexOf('{')>=0) {
            calledTemplateExpression = makeAttributeValueTemplate(nameAttribute);
        } else {
            try {
                calledTemplateName = makeQName(nameAttribute);
            } catch (NamespaceException err) {
                calledTemplateName = new StructuredQName("saxon", NamespaceConstant.SAXON, "error-template");
                compileError(err.getMessage(), "XTSE0280");
            } catch (XPathException err) {
                calledTemplateName = new StructuredQName("saxon", NamespaceConstant.SAXON, "error-template");
                compileError(err.getMessage(), err.getErrorCodeQName());
            }
        }
    }

    public void validate(Declaration decl) throws XPathException {
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while (true) {
            NodeInfo child = kids.next();
            if (child == null) {
                break;
            }
            if (child instanceof XSLWithParam) {
                // OK;
            } else if (child instanceof XSLFallback && mayContainFallback()) {
                // xsl:fallback is not allowed on xsl:call-template, but is allowed on saxon:call-template (cheat!)
            } else if (child.getNodeKind() == Type.TEXT) {
                    // with xml:space=preserve, white space nodes may still be there
                if (!Whitespace.isWhite(child.getStringValueCS())) {
                    compileError("No character data is allowed within xsl:call-template", "XTSE0010");
                }
            } else {
                compileError("Child element " + Err.wrap(child.getDisplayName(), Err.ELEMENT) +
                        " is not allowed as a child of xsl:call-template", "XTSE0010");
            }
        }
        if (calledTemplateExpression==null &&
                !(calledTemplateName.getURI().equals(NamespaceConstant.SAXON) &&
                    calledTemplateName.getLocalPart().equals("error-template"))) {
            template = findTemplate(calledTemplateName);
            if (template==null) {
                return;
            }
        }
        calledTemplateExpression = typeCheck("name", calledTemplateExpression);
    }

    public void postValidate() throws XPathException {
        // check that a parameter is supplied for each required parameter
        // of the called template

        if (template != null) {
            AxisIterator declaredParams = template.iterateAxis(AxisInfo.CHILD);
            while(true) {
                NodeInfo param = declaredParams.next();
                if (param == null) {
                    break;
                }
                if (param instanceof XSLLocalParam && ((XSLLocalParam)param).isRequiredParam()
                                              && !((XSLLocalParam)param).isTunnelParam()) {
                    AxisIterator actualParams = iterateAxis(AxisInfo.CHILD);
                    boolean ok = false;
                    while(true) {
                        NodeInfo withParam = actualParams.next();
                        if (withParam == null) {
                            break;
                        }
                        if (withParam instanceof XSLWithParam &&
                                ((XSLWithParam)withParam).getVariableQName().equals(
                                    ((XSLLocalParam)param).getVariableQName())) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) {
                        compileError("No value supplied for required parameter " +
                                Err.wrap(((XSLLocalParam)param).getVariableQName().getDisplayName(), Err.VARIABLE), "XTSE0690");
                    }
                }
            }


            // check that every supplied parameter is declared in the called
            // template

            AxisIterator actualParams = iterateAxis(AxisInfo.CHILD);
            while(true) {
                NodeInfo w = actualParams.next();
                if (w == null) {
                    break;
                }
                if (w instanceof XSLWithParam && !((XSLWithParam)w).isTunnelParam()) {
                    XSLWithParam withParam = (XSLWithParam)w;
                    assert template != null;
                    AxisIterator formalParams = template.iterateAxis(AxisInfo.CHILD);
                    boolean ok = false;
                    while(true) {
                        NodeInfo param = formalParams.next();
                        if (param == null) {
                            break;
                        }
                        if (param instanceof XSLLocalParam &&
                                ((XSLLocalParam)param).getVariableQName().equals(withParam.getVariableQName())
                                /* TODO spec bug 10934: && !((XSLParam)param).isTunnelParam() */
                                ) {
                            ok = true;
                            SequenceType required = ((XSLLocalParam)param).getRequiredType();
                            withParam.checkAgainstRequiredType(required);
                            break;
                        }
                    }
                    if (!ok) {
                        if (!xPath10ModeIsEnabled()) {
                            compileError("Parameter " +
                                    withParam.getVariableQName().getDisplayName() +
                                    " is not declared in the called template", "XTSE0680");
                        }
                    }
                }
            }
        }
    }

    private XSLTemplate findTemplate(StructuredQName templateName)
    throws XPathException {

        PrincipalStylesheetModule psm = getPrincipalStylesheetModule();
        XSLTemplate template = psm.getNamedTemplate(templateName);
        if (template == null) {
            compileError("No template exists named " + calledTemplateName, "XTSE0650");
        }
        return template;
    }

    /**
     * Mark tail-recursive calls on templates and functions.
     * For most instructions, this does nothing.
    */

    public boolean markTailCalls() {
        useTailRecursion = true;
        return true;
    }


    public Expression compile(Executable exec, Declaration decl) throws XPathException {
        Template target = null;
        NamespaceResolver nsContext = null;

        if (calledTemplateExpression==null) {
            if (template==null) {
                return null;   // error already reported
            }
            target = template.getCompiledTemplate();
        } else {
            //getPrincipalStyleSheet().setRequireRuntimeTemplateMap(true);
            nsContext = makeNamespaceContext();
        }

        CallTemplate call = new CallTemplate (
                                    target,
                                    useTailRecursion,
                                    calledTemplateExpression,
                                    nsContext );
        call.setActualParameters(getWithParamInstructions(exec, decl, false, call),
                                 getWithParamInstructions(exec, decl, true, call));
        return call;
    }

}

