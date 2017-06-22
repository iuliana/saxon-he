////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.expr.parser.RoleLocator;
import net.sf.saxon.expr.parser.TypeChecker;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

import java.util.List;

/**
 * A "let" clause in a FLWOR expression
 */
public class LetClause extends Clause {

    private LocalVariableBinding rangeVariable;
    private Expression sequence;

    @Override
    public int getClauseKey() {
        return LET;
    }

    public LetClause copy() {
        LetClause let2 = new LetClause();
        let2.setLocationId(getLocationId());
        let2.rangeVariable = rangeVariable.copy();
        let2.sequence = sequence.copy();
        return let2;
    }

    public void setSequence(Expression sequence) {
        this.sequence = sequence;
    }

    public Expression getSequence() {
        return sequence;
    }


    public void setRangeVariable(LocalVariableBinding binding) {
        this.rangeVariable = binding;
    }

    public LocalVariableBinding getRangeVariable() {
        return rangeVariable;
    }

    /**
     * Get the number of variables bound by this clause
     *
     * @return the number of variable bindings
     */
    @Override
    public LocalVariableBinding[] getRangeVariables() {
        return new LocalVariableBinding[]{rangeVariable};
    }

    /**
     * Get a tuple stream that implements the functionality of this clause, taking its
     * input from another tuple stream which this clause modifies
     *
     * @param base    the input tuple stream
     * @param context
     * @return the output tuple stream
     */
    @Override
    public TuplePull getPullStream(TuplePull base, XPathContext context) {
        return new LetClausePull(base, this);
    }

    /**
     * Get a push-mode tuple stream that implements the functionality of this clause, supplying its
     * output to another tuple stream
     *
     * @param destination the output tuple stream
     * @param context
     * @return the push tuple stream that implements the functionality of this clause of the FLWOR
     *         expression
     */
    @Override
    public TuplePush getPushStream(TuplePush destination, XPathContext context) {
        return new LetClausePush(destination, this);
    }

    /**
     * Process the subexpressions of this clause
     *
     * @param processor the expression processor used to process the subexpressions
     */
    @Override
    public void processSubExpressions(ExpressionProcessor processor) throws XPathException {
        sequence = processor.processExpression(sequence);
    }

    /**
    * Type-check the expression
    */
	@Override
	public void typeCheck(ExpressionVisitor visitor) throws XPathException {
		RoleLocator role = new RoleLocator(RoleLocator.VARIABLE, rangeVariable.getVariableQName(), 0);
		sequence = TypeChecker.strictTypeCheck(sequence, rangeVariable.getRequiredType(), role,
			visitor.getStaticContext());
	}

    @Override
	public void gatherVariableReferences(final ExpressionVisitor visitor, Binding binding,
		List<VariableReference> references) {
		ExpressionTool.gatherVariableReferences(sequence, binding, references);
	}

    @Override
	public void refineVariableType(ExpressionVisitor visitor, List<VariableReference> references,
		Expression returnExpr) {
		final TypeHierarchy th = visitor.getConfiguration().getTypeHierarchy();
		final ItemType actualItemType = sequence.getItemType(th);
		for (VariableReference ref : references) {
			ref.refineVariableType(actualItemType, sequence.getCardinality(),
				(sequence instanceof Literal ? ((Literal) sequence).getValue() : null),
				sequence.getSpecialProperties(), visitor);
			visitor.resetStaticProperties();
		}
	}

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     *
     * @param out the expression presenter used to display the structure
     */
    @Override
    public void explain(ExpressionPresenter out) {
        out.startElement("let");
        out.emitAttribute("var", getRangeVariable().getVariableQName().getDisplayName());
        out.emitAttribute("slot", Integer.toString(getRangeVariable().getLocalSlotNumber()));
        sequence.explain(out);
        out.endElement();
    }

    public String toString() {
        FastStringBuffer fsb = new FastStringBuffer(FastStringBuffer.SMALL);
        fsb.append("let $");
        fsb.append(rangeVariable.getVariableQName().getDisplayName());
        fsb.append(" := ");
        fsb.append(sequence.toString());
        return fsb.toString();
    }
}
