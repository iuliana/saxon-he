////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.query;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.LocationProvider;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.SequenceType;

import java.util.*;

/**
 * A user-defined function in an XQuery module
 */

public class XQueryFunction implements InstructionInfo, Container, Declaration {
    private StructuredQName functionName;
    private List<UserFunctionParameter> arguments;
    private SequenceType resultType;
    /*@Nullable*/ private Expression body = null;
    /*@NotNull*/ private List<UserFunctionReference> references = new ArrayList<UserFunctionReference>(10);
    private int lineNumber;
    private int columnNumber;
    private String systemId;
    private Executable executable;
    /*@Nullable*/ private UserFunction compiledFunction = null;
    private boolean memoFunction;
    private NamespaceResolver namespaceResolver;
    private QueryModule staticContext;
    private boolean isUpdating = false;
    private Map<StructuredQName, Annotation> annotationMap = new HashMap<StructuredQName, Annotation>();

    /**
     * Create an XQuery function
     */

    public XQueryFunction() {
        arguments = new ArrayList<UserFunctionParameter>(8);
    }

    /**
     * Get the granularity of the container.
     * @return 0 for a temporary container created during parsing; 1 for a container
     *         that operates at the level of an XPath expression; 2 for a container at the level
     *         of a global function or template
     */

    public int getContainerGranularity() {
        return 2;
    }

    /**
     * Set the name of the function
     * @param name the name of the function as a StructuredQName object
     */

    public void setFunctionName(StructuredQName name) {
        functionName = name;
    }

    /**
     * Add an argument to the list of arguments
     * @param argument the formal declaration of the argument to be added
     */

    public void addArgument(UserFunctionParameter argument) {
        arguments.add(argument);
    }

    /**
     * Set the required result type of the function
     * @param resultType the declared result type of the function
     */

    public void setResultType(SequenceType resultType) {
        this.resultType = resultType;
    }

    /**
     * Set the body of the function
     * @param body the expression forming the body of the function
     */

    public void setBody(/*@Nullable*/ Expression body) {
        this.body = body;
        if (body != null) {
            body.setContainer(this);
        }
    }

    /**
     * Get the body of the function
     * @return the expression making up the body of the function
     */

    /*@Nullable*/ public Expression getBody() {
        return body;
    }

    /**
     * Set the system ID of the module containing the function
     * @param systemId the system ID (= base URI) of the module containing the function
     */

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Set the line number of the function declaration within its module
     * @param line the line number of the function declaration
     */

    public void setLineNumber(int line) {
        lineNumber = line;
    }

    /**
     * Set the column number of the function declaration
     * @param column the column number of the function declaration
     */

    public void setColumnNumber(int column) {
        columnNumber = column;
    }

    /**
     * Get the name of the function as a structured QName
     * @return the name of the function as a structured QName
     */

    public StructuredQName getFunctionName() {
        return functionName;
    }

    /**
     * Get the name of the function for display in error messages
     * @return the name of the function as a lexical QName
     */

    public String getDisplayName() {
        return functionName.getDisplayName();
    }

    /**
     * Get an identifying key for this function, which incorporates the URI and local part of the
     * function name plus the arity
     * @return an identifying key
     */

    /*@NotNull*/ public String getIdentificationKey() {
        return functionName.getClarkName() + '/' + arguments.size();
    }

    /**
     * Construct what the identification key would be for a function with given URI, local name, and arity
     * @param uri the URI part of the function name
     * @param localName the local part of the function name
     * @param arity the number of arguments in the function
     * @return an identifying key
     */

    public static String getIdentificationKey(/*@NotNull*/ String uri, /*@NotNull*/ String localName, int arity) {
        FastStringBuffer sb = new FastStringBuffer(uri.length() + localName.length() + 8);
        sb.append('{');
        sb.append(uri);
        sb.append('}');
        sb.append(localName);
        sb.append('/');
        sb.append(Integer.toString(arity));
        return sb.toString();
    }

    /**
     * Construct what the identification key would be for a function with given URI, local name, and arity
     * @param qName the name of the function
     * @param arity the number of arguments
     * @return an identifying key
     */

    public static String getIdentificationKey(/*@NotNull*/ StructuredQName qName, int arity) {
        String uri = qName.getURI();
        String localName = qName.getLocalPart();
        FastStringBuffer sb = new FastStringBuffer(uri.length() + localName.length() + 8);
        sb.append('{');
        sb.append(uri);
        sb.append('}');
        sb.append(localName);
        sb.append("/"+arity);
        //sb.append(arity+"");
        return sb.toString();
    }

    /**
     * Get the result type of the function
     * @return the declared result type
     */

    public SequenceType getResultType() {
        return resultType;
    }

    /**
     * Set the executable in which this function is contained
     * @param exec the executable
     */

    public void setExecutable(Executable exec) {
        executable = exec;
    }

    /**
     * Get the executable in which this function is contained
     * @return the executable
     */

    public Executable getExecutable() {
        return executable;
    }

    /**
     * Get the LocationProvider allowing location identifiers to be resolved.
     * @return the location provider
     */

    public LocationProvider getLocationProvider() {
        return executable.getLocationMap();
    }

    /**
     * Set the static context for this function
     * @param env the static context for the module in which the function is declared
     */

    public void setStaticContext(QueryModule env) {
        staticContext = env;
    }

    /**
     * Get the static context for this function
     * @return the static context for the module in which the function is declared
     */

    public StaticContext getStaticContext() {
        return staticContext;
    }

    /**
     * Get the declared types of the arguments of this function
     * @return an array, holding the types of the arguments in order
     */

    /*@NotNull*/ public SequenceType[] getArgumentTypes() {
        SequenceType[] types = new SequenceType[arguments.size()];
        for (int i=0; i<arguments.size(); i++) {
            types[i] = arguments.get(i).getRequiredType();
        }
        return types;
    }

    /**
     * Get the definitions of the arguments to this function
     * @return an array of UserFunctionParameter objects, one for each argument
     */

    public UserFunctionParameter[] getParameterDefinitions() {
        UserFunctionParameter[] params = new UserFunctionParameter[arguments.size()];
        return arguments.toArray(params);
    }

    /**
     * Get the arity of the function
     * @return the arity (the number of arguments)
     */

    public int getNumberOfArguments() {
        return arguments.size();
    }

    /**
     * Register a call on this function
     * @param ufc a user function call that references this function.
     */

    public void registerReference(UserFunctionReference ufc) {
        references.add(ufc);
    }

    /**
     * Set that this is, or is not, a memo function. A memo function remembers the results of calls
     * on the function so that the a subsequent call with the same arguments simply look up the result
     * @param isMemoFunction true if this is a memo function.
     */

    public void setMemoFunction(boolean isMemoFunction) {
        memoFunction = isMemoFunction;
    }

    /**
     * Find out whether this is a memo function
     * @return true if this is a memo function
     */

    public boolean isMemoFunction() {
        return memoFunction;
    }

    /**
     * Set whether this is an updating function (as defined in XQuery Update)
     * @param isUpdating true if this is an updating function
     */

    public void setUpdating(boolean isUpdating) {
        this.isUpdating = isUpdating;
    }

    /**
     * Ask whether this is an updating function (as defined in XQuery Update)
     * @return true if this is an updating function
     */

    public boolean isUpdating() {
        return isUpdating;
    }

   /**
    * Set the annotations on this function
    * @param annotations the annotations, indexed by annotation name
    */

    public void setAnnotations(Map<StructuredQName, Annotation> annotations) {
        this.annotationMap = annotations;
        if (annotations.get(Annotation.UPDATING) != null) {
            setUpdating(true);
        }
    }

    /**
     * Ask whether this is a private function (as defined in XQuery 3.0)
     * @return true if this is a private function
     */

    public boolean isPrivate() {
        return annotationMap.get(Annotation.PRIVATE) != null;
    }

    /**
     * Compile this function to create a run-time definition that can be interpreted (note, this
     * has nothing to do with Java code generation)
     * @throws XPathException if errors are found
     */

    public void compile() throws XPathException {
        Configuration config = staticContext.getConfiguration();
        try {
            // If a query function is imported into several modules, then the compile()
            // method will be called once for each importing module. If the compiled
            // function already exists, then this is a repeat call, and the only thing
            // needed is to fix up references to the function from within the importing
            // module.

            if (compiledFunction == null) {
                SlotManager map = config.makeSlotManager();
                UserFunctionParameter[] params = getParameterDefinitions();
                for (int i=0; i<params.length; i++) {
                    params[i].setSlotNumber(i);
                    map.allocateSlotNumber(params[i].getVariableQName());
                }

                // type-check the body of the function

                ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, getExecutable());
                body = visitor.simplify(body);
                body = visitor.typeCheck(body, null);

                // Try to extract new global variables from the body of the function
                //body = config.getOptimizer().promoteExpressionsToGlobal(body, visitor);
                
                body.setContainer(this);
                RoleLocator role =
                        new RoleLocator(RoleLocator.FUNCTION_RESULT, functionName, 0);
                //role.setSourceLocator(this);
                body = TypeChecker.staticTypeCheck(body, resultType, false, role, visitor);
                if (config.isCompileWithTracing()) {
                    namespaceResolver = staticContext.getNamespaceResolver();
                    TraceExpression trace = new TraceExpression(body);
                    trace.setConstructType(StandardNames.XSL_FUNCTION);
                    trace.setObjectName(functionName);
                    trace.setNamespaceResolver(namespaceResolver);
                    trace.setLocationId(staticContext.getLocationMap().allocateLocationId(systemId, lineNumber));
                    body = trace;
                }

                compiledFunction = config.newUserFunction(memoFunction);
                compiledFunction.setBody(body);
                compiledFunction.setHostLanguage(Configuration.XQUERY);
                compiledFunction.setFunctionName(functionName);
                compiledFunction.setParameterDefinitions(params);
                compiledFunction.setResultType(getResultType());
                compiledFunction.setLineNumber(lineNumber);
                compiledFunction.setSystemId(systemId);
                compiledFunction.setExecutable(executable);
                compiledFunction.setStackFrameMap(map);
                compiledFunction.setUpdating(isUpdating);
                compiledFunction.setAnnotationMap(annotationMap);

                for (int i=0; i<params.length; i++) {
                    UserFunctionParameter param = params[i];
                    int refs = ExpressionTool.getReferenceCount(body, param, false);
                    param.setReferenceCount(refs);
                }
            }
            // bind all references to this function to the UserFunction object

            fixupReferences();

            // register this function with the function library available at run-time (e.g. for saxon:evaluate())

            FunctionLibraryList libList = executable.getFunctionLibrary();
            if (libList.getLibraryList().size() == 1 && libList.getLibraryList().get(0) instanceof ExecutableFunctionLibrary) {
                ExecutableFunctionLibrary lib  = (ExecutableFunctionLibrary)libList.getLibraryList().get(0);
                lib.addFunction(compiledFunction);
            } else {
//                throw new AssertionError("executable.getFunctionLibrary() is an instance of " +
//                        executable.getFunctionLibrary().getClass().getName());
            }

        } catch (XPathException e) {
            e.maybeSetLocation(this);
            throw e;
        }
    }

    /**
     * Optimize the body of this function
     * @throws net.sf.saxon.trans.XPathException if execution fails, for example because the function is updating
     * and contains constructs not allowed in an updating function, or vice-versa.
     */

    public void optimize() throws XPathException {
        body.checkForUpdatingSubexpressions();
        if (isUpdating) {
            if (!ExpressionTool.isAllowedInUpdatingContext(body)) {
                XPathException err = new XPathException(
                         "The body of an updating function must be an updating expression", "XUST0002");
                err.setLocator(body);
                throw err;
            }
        } else {
            //body.checkForUpdatingSubexpressions();
            if (body.isUpdatingExpression()) {
                XPathException err = new XPathException(
                         "The body of a non-updating function must be a non-updating expression", "XUST0001");
                err.setLocator(body);
                throw err;
            }
        }
        ExpressionVisitor visitor = ExpressionVisitor.make(staticContext, getExecutable());
        Configuration config = staticContext.getConfiguration();
        Optimizer opt = config.obtainOptimizer();
        int arity = arguments.size();
        if (opt.getOptimizationLevel() != Optimizer.NO_OPTIMIZATION) {
            body = visitor.optimize(body, null);

            // Try to extract new global variables from the body of the function
            Expression b2 = opt.promoteExpressionsToGlobal(body, visitor);
            if (b2 != null) {
                body = visitor.optimize(b2, null);
            }

            // mark tail calls within the function body

            if (!isUpdating) {
                // TODO: implement tail call optimization for updating functions. Requires TailCallLoop to
                // be an updating expression (like Block)
                int tailCalls = ExpressionTool.markTailFunctionCalls(body, functionName, arity);
                if (tailCalls != 0) {
                    compiledFunction.setBody(body);
                    compiledFunction.setTailRecursive(tailCalls > 0, tailCalls > 1);
                    body = new TailCallLoop(compiledFunction);
                }
            }
            body.setContainer(this);
            compiledFunction.setBody(body);
        }
        compiledFunction.computeEvaluationMode();
        ExpressionTool.allocateSlots(body, arity, compiledFunction.getStackFrameMap());
        if (config.isGenerateByteCode(Configuration.XQUERY)) {
            Expression cbody = opt.compileToByteCode(body, getFunctionName().getDisplayName(),
                    Expression.PROCESS_METHOD | Expression.ITERATE_METHOD);
            if (cbody != null) {
                body = cbody;
            }
            compiledFunction.setBody(body);
            compiledFunction.computeEvaluationMode();
        }
    }

    /**
     * Fix up references to this function
     */

    public void fixupReferences() throws XPathException {
        for (UserFunctionReference ufc : references) {
            ufc.setFunction(compiledFunction);
            if (ufc instanceof UserFunctionCall) {
                ((UserFunctionCall)ufc).computeArgumentEvaluationModes();
            }
        }
    }

    /**
     * Type-check references to this function
     * @param visitor the expression visitor
     */

    public void checkReferences(ExpressionVisitor visitor) throws XPathException {
        for (UserFunctionReference ufr : references) {
            if (ufr instanceof UserFunctionCall) {
                UserFunctionCall ufc = (UserFunctionCall)ufr;
                ufc.checkFunctionCall(compiledFunction, visitor);
                ufc.computeArgumentEvaluationModes();
            }
        }

        // clear the list of references, so that more can be added in another module
        references = new ArrayList<UserFunctionReference>(0);

    }

    /**
     * Produce diagnostic output showing the compiled and optimized expression tree for a function
     * @param out the destination to be used
     */
    public void explain(/*@NotNull*/ ExpressionPresenter out) {
        out.startElement("declareFunction");
        out.emitAttribute("name", functionName.getDisplayName());
        out.emitAttribute("arity", Integer.toString(getNumberOfArguments()));
        if (compiledFunction == null) {
            out.emitAttribute("unreferenced", "true");
        } else {
            if (compiledFunction.isMemoFunction()) {
                out.emitAttribute("memo", "true");
            }
            out.emitAttribute("tailRecursive", (compiledFunction.isTailRecursive() ? "true" : "false"));
            body.explain(out);
        }
        out.endElement();
    }

    /**
     * Get the callable compiled function contained within this XQueryFunction definition.
     * @return the compiled function object
     */

    /*@Nullable*/ public UserFunction getUserFunction() {
        return compiledFunction;
    }

    /**
     * Get the type of construct. This will be a constant in
     * class {@link Location}.
     */

    public int getConstructType() {
        return StandardNames.XSL_FUNCTION;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     */

    public StructuredQName getObjectName() {
        return functionName;
    }

    /**
     * Get the system identifier (URI) of the source module containing
     * the instruction. This will generally be an absolute URI. If the system
     * identifier is not known, the method may return null. In some cases, for example
     * where XML external entities are used, the correct system identifier is not
     * always retained.
     */

    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the line number of the instruction in the source stylesheet module.
     * If this is not known, or if the instruction is an artificial one that does
     * not relate to anything in the source code, the value returned may be -1.
     */

    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Return the public identifier for the current document event.
     * @return A string containing the public identifier, or
     *         null if none is available.
     * @see #getSystemId
     */
    /*@Nullable*/ public String getPublicId() {
        return null;
    }

    /**
     * Return the column number
     * @return The column number, or -1 if none is available.
     * @see #getLineNumber
     */

    public int getColumnNumber() {
        return -1;
    }

    public String getSystemId(long locationId) {
        return getSystemId();
    }

    public int getLineNumber(long locationId) {
        return getLineNumber();
    }

    public int getColumnNumber(long locationId) {
        return getColumnNumber();
    }

    /**
     * Get the namespace context of the instruction. This will not always be available, in which
     * case the method returns null.
     */

    public NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    /**
     * Get the value of a particular property of the instruction. Properties
     * of XSLT instructions are generally known by the name of the stylesheet attribute
     * that defines them.
     * @param name The name of the required property
     * @return  The value of the requested property, or null if the property is not available
     */

    /*@Nullable*/ public Object getProperty(String name) {
        if ("name".equals(name)) {
            return functionName.getDisplayName();
        } else if ("as".equals(name)) {
            return resultType.toString();
        } else {
            return null;
        }
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property.
     */

    /*@NotNull*/ public Iterator getProperties() {
        return new PairIterator("name", "as");
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     * @return typically {@link net.sf.saxon.Configuration#XSLT} or {@link net.sf.saxon.Configuration#XQUERY}
     */

    public int getHostLanguage() {
        return Configuration.XQUERY;
    }

}

