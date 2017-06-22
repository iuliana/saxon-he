////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;

/**
 * Implement the fn:doc-available() function
 */

public class DocAvailable extends SystemFunctionCall implements Callable {

    /*@Nullable*/ private String expressionBaseURI = null;

    /**
     * Bind aspects of the static context on which the particular function depends
     *
     * @param env the static context of the function call
     * @throws net.sf.saxon.trans.XPathException
     *          if execution with this static context will inevitably fail
     */
    @Override
    public void bindStaticContext(StaticContext env) throws XPathException {
        expressionBaseURI = env.getBaseURI();
    }

    /**
     * Get the static base URI of the expression
     */

    public String getStaticBaseURI() {
        return expressionBaseURI;
    }

    /**
    * preEvaluate: this method suppresses compile-time evaluation by doing nothing
     * @param visitor an expression visitor
     */

    public Expression preEvaluate(ExpressionVisitor visitor) {
        return this;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     */
    @Override
    public Expression copy() {
        DocAvailable fn = (DocAvailable)super.copy();
        fn.expressionBaseURI = expressionBaseURI;
        return fn;
    }

    /**
     * Determine whether two expressions are equivalent
     */
    @Override
    public boolean equals(Object o) {
        return super.equals(o) &&
                equalOrNull(expressionBaseURI, ((DocAvailable)o).expressionBaseURI);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (expressionBaseURI != null ? expressionBaseURI.hashCode() : 0);
        return result;
    }

    /**
     * Evaluate the expression
     * @param context
     * @return the result of evaluating the expression (a BooleanValue)
     * @throws net.sf.saxon.trans.XPathException
     */

    public BooleanValue evaluateItem(XPathContext context) throws XPathException {
        AtomicValue hrefVal = (AtomicValue)argument[0].evaluateItem(context);
        return BooleanValue.get(isDocAvailable(hrefVal, context));
    }

    private boolean isDocAvailable(AtomicValue hrefVal, XPathContext context) throws XPathException {
        if (hrefVal==null) {
            return false;
        }
        String href = hrefVal.getStringValue();
        return docAvailable(href, context);

//        // suppress all error messages while attempting to fetch the document
//        Controller controller = context.getController();
//        ErrorListener old = controller.getErrorListener();
//        controller.setErrorListener(ErrorDiscarder.THE_INSTANCE);
//        boolean b = docAvailable(href, context);
//        controller.setErrorListener(old);
//        return b;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        return BooleanValue.get(isDocAvailable((AtomicValue)arguments[0].head(), context));
    }

//    public static class ErrorDiscarder implements ErrorListener {
//        public static ErrorDiscarder THE_INSTANCE = new ErrorDiscarder();
//        public void warning(TransformerException exception) {}
//        public void error(TransformerException exception) {}
//        public void fatalError(TransformerException exception) {}
//
//    }

    public boolean docAvailable(String href, XPathContext context) throws XPathException {
        try {
            DocumentURI documentKey = DocumentFn.computeDocumentKey(href, expressionBaseURI, context);
            DocumentPool pool = context.getController().getDocumentPool();
            if (pool.isMarkedUnavailable(documentKey)) {
                return false;
            }
            DocumentInfo doc = pool.find(documentKey);
            if (doc != null) {
                return true;
            }
            Item item = DocumentFn.makeDoc(href, expressionBaseURI, context, this);
            if (item != null) {
                return true;
            } else {
                // The document does not exist; ensure that this remains the case
                pool.markUnavailable(documentKey);
                return false;
            }
        } catch (XPathException e) {
            return false;
        }
    }

//#ifdefined BYTECODE
     /**
     * Return the compiler of the DocAvaiable expression
     *
     * @return the relevant ExpressionCompiler
     */
//    @Override
//    public ExpressionCompiler getExpressionCompiler() {
//        return new DocAvailableCompiler();
//    }
//#endif


}

