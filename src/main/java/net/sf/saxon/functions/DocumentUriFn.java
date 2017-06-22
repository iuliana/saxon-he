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
import net.sf.saxon.expr.parser.ExpressionVisitor;
import net.sf.saxon.om.DocumentPool;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AnyURIValue;
import net.sf.saxon.value.EmptySequence;

/**
 * This class supports the document-uri() function
 */

public class DocumentUriFn extends SystemFunctionCall implements Callable {

    /**
     * Simplify and validate.
     *
     * @param visitor an expression visitor
     */

    /*@NotNull*/
    public Expression simplify(ExpressionVisitor visitor) throws XPathException {
        useContextItemAsDefault(visitor);
        return simplifyArguments(visitor);
    }

    /**
     * Evaluate the function in a string context
     */

    /*@Nullable*/
    public AnyURIValue evaluateItem(XPathContext c) throws XPathException {
        NodeInfo node = (NodeInfo) argument[0].evaluateItem(c);
        if (node == null) {
            return null;
        }
        return getDocumentURI(node, c);
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
        NodeInfo node = getDefaultArgumentNode(context, arguments, "fn:document-uri()");;

        if (node == null) {
            return EmptySequence.getInstance();
        }

        AnyURIValue result = getDocumentURI(node, context);
        return (result == null ? EmptySequence.getInstance() : result);
    }

    public static AnyURIValue getDocumentURI(NodeInfo node, XPathContext c) {
        if (node.getNodeKind() == Type.DOCUMENT) {
            final Controller controller = c.getController();
            assert controller != null;
            DocumentPool pool = controller.getDocumentPool();
            String docURI = pool.getDocumentURI(node);
            if (docURI == null) {
                docURI = node.getSystemId();
            }
            if (docURI == null) {
                return null;
            } else if ("".equals(docURI)) {
                return null;
            } else {
                return new AnyURIValue(docURI);
            }
        } else {
            return null;
        }
    }

}

