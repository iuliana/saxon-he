////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.XQueryFunction;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.trace.InstructionInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.SingletonClosure;

import java.util.*;

import org.apache.log4j.Logger;

/**
* A compiled global variable in a stylesheet or query. <br>
*/
public class GlobalVariable extends GeneralVariable implements Container, net.sf.saxon.query.Declaration, InstructionInfo {

    /**
     * Note that variableReferences on this list might be dormant, that is if they might be disconnected from the live
     * expression tree
     */
	private static final Logger LOGGER = Logger.getLogger(GlobalVariable.class);
    protected List<BindingReference> references = new ArrayList<>(10);
    private Executable executable;
    private SlotManager stackFrameMap = null;
    private boolean indexed;
    private int lineNumber;
    private String systemId;
    private boolean isPrivate = false;
    private boolean isAssignable = false;
    private GlobalVariable originalVariable;

    /**
     * Create a global variable
     */
    public GlobalVariable(){}

    /**
     * Get the executable containing this global variable
     * @return the containing executable
     */
    public Executable getExecutable() {
        return executable;
    }

    /**
     * Set the containing executable
     * @param executable the executable that contains this global variable
     */
    public void setExecutable(Executable executable) {
        this.executable = executable;
    }

    /**
     * Say that this (XQuery) variable is a copy of some originally declared variable. It's a
     * separate variable when imported into another module, but it retains the link
     * to the original.
     * @param var the variable in the imported module from which this variable is derived
     */
    public void setOriginalVariable(GlobalVariable var) {
        originalVariable = var;
    }

    /**
     * Get the original declaration of this variable
     * @return the variable in the imported module from which this variable is derived
     */
    public GlobalVariable getOriginalVariable() {
        return originalVariable;
    }

    /**
     * Get the original declaration of this variable, or its original declaration, transitively
     * @return the real variable declaration in some transitively imported module from which this variable is
     * ultimately derived
     */
    public GlobalVariable getUltimateOriginalVariable() {
    	return originalVariable == null ? this : originalVariable.getUltimateOriginalVariable();
    }

    /**
     * Set the line number where the variable declaration appears in the source
     * @param lineNumber the line number
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * Get the line number where the declaration appears
     */
    public int getLineNumber() {
        return lineNumber;
    }

    public int getLineNumber(long locationId) {
        return lineNumber;
    }

    /**
     * Set the system ID of the module where the variable declaration appears
     * @param systemId the System ID (base URI)
     */
    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    /**
     * Get the system ID of the module containing the variable declaration
     * @return the System ID (base URI)
     */
    public String getSystemId() {
        return systemId;
    }

    /**
     * Get the column number within the document, entity, or module containing a particular location
     *
     * @param locationId identifier of the location in question (as passed down the Receiver pipeline)
     * @return the column number within the document, entity, or module, or -1 if this is not available
     */
    public int getColumnNumber(long locationId) {
        return executable.getLocationMap().getLineNumber(locationId);
    }

    /**
     * Ask whether this global variable is private
     * @return true if this variable is private
     */
    public boolean isPrivate() {
        return isPrivate;
    }

    /**
     * Say whether this global variable is a "parameter" (an external variable, in XQuery terminology)
     * @param b true if this variable is external
     */
    public void setPrivate(boolean b) {
        isPrivate = b;
    }

    /**
     * Indicate whether this variable is assignable using saxon:assign
     * @param assignable true if this variable is assignable
     */
    public void setAssignable(boolean assignable) {
        isAssignable = assignable;
    }

    /**
    * Test whether it is permitted to assign to the variable using the saxon:assign
    * extension element. This will only be true if the extra attribute saxon:assignable="yes"
    * is present.
    */
    @Override
    public final boolean isAssignable() {
        return isAssignable;
    }

    /**
     * Get the type of construct. This will either be the fingerprint of a standard XSLT instruction name
     * (values in {@link net.sf.saxon.om.StandardNames}: all less than 1024)
     * or it will be a constant in class {@link net.sf.saxon.trace.Location}.
     *
     * @return an integer identifying the kind of construct
     */
    public int getConstructType() {
        return StandardNames.XSL_VARIABLE;
    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     *
     * @return the QName of the object declared or manipulated by this instruction or expression
     */
    public StructuredQName getObjectName() {
        return getVariableQName();
    }

    /**
     * Get the value of a particular property of the instruction. Properties
     * of XSLT instructions are generally known by the name of the stylesheet attribute
     * that defines them.
     *
     * @param name The name of the required property
     * @return The value of the requested property, or null if the property is not available
     */
    public Object getProperty(String name) {
        return null;
    }

    /**
     * Get an iterator over all the properties available. The values returned by the iterator
     * will be of type String, and each string can be supplied as input to the getProperty()
     * method to retrieve the value of the property. The iterator may return properties whose
     * value is null.
     *
     * @return an iterator over the properties.
     */
    public Iterator<String> getProperties() {
        List<String> list = Collections.emptyList();
        return list.iterator();
    }

    /**
     * Get the container in which this expression is located. This will usually be a top-level construct
     * such as a function or global variable, and XSLT template, or an XQueryExpression. In the case of
     * free-standing XPath expressions it will be the StaticContext object
     *
     * @return the expression's container
     */
    @Override
    public Container getContainer() {
        return this;
    }

    /**
     * Get the host language (XSLT, XQuery, XPath) used to implement the code in this container
     * @return typically {@link net.sf.saxon.Configuration#XSLT} or {@link net.sf.saxon.Configuration#XQUERY}
     */
    public int getHostLanguage() {
        return executable.getHostLanguage();
    }

    /**
     * Mark this as an indexed variable, to allow fast searching
     */
    public void setIndexedVariable() {
        indexed = true;
    }

    /**
     * Ask whether this is an indexed variable
     * @return true if this variable is indexed
     */
    public boolean isIndexedVariable() {
        return indexed;
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
     * The expression that initializes a global variable may itself use local variables.
     * In this case a stack frame needs to be allocated while evaluating the global variable
     * @param map The stack frame map for local variables used while evaluating this global
     * variable.
     */
    public void setContainsLocals(SlotManager map) {
        this.stackFrameMap = map;
    }

    /**
     * Is this a global variable?
     * @return true (yes, it is a global variable)
     */
    @Override
    public boolean isGlobal() {
        return true;
    }

    /**
     * Register a variable reference that refers to this global variable
     * @param ref the variable reference
     */
    public void registerReference(BindingReference ref) {
        references.add(ref);
    }

    /**
     * Iterate over the references to this variable
     * @return an iterator over the references: returns objects of class {@link VariableReference}
     */
    public Iterator iterateReferences() {
        return references.iterator();
    }

    /**
     * Create a compiled representation of this global variable
     * @param exec the executable
     * @param slot the slot number allocated to this variable
     * @throws XPathException if compile-time errors are found.
     */
    public void compile(Executable exec, int slot) throws XPathException {
        if (references.isEmpty()) {
            return;
        }
        TypeHierarchy th = exec.getConfiguration().getTypeHierarchy();

        setSlotNumber(slot);
        if (this instanceof GlobalParam) {
            setRequiredParam(select == null);
        }
        final SequenceType type = getRequiredType();
		for (BindingReference ref : references) {
			ref.fixup(this);
			GroundedValue constantValue = null;
			int properties = 0;
			Expression select = getSelectExpression();
			if (select instanceof Literal && !(this instanceof GlobalParam)) {
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
			properties |= StaticProperty.NON_CREATIVE;
			// a variable reference is non-creative even if its initializer is creative
			ref.setStaticType(type, constantValue, properties);
		}
		exec.registerGlobalVariable(this);
		setReferenceCount(10); // TODO: temporary!
	}

    /**
     * Type check the compiled representation of this global variable
     * @param visitor an expression visitor
     * @throws XPathException if compile-time errors are found.
     */
    public void typeCheck(ExpressionVisitor visitor) throws XPathException {
        Expression value = getSelectExpression();
        if (value != null) {
            value.checkForUpdatingSubexpressions();
            if (value.isUpdatingExpression()) {
				throw new XPathException("Initializing expression for global variable must not be an updating " +
					"expression", "XUST0001");
            }
            value.setContainer(this);
            RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, getVariableQName(), 0);
            ExpressionVisitor.ContextItemType cit = new ExpressionVisitor.ContextItemType(AnyItemType.getInstance(), true);
            Expression value2 = TypeChecker.strictTypeCheck(
                    visitor.typeCheck(visitor.simplify(value), cit),
                    getRequiredType(), role, visitor.getStaticContext());
            value2 = value2.optimize(visitor, cit);
            setSelectExpression(value2);
            value2.setContainer(this);
            // the value expression may declare local variables
            SlotManager map = visitor.getConfiguration().makeSlotManager();
            int slots = ExpressionTool.allocateSlots(value2, 0, map);
            if (slots > 0) {
                setContainsLocals(map);
            }

            if (getRequiredType() == SequenceType.ANY_SEQUENCE && !(this instanceof GlobalParam)) {
                // no type was declared; try to deduce a type from the value
                try {
                    final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
                    final ItemType itemType = value.getItemType(th);
                    final int cardinality = value.getCardinality();
                    setRequiredType(SequenceType.makeSequenceType(itemType, cardinality));
                    GroundedValue constantValue = null;
                    if (value2 instanceof Literal) {
                        constantValue = ((Literal)value2).getValue();
                    }
                    for (BindingReference reference : references) {
                        BindingReference ref = (reference);
                        if (ref instanceof VariableReference) {
                            ((VariableReference) ref).refineVariableType(
                                    itemType, cardinality, constantValue, value.getSpecialProperties(), visitor);
                        }
                    }
                } catch (Exception err) {
                    LOGGER.error("Could not type check the compiled representation of the global variable", err);
                }
            }
        }
    }

    /**
     * Check for cycles in this variable definition
     * @param referees the calls leading up to this one; it's an error if this variable is on the
     * stack, because that means it calls itself directly or indirectly. The stack may contain
     * variable definitions (GlobalVariable objects) and user-defined functions (UserFunction objects).
     * It will never contain the same object more than once.
     * @param globalFunctionLibrary the library containing all global functions
     * @throws net.sf.saxon.trans.XPathException if cycles are found
     */
	public void lookForCycles(Stack<Container> referees, XQueryFunctionLibrary globalFunctionLibrary)
		throws XPathException {
        if (referees.contains(this)) {
            int s = referees.indexOf(this);
            referees.push(this);
            StringBuilder message = new StringBuilder("Circular definition of global variable: ");
            if (getVariableQName().getLocalPart().equals("context-item")) {
                message.append("Context item");
            } else {
                message.append('$').append(getVariableQName().getDisplayName());
            }

            for (int i = s; i < referees.size() - 1; i++) {
                if (i != s) {
                    message.append(", which");
                }
                if (referees.get(i + 1) instanceof GlobalVariable) {
                    GlobalVariable next = (GlobalVariable)referees.get(i + 1);
                    if (next.getVariableQName().getLocalPart().equals("context-item")) {
                        message.append(" uses context item");
                    } else {
                        message.append(" uses $").append(next.getVariableQName().getDisplayName());
                    }
                } else if (referees.get(i + 1) instanceof XQueryFunction) {
                    XQueryFunction next = (XQueryFunction)referees.get(i + 1);
					message.append(" calls ").append(next.getFunctionName().getDisplayName()).append('#')
						.append(next.getNumberOfArguments()).append("()");
                }
            }
            message.append('.');
            XPathException err = new XPathException(message.toString());
            String errorCode;
            if (getHostLanguage() == Configuration.XSLT) {
                errorCode = "XTDE0640";
            } else if (executable.isAllowXPath30()) {
                errorCode = "XQDY0054";
            } else {
                errorCode = "XQST0054";
            }
            err.setErrorCode(errorCode);
            err.setIsStaticError(true);
            err.setLocator(this);
            throw err;
        }
        if (select != null) {
            referees.push(this);
            List<Binding> list = new ArrayList<>(10);
            ExpressionTool.gatherReferencedVariables(select, list);
            for (Binding b : list) {
                if (b instanceof GlobalVariable) {
                    ((GlobalVariable) b).lookForCycles(referees, globalFunctionLibrary);
                }
            }
            List<String> flist = new ArrayList<>();
            ExpressionTool.gatherCalledFunctionNames(select, flist);
            for (String s : flist) {
                XQueryFunction f = globalFunctionLibrary.getDeclarationByKey(s);
                if (!referees.contains(f)) {
                    // recursive function calls are allowed
                    lookForFunctionCycles(f, referees, globalFunctionLibrary);
                }
            }
            referees.pop();
        }
    }

    /**
     * Look for cyclic variable references that go via one or more function calls
     * @param f a used-defined function
     * @param referees a list of variables and functions that refer directly or indirectly to this variable
     * @param globalFunctionLibrary the library containing all global functions
     */
	private static void lookForFunctionCycles(XQueryFunction f, Stack<Container> referees,
		XQueryFunctionLibrary globalFunctionLibrary) throws XPathException {
        Expression body = f.getBody();
        referees.push(f);
        List<Binding> list = new ArrayList<>(10);
        ExpressionTool.gatherReferencedVariables(body, list);
        for (Binding b : list) {
            if (b instanceof GlobalVariable) {
                ((GlobalVariable) b).lookForCycles(referees, globalFunctionLibrary);
            }
        }
        List<String> flist = new ArrayList<>();
        ExpressionTool.gatherCalledFunctionNames(body, flist);
        for (String s : flist) {
            XQueryFunction qf = globalFunctionLibrary.getDeclarationByKey(s);
            if (!referees.contains(qf)) {
                // recursive function calls are allowed
                lookForFunctionCycles(qf, referees, globalFunctionLibrary);
            }
        }
        referees.pop();
    }

    /**
     * Evaluate the variable. That is,
     * get the value of the select expression if present or the content
     * of the element otherwise, either as a tree or as a sequence
    */
	@Override
    public Sequence getSelectValue(XPathContext context) throws XPathException {
		if (select == null) {
			throw new AssertionError("*** No select expression for global variable $" +
				getVariableQName().getDisplayName() + "!!");
        } else {
            try {
                XPathContextMajor c2 = context.newCleanContext();
                c2.setOrigin(this);
                final Controller controller = c2.getController();
                assert controller != null;
                UnfailingIterator initialNode =
                        SingletonIterator.makeIterator(controller.getContextForGlobalVariables());
                initialNode.next();
                c2.setCurrentIterator(initialNode);
                if (stackFrameMap != null) {
                    c2.openStackFrame(stackFrameMap);
                }
                return ExpressionTool.evaluate(select, evaluationMode, c2, referenceCount);
            } catch (XPathException e) {
                if (!getVariableQName().getURI().equals(NamespaceConstant.SAXON_GENERATED_GLOBAL)) {
                    e.setIsGlobalError(true);
                }
                throw e;
            }
        }
    }

    /**
    * Evaluate the variable
    */
    public Sequence evaluateVariable(XPathContext context) throws XPathException {
        final Controller controller = context.getController();
        assert controller != null;
        final Bindery b = controller.getBindery();
        final Sequence v = b.getGlobalVariable(getSlotNumber());
        return v != null ? v : actuallyEvaluate(context);
    }

    /**
     * Evaluate the global variable, and save its value for use in subsequent references.
     * @param context the XPath dynamic context
     * @return the value of the variable
     * @throws XPathException if evaluation fails
     */
    protected Sequence actuallyEvaluate(XPathContext context) throws XPathException {
        final Controller controller = context.getController();
        assert controller != null;
        final Bindery b = controller.getBindery();
        try {
            // This is the first reference to a global variable; try to evaluate it now.
            // But first check for circular dependencies.
            setDependencies(b, this, context);

            // Set a flag to indicate that the variable is being evaluated. This is designed to prevent
            // (where possible) the same global variable being evaluated several times in different threads
            boolean go = b.setExecuting(this);
            if (!go) {
                // some other thread has evaluated the variable while we were waiting
                return b.getGlobalVariable(getSlotNumber());
            }

            Sequence value = getSelectValue(context);
            if (indexed) {
                value = controller.getConfiguration().obtainOptimizer().makeIndexedValue(value.iterate());
            }
            return b.saveGlobalVariableValue(this, value);

        } catch (XPathException err) {
            b.setNotExecuting(this);
            if (err instanceof XPathException.Circularity) {
                String errorCode;
                if (getHostLanguage() == Configuration.XSLT) {
                    errorCode = "XTDE0640";
                } else if (executable.isAllowXPath30()) {
                    errorCode = "XQDY0054";
                } else {
                    errorCode = "XQST0054";
                }
                err.setErrorCode(errorCode);
                err.setXPathContext(context);
                // Detect it more quickly the next time (in a pattern, the error is recoverable)
                SingletonClosure closure = new SingletonClosure(new ErrorExpression(err), context);
                b.setGlobalVariable(this, closure);
                err.setLocator(this);
                throw err;
            } else {
                throw err;
            }
        }
    }

    /**
     * Get the variable that is immediately dependent on this one, and register the dependency, so
     * that circularities can be detected across threads. This relies on the fact that when the initialiser
     * for variable X contains a reference to variable Y, then when Y is evaluated, a stack frame will be found
     * on the context stack representing the evaluation of X. We don't set a dependency from X to Y if the value
     * of Y was already available in the Bindery; it's not needed, because in this case we know that evaluation
     * of Y is unproblematic, and can't lead to any circularities.
     * @param bindery the Bindery
     * @param var the global variable or parameter being evaluated
     * @param context the dynamic evaluation context
     */
	protected static void setDependencies(Bindery bindery, GlobalVariable var, XPathContext context)
		throws XPathException {
        if (!(context instanceof XPathContextMajor)) {
            context = getMajorCaller(context);
        }
        while (context != null) {
            do {
                InstructionInfo instructionInfo = ((XPathContextMajor) context).getOrigin();
                if (instructionInfo instanceof GlobalVariable) {
                    bindery.registerDependency((GlobalVariable)instructionInfo, var);
                    return;
                }
                context = getMajorCaller(context);
            } while (context != null);
        }
    }

    private static XPathContextMajor getMajorCaller(XPathContext context) {
        XPathContext caller = context.getCaller();
        while (!(caller == null || caller instanceof XPathContextMajor)) {
            caller = caller.getCaller();
		}
		return (XPathContextMajor) caller;
    }

}
