////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.expr.flwor.Clause;
import net.sf.saxon.om.StandardNames;

/**
 * A Simple trace listener for XQuery that writes messages (by default) to System.err
 */

public class XQueryTraceListener extends AbstractTraceListener {

    /**
     * Generate attributes to be included in the opening trace element
     */

    /*@NotNull*/ protected String getOpeningAttributes() {
        return "";
    }

    /**
     * Get the trace element tagname to be used for a particular construct. Return null for
     * trace events that are ignored by this trace listener.
     */

    /*@Nullable*/ protected String tag(int construct) {
        switch (construct) {
            case StandardNames.XSL_FUNCTION:
                return "function";
            case StandardNames.XSL_VARIABLE:
                return "variable";
            case StandardNames.XSL_ELEMENT:
                return "element";
            case StandardNames.XSL_ATTRIBUTE:
                return "attribute";
            case StandardNames.XSL_COMMENT:
                return "comment";
            case StandardNames.XSL_DOCUMENT:
                return "document";
            case StandardNames.XSL_PROCESSING_INSTRUCTION:
                return "processing-instruction";
            case StandardNames.XSL_TEXT:
                return "text";
            case StandardNames.XSL_NAMESPACE:
                return "namespace";
            case Location.LITERAL_RESULT_ELEMENT:
                return "element";
            case Location.LITERAL_RESULT_ATTRIBUTE:
                return "attribute";
            case Location.FUNCTION_CALL:
                //return "function-call";
                return null;
            case Location.FOR_EXPRESSION:
                return "for";
            case Location.LET_EXPRESSION:
                return "let";
            case Location.WHERE_CLAUSE:
                return "where";
            case Location.ORDER_BY_CLAUSE:
                return "sort";
            case Location.RETURN_EXPRESSION:
                return "return";
            case Location.COPY_MODIFY_EXPRESSION:
                return "modify";
            case Location.INSERT_EXPRESSION:
                return "insert";
            case Location.DELETE_EXPRESSION:
                return "delete";
            case Location.REPLACE_EXPRESSION:
                return "replace";
            case Location.RENAME_EXPRESSION:
                return "rename";
            case Location.TYPESWITCH_EXPRESSION:
                return "typeswitch";
            case Location.VALIDATE_EXPRESSION:
                return "validate";
            case Location.IF_EXPRESSION:
                return "if";
            case Location.THEN_EXPRESSION:
                return "then";
            case Location.ELSE_EXPRESSION:
                return "else";
            case Location.CASE_EXPRESSION:
                return "case";
            case Location.SWITCH_EXPRESSION:
                return "switch";
            case Location.DEFAULT_EXPRESSION:
                return "default";
            case Location.TRACE_CALL:
                return "user-trace";
            case Location.CLAUSE_BASE + Clause.COUNT:
                return "count";
            case Location.CLAUSE_BASE + Clause.FOR:
                return "for";
            case Location.CLAUSE_BASE + Clause.LET:
                return "let";
            case Location.CLAUSE_BASE + Clause.GROUPBYCLAUSE:
                return "group-by";
            case Location.CLAUSE_BASE + Clause.ORDERBYCLAUSE:
                return "order-by";
            case Location.CLAUSE_BASE + Clause.WHERE:
                return "where";
            case Location.CLAUSE_BASE + Clause.WINDOW:
                return "window";
            default:
                //return "Other";
                return null;
            }
    }

}

