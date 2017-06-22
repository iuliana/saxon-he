////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.om.StructuredQName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * A SlotManager supports functions, templates, etc: specifically, any executable code that
 * requires a stack frame containing local variables. In XSLT a SlotManager underpins any
 * top-level element that can contain local variable declarations,
 * specifically, a top-level xsl:template, xsl:variable, xsl:param, or xsl:function element
 * or an xsl:attribute-set element or xsl:key element. In XQuery it underpins functions and
 * global variables. The purpose of the SlotManager is to allocate slot numbers to variables
 * in the stack, and to record how many slots are needed. A Debugger may define a subclass
 * with additional functionality.
*/

public class SlotManager implements Serializable {

    /**
     * An empty SlotManager
     */

    public static final SlotManager EMPTY = new SlotManager(0);

    private ArrayList<StructuredQName> variableMap = new ArrayList<StructuredQName>(10);
            // values are StructuredQName objects representing the variable names
    private int numberOfVariables = 0;

    /**
     * The constructor should not be called directly. A new SlotManager should be obtained using
     * the factory method {@link net.sf.saxon.Configuration#makeSlotManager()}.
     */

    public SlotManager(){}

    /**
     * Create a SlotManager with a given number of slots
     * @param n the number of slots
     */

    public SlotManager(int n) {
        numberOfVariables = n;
        variableMap = new ArrayList<StructuredQName>(n);
    }

    /**
    * Get number of variables (size of stack frame)
     * @return the number of slots for variables
    */

    public int getNumberOfVariables() {
        return numberOfVariables;
    }

    /**
     * Set the number of variables
     * @param numberOfVariables the space to be allocated for variables
     */

    public void setNumberOfVariables(int numberOfVariables) {
        this.numberOfVariables = numberOfVariables;
        variableMap.trimToSize();
    }

    /**
    * Allocate a slot number for a variable
     * @param qName the name of the variable
     * @return the allocated slot number (the next one available)
     */

    public int allocateSlotNumber(StructuredQName qName) {
        variableMap.add(qName);
        return numberOfVariables++;
    }

    /**
     * Get the variable map (simply a list of variable names as structured QNames). Note that it
     * is possible for several variables to have the same name.
     * <p><b>Changed in Saxon 9.0 to return a list of StructuredQName values rather than integers</b></p>
     * @return the list of variable names for this stack frame
     */

    /*@NotNull*/ public List<StructuredQName> getVariableMap() {
        return variableMap;
    }

}

