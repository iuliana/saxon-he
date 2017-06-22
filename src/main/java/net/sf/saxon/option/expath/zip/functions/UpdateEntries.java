////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.expath.zip.functions;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.option.expath.zip.library.Element;
import net.sf.saxon.option.expath.zip.library.SaxonElement;
import net.sf.saxon.option.expath.zip.library.ZipFacade;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

/**
 ** The implementation of the update-entries, a function of the EXPath zip module
 *   Modifies a copy of an existing ZIP file with the characteristics described by
 *   the elements within the $zip element. The $output argument is the URI where the
 *   modified ZIP file is copied to
 * */
public class UpdateEntries extends ExtensionFunctionDefinition {
    private final static StructuredQName name =
            new StructuredQName("", NamespaceConstant.EXPATH_ZIP, "update-entries");

    @Override
    public StructuredQName getFunctionQName() {
        return name;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_NODE, SequenceType.SINGLE_ANY_URI};
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.EMPTY_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            private String baseURI;
            @Override
            public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {

                NodeInfo struct = (NodeInfo)arguments[0].head();
                StringValue dest = (StringValue)arguments[1].head();
                assert struct != null;
                assert dest != null;
                ZipFacade zip = new ZipFacade(baseURI);
                Element elem = new SaxonElement(struct);
                zip.doUpdateEntries(elem, dest.getStringValue());
                return EmptySequence.getInstance();
            }

            @Override
            public void supplyStaticContext(StaticContext context, int locationId, Expression[] arguments) throws XPathException {
                if (baseURI == null) {
                    baseURI = context.getBaseURI();
                }
            }
        };
    }
}

