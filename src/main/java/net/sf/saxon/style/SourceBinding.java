////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.DocumentInstr;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GeneralVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.*;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;


/**
* Helper class for xsl:variable and xsl:param elements. <br>
*/

public class SourceBinding {

    private StyleElement sourceElement;
    private StructuredQName name;
    private Expression select = null;
    private SequenceType declaredType = null;
    private SequenceType inferredType = null;
    protected String constantText = null;
    protected SlotManager slotManager = null;  // used only for global variable declarations
    protected GeneralVariable compiledVariable = null;
    private boolean textonly;

    private int properties;

    public static final int PRIVATE = 1;
    public static final int GLOBAL = 2;
    public static final int PARAM = 4;
    public static final int TUNNEL = 8;
    public static final int REQUIRED = 16;
    public static final int IMPLICITLY_REQUIRED = 32;
    public static final int ASSIGNABLE = 64;
    public static final int SELECT = 128;
    public static final int AS = 256;
    public static final int DISALLOWS_CONTENT = 512;
    public static final int GROUP = 1024;
    public static final int STATIC = 2048;

    // List of VariableReference objects that reference this XSLVariableDeclaration
    private List<BindingReference> references = new ArrayList<BindingReference>(10);

    public SourceBinding(StyleElement sourceElement) {
        this.sourceElement = sourceElement;
    }


    public void prepareAttributes(int permittedAttributes) throws XPathException {

		AttributeCollection atts = sourceElement.getAttributeList();

		String selectAtt = null;
        String asAtt = null;
        String requiredAtt = null;
        String tunnelAtt = null;
        String assignableAtt = null;
        String staticAtt = null;

		for (int a=0; a<atts.getLength(); a++) {
			String f = atts.getQName(a);
			if (f.equals(StandardNames.NAME)) {
                if (name == null || name.equals(errorName())) {
        		    processVariableName(atts.getValue(a));
                }
        	} else if (f.equals(StandardNames.SELECT)) {
                if ((permittedAttributes & SELECT) != 0) {
        		    selectAtt = atts.getValue(a);
                } else {
                    sourceElement.compileError("The select attribute is not permitted on a function parameter", "XTSE0760");
                }
        	} else if (((permittedAttributes & AS) != 0) && f.equals(StandardNames.AS)) {
        		asAtt = atts.getValue(a);
        	} else if (((permittedAttributes & REQUIRED) != 0) && f.equals(StandardNames.REQUIRED)) {
        		requiredAtt = Whitespace.trim(atts.getValue(a)) ;
            } else if (f.equals(StandardNames.TUNNEL)) {
        		tunnelAtt = Whitespace.trim(atts.getValue(a)) ;
            } else if (((permittedAttributes & STATIC) != 0) && f.equals(StandardNames.STATIC)) {
        		staticAtt = Whitespace.trim(atts.getValue(a)) ;
			} else if (((permittedAttributes & ASSIGNABLE) != 0) && atts.getLocalName(a).equals("assignable") &&
				NamespaceConstant.SAXON.equals(atts.getURI(a))) {
				assignableAtt = Whitespace.trim(atts.getValue(a));
			} else {
        		sourceElement.checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

		if (name == null) {
			sourceElement.reportAbsence("name");
			name = errorName();
		}

		if (selectAtt != null) {
			select = sourceElement.makeExpression(selectAtt);
		}

		if (requiredAtt != null) {
			if (requiredAtt.equals("yes")) {
				setProperty(REQUIRED, true);
			} else if (requiredAtt.equals("no")) {
				setProperty(REQUIRED, false);
			} else {
				sourceElement.compileError("The attribute 'required' must be set to 'yes' or 'no'", "XTSE0020");
			}
		}

		if (tunnelAtt != null) {
			if (tunnelAtt.equals("yes")) {
				if ((permittedAttributes & TUNNEL) == 0) {
					sourceElement.compileError("The only permitted value of the 'tunnel' attribute is 'no'",
						"XTSE0020");
				} else {
					setProperty(TUNNEL, true);
				}
			} else if (tunnelAtt.equals("no")) {
				setProperty(TUNNEL, false);
			} else {
				sourceElement.compileError("The attribute 'tunnel' must be set to 'yes' or 'no'", "XTSE0020");
			}
		}

		if (assignableAtt != null) {
			if (assignableAtt.equals("yes")) {
				setProperty(ASSIGNABLE, true);
			} else if (assignableAtt.equals("no")) {
				setProperty(ASSIGNABLE, false);
			} else {
				sourceElement.compileError("The attribute 'saxon:assignable' must be set to 'yes' or 'no'", "XTSE0020");
			}
		}

		if (staticAtt != null) {
			if (sourceElement.isXslt30Processor()) {
				if (staticAtt.equals("yes")) {
					setProperty(STATIC, true);
					setProperty(SourceBinding.DISALLOWS_CONTENT, true);
				} else if (staticAtt.equals("no")) {
					setProperty(STATIC, false);
				}
			} else if (!sourceElement.forwardsCompatibleModeIsEnabled()) {
				sourceElement.compileError("The attribute 'static' is not allowed", "XTSE0020");
			}
		}

		if (asAtt != null) {
			declaredType = sourceElement.makeSequenceType(asAtt);
		}
    }

    /**
     * Get the declaration in the stylesheet
     * @return the node in the stylesheet containing this variable binding
     */

    public StyleElement getSourceElement() {
        return sourceElement;
    }

    /**
     * Set the name of the variable
     * @param name the name of the variable as a QName
     */

    public void setVariableQName(StructuredQName name) {
        this.name = name;
    }

    /**
     * Process the QName of the variable. Validate the name and place it in the "name" field;
     * if invalid, construct an error message and place a dummy name in the "name" field for
     * recovery purposes.
     *  @param nameAttribute the lexical QName
     *  @throws XPathException if the name is invalid
     */

    private void processVariableName(String nameAttribute) throws XPathException {
        try {
            if (nameAttribute != null) {
                name = sourceElement.makeQName(nameAttribute);
            }
        } catch (NamespaceException err) {
            name = errorName();
            throw new XPathException("Invalid variable name: " + err.getMessage(), "XTSE0020");
        } catch (XPathException err) {
            name = errorName();
            throw new XPathException("Invalid variable name: " + err.getMessage() +
                (nameAttribute.startsWith("$") ? " (No '$' sign needed)" : ""), "XTSE0020");
        }
    }

    /**
     * Fallback name to be returned if there is anything wrong with the variable's real name
     * (after reporting an error)
     * @return a fallback name
     */

    private StructuredQName errorName() {
        return new StructuredQName("saxon", NamespaceConstant.SAXON, "error-variable-name");
    }

    /**
     * Validate the declartaion
     */

    public void validate() throws XPathException {
        if (select!=null && sourceElement.hasChildNodes()) {
            sourceElement.compileError("An " + name.getDisplayName() + " element with a select attribute must be empty", "XTSE0620");
        }
        if (hasProperty(DISALLOWS_CONTENT) && sourceElement.hasChildNodes()) {
            sourceElement.compileError("The xsl:param child of xsl:function must have no content", "XTSE0620");
        }
    }

    /**
     * Hook to allow additional validation of a parent element immediately after its
     * children have been validated.
     */

    public void postValidate() throws XPathException {
        checkAgainstRequiredType(declaredType);

        if (select==null && !hasProperty(DISALLOWS_CONTENT)) {
            textonly = true;
            AxisIterator kids = sourceElement.iterateAxis(AxisInfo.CHILD);
            NodeInfo first = kids.next();
            if (first == null) {
                if (declaredType == null) {
                    select = new StringLiteral(StringValue.EMPTY_STRING);
                } else {
                    if (sourceElement instanceof XSLLocalParam || sourceElement instanceof XSLGlobalParam) {
                        if (!hasProperty(REQUIRED)) {
                            if (Cardinality.allowsZero(declaredType.getCardinality())) {
                                select = Literal.makeEmptySequence();
                            } else {
                                // The implicit default value () is not valid for the required type, so
                                // it is treated as if there is no default
                                setProperty(IMPLICITLY_REQUIRED, true);
                            }
                        }
                    } else {
                        if (Cardinality.allowsZero(declaredType.getCardinality())) {
                            select = Literal.makeEmptySequence();
                        } else {
                            sourceElement.compileError("The implicit value () is not valid for the declared type", "XTTE0570");
                        }
                    }
                }
            } else {
                if (kids.next() == null) {
                    // there is exactly one child node
                    if (first.getNodeKind() == Type.TEXT) {
                        // it is a text node: optimize for this case
                        constantText = first.getStringValue();
                    }
                }

                // Determine if the temporary tree can only contain text nodes
                textonly = (sourceElement.getCommonChildItemType() == NodeKindTest.TEXT);
            }
        }
        select = sourceElement.typeCheck("select", select);
    }

    public boolean isStatic(){
      return hasProperty(STATIC);
    }

    /**
     * Check the supplied select expression against the required type.
     * @param required The type required by the variable declaration, or in the case
     * of xsl:with-param, the signature of the called template
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */

    public void checkAgainstRequiredType(SequenceType required)
    throws XPathException {
        try {

            if (required!=null) {
                // check that the expression is consistent with the required type
                if (select != null) {
                    int category = RoleLocator.VARIABLE;
                    String errorCode = "XTTE0570";
                    if (sourceElement instanceof XSLLocalParam) {
                        category = RoleLocator.PARAM;
                        errorCode = "XTTE0600";
                    } else if (sourceElement instanceof XSLWithParam) {
                        category = RoleLocator.PARAM;
                        errorCode = "XTTE0590";
                    }
                    RoleLocator role = new RoleLocator(category, name.getDisplayName(), 0);
                    //role.setSourceLocator(new ExpressionLocation(this));
                    role.setErrorCode(errorCode);
                    select = TypeChecker.staticTypeCheck(select, required, false, role, sourceElement.makeExpressionVisitor());
                } else {
                    // do the check later
                }
            }
        } catch (XPathException err) {
            err.setLocator(sourceElement);   // because the expression wasn't yet linked into the module
            sourceElement.compileError(err);
            select = new ErrorExpression(err);
        }
    }


    /**
     * Get the name of the variable
     * @return the variable's name
     */

    public StructuredQName getVariableQName() {
        if (name == null) {
            try {
                processVariableName(sourceElement.getAttributeValue("", "name"));
            } catch (XPathException e) {
                return errorName();
            }
        }
        return name;
    }

    /**
     * Set a boolean property of the variable
     * @param prop the property to be set (e.g. {@link #TUNNEL})
     * @param flag true to set the property on, false to set it off
     */

    public void setProperty(int prop, boolean flag) {
        if (flag) {
            properties |= prop;
        } else {
            properties &= (~prop);
        }
    }

    /**
     * Get a boolean property of the variable
     * @param prop the property whose value is required
     * @return true if the variable has the specified property, otherwise false
     */

    public boolean hasProperty(int prop) {
        return (properties & prop) != 0;
    }

    /**
     * Get all the known references to this variable
     * @return the list of references
     */

    public List<BindingReference> getReferences() {
        return references;
    }

    /**
     * Get the SlotManager associated with this stylesheet construct. The SlotManager contains the
     * information needed to manage the local stack frames used by run-time instances of the code.
     * @return the associated SlotManager object
     */

    public SlotManager getSlotManager() {
        return slotManager;
    }

    /**
     * If the element contains a sequence constructor, convert this to an expression and assign it to the
     * select attribute
     * @param exec the executable
     * @param decl the declaration being compiled
     * @throws XPathException if a static error is found, for example a type error
     */
	public void handleSequenceConstructor(Executable exec, Declaration decl) throws XPathException {
		// handle the "temporary tree" case by creating a Document sub-instruction
		// to construct and return a document node.
		if (sourceElement.hasChildNodes()) {
			if (declaredType == null) {
				DocumentInstr doc = new DocumentInstr(textonly, constantText, sourceElement.getBaseURI());
				Expression b = sourceElement.compileSequenceConstructor(
					exec, decl, sourceElement.iterateAxis(AxisInfo.CHILD), true);
				if (b == null) {
					b = Literal.makeEmptySequence();
				}
				doc.setContentExpression(b);
				select = doc;
			} else {
				select = sourceElement.compileSequenceConstructor(
					exec, decl, sourceElement.iterateAxis(AxisInfo.CHILD), true);
				if (select == null) {
					select = Literal.makeEmptySequence();
				}
				try {
					assert select != null;
					select.setContainer(sourceElement);
					RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, name.getDisplayName(), 0);
					role.setErrorCode("XTTE0570");
					// role.setSourceLocator(new ExpressionLocation(this));
					ExpressionVisitor expressionVisitor = sourceElement.makeExpressionVisitor();
					select = expressionVisitor.simplify(select);
					select = TypeChecker.staticTypeCheck(select, declaredType, false, role, expressionVisitor);
				} catch (XPathException err) {
					err.setLocator(sourceElement);
					sourceElement.compileError(err);
					select = new ErrorExpression(err);
				}
			}
		}
	}

    /**
     * Get the type actually declared for the attribute
     * @return the type appearing in the "as" attribute, or null if the attribute was absent
     */

    public SequenceType getDeclaredType() {
        if (declaredType == null) {
            // may be handling a forwards reference - see hof-038
            String asAtt = sourceElement.getAttributeValue("", "as");
            if (asAtt == null) {
                return null;
            } else {
                try {
                    declaredType = sourceElement.makeSequenceType(asAtt);
                } catch (XPathException err) {
                    // the error will be reported when we get round to processing the function declaration
                }
            }

        }
        return declaredType;
    }

    /**
     * Get the select expression actually appearing in the variable declaration
     * @return the select expression as it appears, or null if it is absent
     */

    public Expression getSelectExpression() {
        return select;
    }

    /**
     * Get the best available static type of the variable.
     * @param useContentRules set to true if the standard rules for xsl:variable and similar elements apply,
     * whereby the element's contained sequence constructor substitutes for the select attribute
     * @return the static type declared for the variable, or inferred from its initialization
    */

    public SequenceType getInferredType(boolean useContentRules) {
        if (inferredType != null) {
            return inferredType;
        }
        if (hasProperty(PARAM) || hasProperty(ASSIGNABLE)) {
            return (inferredType = (declaredType == null ? SequenceType.ANY_SEQUENCE : declaredType));
        }
        if (select != null) {
            TypeHierarchy th = sourceElement.getConfiguration().getTypeHierarchy();
            if (Literal.isEmptySequence(select)) {
                // returning Type.EMPTY gives problems with static type checking
                return (inferredType = (declaredType == null ? SequenceType.ANY_SEQUENCE : declaredType));
            }
            ItemType actual = select.getItemType(th);
            int card = select.getCardinality();
            if (declaredType != null) {
                if (!th.isSubType(actual, declaredType.getPrimaryType())) {
                    actual = declaredType.getPrimaryType();
                }
                if (!Cardinality.subsumes(declaredType.getCardinality(), card)) {
                    card = declaredType.getCardinality();
                }
            }
            inferredType = SequenceType.makeSequenceType(actual, card);
            return inferredType;
        }
        if (useContentRules) {
            if (sourceElement.hasChildNodes()) {
                if (declaredType == null) {
                    return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.EXACTLY_ONE);
                } else {
                    return declaredType;
                }
            } else {
                if (declaredType == null) {
                    // no select attribute or content: value is an empty string
                    return SequenceType.SINGLE_STRING;
                } else {
                    return declaredType;
                }
            }
        }
        return declaredType;
    }

    /**
    * Method called by VariableReference to register the variable reference for
    * subsequent fixup
     * @param ref the variable reference being registered
    */

    public void registerReference(BindingReference ref) {
        references.add(ref);
    }

    /**
    * Notify all references to this variable of the data type
    */

    public void fixupReferences() throws XPathException {
        final SequenceType type = getInferredType(true);
        final TypeHierarchy th = sourceElement.getConfiguration().getTypeHierarchy();
        GroundedValue constantValue = null;
        int properties = 0;
        if (!hasProperty(ASSIGNABLE) && !hasProperty(PARAM)) {
            if (select instanceof Literal) {
                // we can't rely on the constant value because it hasn't yet been type-checked,
                // which could change it (eg by numeric promotion). Rather than attempt all the type-checking
                // now, we do a quick check. See test bug64
                int relation = th.relationship(select.getItemType(th), type.getPrimaryType());
                if (relation == TypeHierarchy.SAME_TYPE || relation == TypeHierarchy.SUBSUMED_BY) {
                    constantValue = ((Literal) select).getValue();
                }
            }
            if (select != null) {
                properties = select.getSpecialProperties();
            }
        }
        for (BindingReference reference : references) {
            reference.setStaticType(type, constantValue, properties);
        }
    }

    /**
     * Notify all variable references of the Binding instruction
     * @param binding the Binding that represents this variable declaration in the executable code tree
    */

    protected void fixupBinding(Binding binding) {
        for (BindingReference reference : references) {
            (reference).fixup(binding);
        }
    }

    /**
     * Set the number of references to this variable. This code is invoked only for a global variable,
     * and only if there is at least one reference.
     * @param var the variable
     */

    protected void setReferenceCount(GeneralVariable var) {
        var.setReferenceCount(10);  // TODO: temporary
    }

    /**
     * Get the compiled variable
     * @return the compiled variable if it has been compiled, or null otherwise
     */

    /*@Nullable*/ public GeneralVariable getCompiledVariable() {
        return compiledVariable;
    }
}

