////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.InstructionDetails;
import net.sf.saxon.expr.parser.ExpressionTool;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.*;
import net.sf.saxon.trace.Location;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.SequenceExtent;

import java.io.PrintStream;

/**
* This class supports the XPath 2.0 function trace().
 * The value is traced to the registered output stream (defaulting to System.err),
 * unless a TraceListener is in use, in which case the information is sent to the TraceListener
*/


public class Trace extends SystemFunctionCall implements Callable {

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
    * Get the static properties of this expression (other than its type). The result is
    * bit-significant. These properties are used for optimizations. In general, if
    * property bit is set, it is true, but if it is unset, the value is unknown.
     */

    public int computeSpecialProperties() {
        return argument[0].getSpecialProperties();
    }

    /**
    * Get the static cardinality
    */

    public int computeCardinality() {
        return argument[0].getCardinality();
    }

    /**
    * Evaluate the function
    */

    public Item evaluateItem(XPathContext context) throws XPathException {
        Item val = argument[0].evaluateItem(context);
        String label = argument[1].evaluateAsString(context).toString();
        Controller controller = context.getController();
        if (controller.isTracing()) {
            notifyListener(label, val, context);
        } else {
            PrintStream out = controller.getTraceFunctionDestination();
            if (out != null) {
                traceItem(val, label, out);
            }
        }
        return val;
    }

    private void notifyListener(String label, Sequence val, XPathContext context) {
        InstructionDetails info = new InstructionDetails();
        info.setConstructType(Location.TRACE_CALL);
        info.setLineNumber(getLineNumber());
        info.setSystemId(getSystemId());
        info.setProperty("label", label);
        info.setProperty("value", val);
        TraceListener listener = context.getController().getTraceListener();
        listener.enter(info, context);
        listener.leave(info);
    }

    public static void traceItem(/*@Nullable*/ Item val, String label, PrintStream out) {
        if (val==null) {
            out.println(label + ": empty sequence");
        } else {
            if (val instanceof NodeInfo) {
                out.println(label + ": " + Type.displayTypeName(val) + ": "
                                    + Navigator.getPath((NodeInfo)val));
            } else {
                out.println(label + ": " + Type.displayTypeName(val) + ": "
                                    + val.getStringValue());
            }
        }
    }

    /**
    * Iterate over the results of the function
    */

    /*@NotNull*/
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        Controller controller = context.getController();
        if (controller.isTracing()) {
            String label = argument[1].evaluateAsString(context).toString();
            Sequence value = ExpressionTool.eagerEvaluate(argument[0], context);
            notifyListener(label, value, context);
            return value.iterate();
        } else {
            PrintStream out = controller.getTraceFunctionDestination();
            if (out == null) {
                return argument[0].iterate(context);
            } else {
                return new TracingIterator(argument[0].iterate(context),
                        argument[1].evaluateAsString(context).toString(),
                        out);
            }
        }
    }

    /**
     * Evaluate the expression
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Controller controller = context.getController();
        if (controller.isTracing()) {
            String label = arguments[1].head().getStringValue();
            Sequence value = SequenceExtent.makeSequenceExtent(arguments[0].iterate());
            notifyListener(label, value, context);
            return value;
        } else {
            PrintStream out = controller.getTraceFunctionDestination();
            if (out == null) {
                return arguments[0];
            } else {
                return SequenceTool.toLazySequence(new TracingIterator(arguments[0].iterate(),
                        arguments[1].head().toString(),
                        out));
            }
        }
    }

    /**
    * Tracing Iterator class
    */

    private class TracingIterator implements SequenceIterator {

        SequenceIterator base;
        String label;
        PrintStream out;
        boolean empty = true;


        public TracingIterator(SequenceIterator base, String label, PrintStream out) {
            this.base = base;
            this.label = label;
            this.out = out;
        }

        public Item next() throws XPathException {
            Item n = base.next();
            if (n==null) {
                if (empty) {
                    traceItem(null, label, out);
                }
            } else {
                traceItem(n, label + " [" + position() + ']', out);
                empty = false;
            }
            return n;
        }

        public Item current() {
            return base.current();
        }

        public int position() {
            return base.position();
        }

        public void close() {
            base.close();
        }

        /*@NotNull*/
        public SequenceIterator getAnother() throws XPathException {
            return new TracingIterator(base.getAnother(), label, out);
        }

        /**
         * Get properties of this iterator, as a bit-significant integer.
         *
         * @return the properties of this iterator. This will be some combination of
         *         properties such as {@link #GROUNDED}, {@link #LAST_POSITION_FINDER},
         *         and {@link #LOOKAHEAD}. It is always
         *         acceptable to return the value zero, indicating that there are no known special properties.
         *         It is acceptable for the properties of the iterator to change depending on its state.
         */

        public int getProperties() {
            return 0;
        }
    }

}

