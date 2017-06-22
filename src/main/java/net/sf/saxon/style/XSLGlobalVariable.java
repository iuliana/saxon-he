////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.Executable;
import net.sf.saxon.expr.instruct.GeneralVariable;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.AnyNodeTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceType;

/**
* Handler for xsl:variable elements appearing as a child of xsl:stylesheet. <br>
* The xsl:variable element has mandatory attribute name and optional attribute select
*/

public class XSLGlobalVariable extends XSLGeneralVariable implements StylesheetProcedure {

    SlotManager slotManager; // used to manage local variables declared inside this global variable

    public XSLGlobalVariable() {
        sourceBinding.setProperty(SourceBinding.GLOBAL, true);
    }

    protected int getPermittedAttributes() {
        return
            SourceBinding.ASSIGNABLE |
            SourceBinding.SELECT |
            SourceBinding.AS |
            SourceBinding.STATIC;
    }

    private int state = 0;
            // 0 = before prepareAttributes()
            // 1 = during prepareAttributes()
            // 2 = after prepareAttributes()

    protected boolean redundant = false;

    /**
     * Ask whether this element contains a binding for a variable with a given name; and if it does,
     * return the source binding information
     * @param name the variable name
     * @return the binding information if this element binds a variable of this name; otherwise null
     */

    public SourceBinding getBindingInformation(StructuredQName name) {
        if (name.equals(sourceBinding.getVariableQName())) {
            return sourceBinding;
        } else {
            return null;
        }
    }

    public void prepareAttributes() throws XPathException {
        if (state==2) return;
        if (state==1) {
            compileError("Circular reference to variable", "XTDE0640");
        }
        state = 1;
        //System.err.println("Prepare attributes of $" + getVariableName());

        sourceBinding.prepareAttributes(getPermittedAttributes());
        state = 2;
    }

    @Override
    protected void index(Declaration decl, PrincipalStylesheetModule top) throws XPathException {
        top.indexVariableDeclaration(decl);
    }

    @Override
    public void validate(Declaration decl) throws XPathException {
        slotManager = getConfiguration().makeSlotManager();
        super.validate(decl);
    }

    /**
     * Ask whether the global variable is declared with assignable="yes"
     * @return true if assignabl="yes" was specified
     */

    public boolean isAssignable() {
        return sourceBinding.hasProperty(SourceBinding.ASSIGNABLE);
    }

    /**
    * Determine whether this node is a declaration.
    * @return true - a global variable is a declaration
    */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
    * Determine whether this node is an instruction.
    * @return false - a global variable is not an instruction
    */

    public boolean isInstruction() {
        return false;
    }

    /**
    * Get the static type of the variable. This is the declared type, unless the value
    * is statically known and constant, in which case it is the actual type of the value.
    */

    public SequenceType getRequiredType() {
        return sourceBinding.getInferredType(true);
    }

    @Override
    public void fixupReferences() throws XPathException {
        sourceBinding.fixupReferences();
        super.fixupReferences();
    }

    /**
     * Compile.
     * This method ensures space is available for local variables declared within
     * this global variable
     */

    public void compileDeclaration(Executable exec, Declaration decl) throws XPathException {

        if (sourceBinding.getReferences().isEmpty() && !isAssignable()) {
            redundant = true;
        }

        if (!redundant) {
            sourceBinding.handleSequenceConstructor(exec, decl);
            GlobalVariable inst = new GlobalVariable();
            inst.setExecutable(exec);
            Expression select = sourceBinding.getSelectExpression();
            inst.setSelectExpression(select);
            if (select != null) {
                select.setContainer(inst);
            }
            StructuredQName variableQName = sourceBinding.getVariableQName();
            inst.setVariableQName(variableQName);
            initializeBinding(exec, decl, inst);
            inst.setAssignable(isAssignable());
            int slot = exec.getGlobalVariableMap().allocateSlotNumber(variableQName);
            inst.setSlotNumber(slot);
            inst.setRequiredType(getRequiredType());
            sourceBinding.fixupBinding(inst);
            inst.setContainer(inst);
            compiledVariable = inst;
        }

    }

    /**
     * Initialize - common code called from the compile() method of all subclasses
     * @param exec the executable
     * @param decl this xsl:variable declaration
     * @param var the representation of the variable declaration in the compiled executable
     * @throws net.sf.saxon.trans.XPathException if an error is detected
     */

    protected void initializeBinding(Executable exec, Declaration decl, GeneralVariable var)
    throws XPathException {

        Expression select = var.getSelectExpression();
        final GlobalVariable gvar = (GlobalVariable)var;
        var.setContainer(gvar);
        Expression exp2 = select;
        if (exp2 != null) {
            try {
                ExpressionVisitor visitor = makeExpressionVisitor();
                exp2.setContainer(gvar);
                exp2 = visitor.typeCheck(visitor.simplify(select), new ExpressionVisitor.ContextItemType(Type.ITEM_TYPE, true));
            } catch (XPathException err) {
                compileError(err);
            }

            // Add trace wrapper code if required
            exp2 = makeTraceInstruction(this, exp2);

            allocateSlots(exp2);
        }
        if (slotManager != null && slotManager.getNumberOfVariables() > 0) {
            gvar.setContainsLocals(slotManager);
        }
        exec.registerGlobalVariable(gvar);
        setReferenceCount(gvar);

        if (exp2 != select) {
            gvar.setSelectExpression(exp2);
        }

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
     * Optimize the stylesheet construct
     * @param declaration
     */

    public void optimize(Declaration declaration) throws XPathException {
        if (!redundant && compiledVariable.getSelectExpression()!=null) {
            Expression exp2 = compiledVariable.getSelectExpression();
            ExpressionVisitor visitor = makeExpressionVisitor();
            Optimizer opt = getConfiguration().obtainOptimizer();
            try {
                if (opt.getOptimizationLevel() != Optimizer.NO_OPTIMIZATION) {
                    ExpressionTool.resetPropertiesWithinSubtree(exp2);
                    exp2 = exp2.optimize(visitor, new ExpressionVisitor.ContextItemType(AnyNodeTest.getInstance(), true));
                }

            } catch (XPathException err) {
                err.maybeSetLocation(this);
                compileError(err);
            }

            // Try to extract new global variables from the body of the variable declaration
            // (but don't extract the whole body!)
//            if (opt.getOptimizationLevel() != Optimizer.NO_OPTIMIZATION) {
//                exp2 = opt.promoteExpressionsToGlobal(exp2, visitor, true);
//            }
            // dropped because it doesn't seem to do much good - just splits up an expression
            // into lots of small global variables.

            allocateSlots(exp2);
            if (slotManager != null && slotManager.getNumberOfVariables() > 0) {
                ((GlobalVariable)compiledVariable).setContainsLocals(slotManager);
            }

            if (exp2 != compiledVariable.getSelectExpression()) {
                compiledVariable.setSelectExpression(exp2);
            }
        }
    }


    /**
     * Mark this global variable as redundant, typically because it is overridden by another global
     * variable of the same name, or because there are no references to it
     * @param redundant true if this variable is redundant, otherwise false
     */

    public void setRedundant(boolean redundant) {
        this.redundant = redundant;
    }



}

