////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
* Handler for xsl:function elements in stylesheet (XSLT 2.0). <BR>
* Attributes: <br>
* name gives the name of the function
* saxon:memo-function=yes|no indicates whether it acts as a memo function.
*/

public class XSLFunction extends StyleElement implements StylesheetProcedure {

    /*@Nullable*/ private String nameAtt = null;
    private String asAtt = null;
    private String overrideAtt = null;
    private SequenceType resultType;
    private String functionName;
    private SlotManager stackFrameMap;
    private boolean memoFunction = false;
    private boolean override = true;
    private int numberOfArguments = -1;  // -1 means not yet known
    private UserFunction compiledFunction;

    // List of UserFunctionCall objects that reference this XSLFunction
    List<UserFunctionReference> references = new ArrayList<UserFunctionReference>(10);

    /**
     * Method called by UserFunctionCall to register the function call for
     * subsequent fixup.
     * @param ref the UserFunctionCall to be registered
    */

    public void registerReference(UserFunctionReference ref) {
        references.add(ref);
    }

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
        overrideAtt = "yes";
    	for (int a=0; a<atts.getLength(); a++) {
            String uri = atts.getURI(a);
			String local = atts.getLocalName(a);
            if ("".equals(uri)) {
                if (local.equals(StandardNames.NAME)) {
                    nameAtt = Whitespace.trim(atts.getValue(a));
                    assert (nameAtt != null);
                    if (nameAtt.indexOf(':')<0) {
                        compileError("Function name must have a namespace prefix", "XTSE0740");
                    }
                    try {
                        setObjectName(makeQName(nameAtt));
                    } catch (NamespaceException err) {
                        compileError(err.getMessage(), "XTSE0280");
                    } catch (XPathException err) {
                        compileError(err);
                    }
                } else if (local.equals(StandardNames.AS)) {
                    asAtt = atts.getValue(a);
                } else if (local.equals(StandardNames.OVERRIDE)) {
                    overrideAtt = Whitespace.trim(atts.getValue(a));
                    if ("yes".equals(overrideAtt)) {
                        override = true;
                    } else if ("no".equals(overrideAtt)) {
                        override = false;
                    } else {
                        override = true;
                        compileError("override must be 'yes' or 'no'", "XTSE0020");
                    }
                } else {
                    checkUnknownAttribute(atts.getNodeName(a));
                }
            } else if (local.equals("memo-function") && uri.equals(NamespaceConstant.SAXON)) {
                String memoAtt = Whitespace.trim(atts.getValue(a));
                if ("yes".equals(memoAtt)) {
                    memoFunction = true;
                } else if ("no".equals(memoAtt)) {
                    memoFunction = false;
                } else {
                    compileError("saxon:memo-function must be 'yes' or 'no'", "XTSE0020");
                }
        	} else {
        		checkUnknownAttribute(atts.getNodeName(a));
        	}
        }

        if (nameAtt == null) {
            reportAbsence("name");
            nameAtt="xsl:unnamed-function-" + generateId();
        }

        if (asAtt == null) {
            resultType = SequenceType.ANY_SEQUENCE;
        } else {
            resultType = makeSequenceType(asAtt);
        }

        functionName = nameAtt;
    }

    private String generateId() {
        FastStringBuffer buff = new FastStringBuffer(FastStringBuffer.TINY);
        generateId(buff);
        return buff.toString();
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be -1.
     */

    /*@NotNull*/
    public StructuredQName getObjectName() {
        StructuredQName qn = super.getObjectName();
        if (qn == null) {
            nameAtt = Whitespace.trim(getAttributeValue("", "name"));
            if (nameAtt == null) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-function" + generateId());
            }
            try {
                qn = makeQName(nameAtt);
                setObjectName(qn);
            } catch (NamespaceException err) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-function" + generateId());
            } catch (XPathException err) {
                return new StructuredQName("saxon", NamespaceConstant.SAXON, "badly-named-function" + generateId());
            }
        }
        return qn;
    }

    /**
    * Determine whether this type of element is allowed to contain a template-body.
    * @return true: yes, it may contain a general template-body
    */

    public boolean mayContainSequenceConstructor() {
        return true;
    }

    protected boolean mayContainParam(String attName) {
        return !"required".equals(attName);
    }

    /**
     * Specify that xsl:param is a permitted child
     */

    protected boolean isPermittedChild(StyleElement child) {
        return (child instanceof XSLLocalParam);
    }
    /**
    * Is override="yes"?.
    * @return true if override="yes" was specified, otherwise false
    */

    public boolean isOverriding() {
        if (overrideAtt == null) {
            // this is a forwards reference
            try {
                prepareAttributes();
            } catch (XPathException e) {
                // no action: error will be caught later
            }
        }
        return override;
    }

    protected void index(Declaration decl, PrincipalStylesheetModule top) throws XPathException {
        top.indexFunction(decl);
    }

    /**
    * Notify all references to this function of the data type.
     * @throws XPathException
    */

    public void fixupReferences() throws XPathException {
        for (UserFunctionReference reference : references) {
            if (reference instanceof UserFunctionCall) {
                ((UserFunctionCall)reference).setStaticType(resultType);
            }
        }
        super.fixupReferences();
    }

    public void validate(Declaration decl) throws XPathException {

        stackFrameMap = getConfiguration().makeSlotManager();

        // check the element is at the top level of the stylesheet

        checkTopLevel("XTSE0010");
        getNumberOfArguments();

    }


    /**
     * Compile the function definition to create an executable representation
     * The compileDeclaration() method has the side-effect of binding
     * all references to the function to the executable representation
     * (a UserFunction object)
     * @throws XPathException
     */

    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {
        compileAsExpression(exec, decl);
    }

    /**
     * Compile the function into a UserFunction object, which treats the function
     * body as a single XPath expression. This involves recursively translating
     * xsl:variable declarations into let expressions, withe the action part of the
     * let expression containing the rest of the function body.
     * The UserFunction that is created will be linked from all calls to
     * this function, so nothing else needs to be done with the result. If there are
     * no calls to it, the compiled function will be garbage-collected away.
     * @param exec the Executable
     * @param decl this xsl:function declaration
     * @throws XPathException if a failure occurs
     */

    private void compileAsExpression(Executable exec, Declaration decl) throws XPathException {
        Expression exp = compileSequenceConstructor(exec, decl, iterateAxis(AxisInfo.CHILD), false);
        if (exp == null) {
            exp = Literal.makeEmptySequence();
        } else {
            ExpressionVisitor visitor = makeExpressionVisitor();
            exp = exp.simplify(visitor);
        }

        if (exec.getConfiguration().isCompileWithTracing()) {
            TraceExpression trace = new TraceExpression(exp);
            trace.setConstructType(StandardNames.XSL_FUNCTION);
            trace.setNamespaceResolver(getNamespaceResolver());
            trace.setObjectName(getObjectName());
            exp = trace;
        }

        UserFunction fn = exec.getConfiguration().newUserFunction(memoFunction);
        fn.setHostLanguage(Configuration.XSLT);
        fn.setBody(exp);
        fn.setFunctionName(getObjectName());
        setParameterDefinitions(fn);
        fn.setResultType(getResultType());
        fn.setLineNumber(getLineNumber());
        fn.setSystemId(getSystemId());
        fn.setStackFrameMap(stackFrameMap);
        fn.setExecutable(exec);
        compiledFunction = fn;
        fixupInstruction(fn);

        if (memoFunction && !fn.isMemoFunction()) {
            compileWarning("Memo functions are not available in Saxon-HE: saxon:memo-function attribute ignored",
                    SaxonErrorCode.SXWN9011);
        }
    }

    public void typeCheckBody() throws XPathException {
        Expression exp = compiledFunction.getBody();
        ExpressionTool.resetPropertiesWithinSubtree(exp);
        Expression exp2 = exp;
        ExpressionVisitor visitor = makeExpressionVisitor();
        try {
            // We've already done the typecheck of each XPath expression, but it's worth doing again at this
            // level because we have more information now.

            exp2 = visitor.typeCheck(exp, null);
            if (resultType != null) {
                RoleLocator role =
                        new RoleLocator(RoleLocator.FUNCTION_RESULT, functionName, 0);
                role.setErrorCode("XTTE0780");
                exp2 = TypeChecker.staticTypeCheck(exp2, resultType, false, role, visitor);
            }
        } catch (XPathException err) {
            err.maybeSetLocation(this);
            compileError(err);
        }
        if (exp2 != exp) {
            compiledFunction.setBody(exp2);
        }
    }

    public void optimize(Declaration declaration) throws XPathException {
        Expression exp = compiledFunction.getBody();
        ExpressionTool.resetPropertiesWithinSubtree(exp);
        ExpressionVisitor visitor = makeExpressionVisitor();
        Expression exp2 = exp;
        Optimizer opt = getConfiguration().obtainOptimizer();
        try {
            if (opt.getOptimizationLevel() != Optimizer.NO_OPTIMIZATION) {
                exp2 = exp.optimize(visitor, null);
            }

        } catch (XPathException err) {
            err.maybeSetLocation(this);
            compileError(err);
        }

        // Try to extract new global variables from the body of the function
        if (opt.getOptimizationLevel() != Optimizer.NO_OPTIMIZATION) {
            Expression exp3 = opt.promoteExpressionsToGlobal(exp2, visitor);
            if (exp3 != null) {
                exp2 = visitor.optimize(exp3, null);
            }
        }

        // Add trace wrapper code if required
        exp2 = makeTraceInstruction(this, exp2);

        allocateSlots(exp2);
        if (exp2 != exp) {
            compiledFunction.setBody(exp2);
        }

        int tailCalls = ExpressionTool.markTailFunctionCalls(exp2, getObjectName(), getNumberOfArguments());
        if (tailCalls != 0) {
            compiledFunction.setTailRecursive(tailCalls > 0, tailCalls > 1);
            compiledFunction.setBody(new TailCallLoop(compiledFunction));
        }

        // Generate byte code if appropriate

        if (getConfiguration().isGenerateByteCode(Configuration.XSLT)) {
            try {
                Expression cbody = opt.compileToByteCode(compiledFunction.getBody(), nameAtt,
                        Expression.PROCESS_METHOD | Expression.ITERATE_METHOD);
                if (cbody != null) {
                    compiledFunction.setBody(cbody);
                }
            } catch (Exception e) {
                System.err.println("Failed while compiling function " + nameAtt);
                e.printStackTrace();
                throw new XPathException(e);
            }
        }

        compiledFunction.computeEvaluationMode();

        if (isExplaining()) {
            exp2.explain(getConfiguration().getStandardErrorOutput());
        }
    }

    /**
    * Fixup all function references.
     * @param compiledFunction the Instruction representing this function in the compiled code
     * @throws XPathException if an error occurs.
    */

    private void fixupInstruction(UserFunction compiledFunction)
    throws XPathException {
        ExpressionVisitor visitor = makeExpressionVisitor();
        try {
            for (UserFunctionReference reference : references) {
                reference.setFunction(compiledFunction);
                if (reference instanceof UserFunctionCall) {
                    UserFunctionCall call = (UserFunctionCall)reference;
                    call.checkFunctionCall(compiledFunction, visitor);
                    call.computeArgumentEvaluationModes();
                }
            }
        } catch (XPathException err) {
            compileError(err);
        }
    }

    /**
     * Get associated Procedure (for details of stack frame).
     * @return the associated Procedure object
     */

    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    /**
     * Get the type of value returned by this function
     * @return the declared result type, or the inferred result type
     * if this is more precise
     */
    public SequenceType getResultType() {
        if (resultType == null) {
            // may be handling a forwards reference - see hof-038
            String asAtt = getAttributeValue("", "as");
            if (asAtt != null) {
                try {
                    resultType = makeSequenceType(asAtt);
                } catch (XPathException err) {
                    // the error will be reported when we get round to processing the function declaration
                }
            }
        }
        return resultType == null ? SequenceType.ANY_SEQUENCE : resultType;
    }

    /**
     * Get the number of arguments declared by this function (that is, its arity).
     * @return the arity of the function
     */

    public int getNumberOfArguments() {
        if (numberOfArguments == -1) {
            numberOfArguments = 0;
            AxisIterator kids = iterateAxis(AxisInfo.CHILD);
            while (true) {
                Item child = kids.next();
                if (child instanceof XSLLocalParam) {
                    numberOfArguments++;
                } else {
                    return numberOfArguments;
                }
            }
        }
        return numberOfArguments;
    }

    /**
     * Set the definitions of the parameters in the compiled function, as an array.
     * @param fn the compiled object representing the user-written function
     */

    public void setParameterDefinitions(UserFunction fn) {
        UserFunctionParameter[] params = new UserFunctionParameter[getNumberOfArguments()];
        fn.setParameterDefinitions(params);
        int count = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while (true) {
            NodeInfo node = kids.next();
            if (node == null) {
                return;
            }
            if (node instanceof XSLLocalParam) {
                UserFunctionParameter param = new UserFunctionParameter();
                params[count++] = param;
                param.setRequiredType(((XSLLocalParam)node).getRequiredType());
                param.setVariableQName(((XSLLocalParam)node).getVariableQName());
                param.setSlotNumber(((XSLLocalParam)node).getSlotNumber());
                ((XSLLocalParam)node).getSourceBinding().fixupBinding(param);
                int refs = ExpressionTool.getReferenceCount(fn.getBody(), param, false);
                param.setReferenceCount(refs);
            }
        }
    }

    /**
     * Get the argument types
     * @return the declared types of the arguments
     */

    public SequenceType[] getArgumentTypes() {
        SequenceType[] types = new SequenceType[getNumberOfArguments()];
        int count = 0;
        AxisIterator kids = iterateAxis(AxisInfo.CHILD);
        while (true) {
            NodeInfo node = kids.next();
            if (node == null) {
                return types;
            }
            if (node instanceof XSLLocalParam) {
                types[count++] = (((XSLLocalParam)node).getRequiredType());
            }
        }
    }

    /**
     * Get the compiled function
     * @return the object representing the compiled user-written function
     */

    public UserFunction getCompiledFunction() {
        return compiledFunction;
    }

    /**
     * Get the type of construct. This will be a constant in
     * class {@link net.sf.saxon.trace.Location}. This method is part of the
     * {@link net.sf.saxon.trace.InstructionInfo} interface
     */

    public int getConstructType() {
        return StandardNames.XSL_FUNCTION;
    }

}

