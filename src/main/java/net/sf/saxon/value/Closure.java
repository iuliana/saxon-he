////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.value;

import net.sf.saxon.event.SequenceReceiver;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.type.AnyItemType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.TypeHierarchy;

/**
 * A Closure represents a value that has not yet been evaluated: the value is represented
 * by an expression, together with saved values of all the context variables that the
 * expression depends on.
 *
 * <p>This Closure is designed for use when the value is only read once. If the value
 * is read more than once, a new iterator over the underlying expression is obtained
 * each time: this may (for example in the case of a filter expression) involve
 * significant re-calculation.</p>
 *
 * <p>The expression may depend on local variables and on the context item; these values
 * are held in the saved XPathContext object that is kept as part of the Closure, and they
 * will always be read from that object. The expression may also depend on global variables;
 * these are unchanging, so they can be read from the Bindery in the normal way. Expressions
 * that depend on other contextual information, for example the values of position(), last(),
 * current(), current-group(), should not be evaluated using this mechanism: they should
 * always be evaluated eagerly. This means that the Closure does not need to keep a copy
 * of these context variables.</p>
 *
*/

public class Closure<T extends Item> implements Sequence {

    protected Expression expression;
    /*@Nullable*/ protected XPathContextMajor savedXPathContext;
    protected int depth = 0;

    // The base iterator is used to copy items on demand from the underlying value
    // to the reservoir. It only ever has one instance (for each Closure) and each
    // item is read only once.

    /*@Nullable*/ protected SequenceIterator<T> inputIterator;

//    private static int countClosures = 0;
//    private static int countMemoClosures = 0;

    /**
     * Constructor should not be called directly, instances should be made using the make() method.
     */
    //private static int closureCount = 0;
    public Closure()  {
//        this.expression = exp;
//        this.savedXPathContext = context.newContext();
//        this.savedXPathContext.setOriginatingConstructType(Location.LAZY_EVALUATION);
//        saveContext(expression, context);
    }

    /**
     * Construct a Closure by supplying the expression and the set of context variables.
     * @param expression the expression to be lazily evaluated
     * @param context the dynamic context of the expression including for example the variables
     * on which it depends
     * @param ref the number of references to the value being lazily evaluated; this affects
     * the kind of Closure that is created
     * @return the Closure, a virtual value that can later be materialized when its content is required
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs
    */

    /*@NotNull*/ public static Sequence make(/*@NotNull*/ Expression expression, /*@NotNull*/ XPathContext context, int ref) throws XPathException {

        // special cases such as TailExpressions and shared append expressions are now picked up before
        // this method is called (where possible, at compile time)
//        SequenceIterator iter = expression.iterate(context);
//        return Value.asValue(SequenceExtent.makeSequenceExtent(iter));
        Sequence v = context.getConfiguration().makeClosure(expression, ref, context);
        if (v instanceof Closure) {
            Closure c = (Closure)v;
            c.expression = expression;
            c.savedXPathContext = context.newContext();
            c.savedXPathContext.setOriginatingConstructType(Location.LAZY_EVALUATION);
            c.saveContext(expression, context);
            return c;
        } else {
            return v;
        }
    }

    public void saveContext(/*@NotNull*/ Expression expression, /*@NotNull*/ XPathContext context) throws XPathException {
        // Make a copy of all local variables. If the value of any local variable is a closure
        // whose depth exceeds a certain threshold, we evaluate the closure eagerly to avoid
        // creating deeply nested lists of Closures, which consume memory unnecessarily

        // We only copy the local variables if the expression has dependencies on local variables.
        // What's more, we only copy those variables that the expression actually depends on.

        if ((expression.getDependencies() & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
            StackFrame localStackFrame = context.getStackFrame();
            Sequence[] local = localStackFrame.getStackFrameValues();
            int[] slotsUsed = expression.getSlotsUsed();  // computed on first call
            if (local != null) {
                final SlotManager stackFrameMap = localStackFrame.getStackFrameMap();
                final Sequence[] savedStackFrame =
                        new Sequence[stackFrameMap.getNumberOfVariables()];
                for (int i : slotsUsed) {
                    if (local[i] instanceof Closure) {
                        int cdepth = ((Closure) local[i]).depth;
                        if (cdepth >= 10) {
                            local[i] = SequenceExtent.makeSequenceExtent(((Closure) local[i]).iterate());
                        } else if (cdepth + 1 > depth) {
                            depth = cdepth + 1;
                        }
                    }
                    savedStackFrame[i] = local[i];
                }

                savedXPathContext.setStackFrame(stackFrameMap, savedStackFrame);
            }
        }

        // Make a copy of the context item
        SequenceIterator currentIterator = context.getCurrentIterator();
        if (currentIterator != null) {
            Item contextItem = currentIterator.current();
            UnfailingIterator single = SingletonIterator.makeIterator(contextItem);
            single.next();
            savedXPathContext.setCurrentIterator(single);
            // we don't save position() and last() because we have no way
            // of restoring them. So the caller must ensure that a Closure is not
            // created if the expression depends on position() or last()
        }

        savedXPathContext.setReceiver(null);
    }

    /**
     * Get the first item in the sequence.
     *
     * @return the first item in the sequence if there is one, or null if the sequence
     *         is empty
     * @throws net.sf.saxon.trans.XPathException
     *          in the situation where the sequence is evaluated lazily, and
     *          evaluation of the first item causes a dynamic error.
     */
    public Item head() throws XPathException {
        return iterate().next();
    }

    /**
     * Get the static item type
     * @param th the type hierarchy cache
     */

    /*@NotNull*/
    public ItemType getItemType(/*@Nullable*/ TypeHierarchy th) {
        if (expression == null || th == null) {
            return AnyItemType.getInstance();
        } else {
            return expression.getItemType(th);
        }
        // This is probably rarely used, because a Closure has no static existence, and
        // run-time polymorphism applies mainly to singletons, which are rarely evaluated as Closures.
    }

    /**
     * Get the cardinality
     */

    public int getCardinality() {
        if (expression == null) {
            return StaticProperty.ALLOWS_ZERO_OR_MORE;
        } else {
            return expression.getCardinality();
        }
    }

    public Expression getExpression() {
		return expression;
	}

	/*@Nullable*/ public XPathContextMajor getSavedXPathContext() {
		return savedXPathContext;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
	}

	public void setSavedXPathContext(XPathContextMajor savedXPathContext) {
		this.savedXPathContext = savedXPathContext;
	}

	/**
     * Evaluate the expression in a given context to return an iterator over a sequence
     */

    /*@NotNull*/
    public SequenceIterator<T> iterate() throws XPathException {

        if (inputIterator == null) {
            return (inputIterator = (SequenceIterator<T>)expression.iterate(savedXPathContext));
        } else {
            // In an ideal world this shouldn't happen: if the value is needed more than once, we should
            // have chosen a MemoClosure. In fact, this path is never taken when executing the standard
            // test suite (April 2005). However, it provides robustness in case the compile-time analysis
            // is flawed. I believe it's also possible that this path can be taken if a Closure needs to be
            // evaluated when the chain of dependencies gets too long: this was happening routinely when
            // all local variables were saved, rather than only those that the expression depends on.
            return inputIterator.getAnother();
        }
    }

    /**
     * Process the instruction, without returning any tail calls
     * @param context The dynamic context, giving access to the current node,
     *                the current variables, etc.
     */

    public void process(/*@NotNull*/ XPathContext context) throws XPathException {
        if (expression == null) {
            // This is a Closure that simply wraps a SequenceIterator supplied from the Java level
            SequenceReceiver out = context.getReceiver();
            while (true) {
                T item = inputIterator.next();
                if (item == null) {
                    break;
                }
                out.append(item, 0, NodeInfo.ALL_NAMESPACES);
            }
            inputIterator = inputIterator.getAnother();
        } else {
            // To evaluate the closure in push mode, we need to use the original context of the
            // expression for everything except the current output destination, which is newly created
            XPathContextMajor c2 = savedXPathContext.newContext();
            SequenceReceiver out = context.getReceiver();
            c2.setReceiver(out);
            c2.setTemporaryOutputState(true);
            expression.process(c2);
        }
    }

    /**
     * Reduce a value to its simplest form. If the value is a closure or some other form of deferred value
     * such as a FunctionCallPackage, then it is reduced to a SequenceExtent. If it is a SequenceExtent containing
     * a single item, then it is reduced to that item. One consequence that is exploited by class FilterExpression
     * is that if the value is a singleton numeric value, then the result will be an instance of NumericValue
     */

    public GroundedValue reduce() throws XPathException {
        return SequenceExtent.makeSequenceExtent(iterate());
    }

}

