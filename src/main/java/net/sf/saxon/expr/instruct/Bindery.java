////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.apache.log4j.Logger;

/**
* The Bindery class holds information about variables and their values. From Saxon 8.1, it is
* used only for global variables: local variables are now held in the XPathContext object.
*
* Variables are identified by a Binding object. Values will always be of class Value.
*/

public final class Bindery  {

	private static final Logger LOGGER = Logger.getLogger(Bindery.class);
    private Sequence[] globals;          // values of global variables and parameters
    private long[] busy;                            // set to current thread id while variable is being evaluated
    private GlobalParameterSet globalParameters;    // supplied global parameters
    private SlotManager globalVariableMap;          // contains the mapping of variable names to slot numbers
    private Map<GlobalVariable, Set<GlobalVariable>> dependencies = new HashMap<>();
    private boolean applyConversionRules = true;

    /**
     * Define how many slots are needed for global variables
     * @param map the SlotManager that keeps track of slot allocation for global variables.
    */
	public void allocateGlobals(SlotManager map) {
		globalVariableMap = map;
		int n = map.getNumberOfVariables() + 1;
		globals = new Sequence[n];
		busy = new long[n];
		for (int i = 0; i < n; i++) {
			globals[i] = null;
			busy[i] = -1L;
		}
	}

    /**
     * Say whether the function conversion rules should be applied to supplied
     * parameter values. For example, this allows an integer to be supplied as the value
     * for a parameter where the expected type is xs:double. The default is true.
     * @param convert true if function conversion rules are to be applied to supplied
     * values; if false, the supplied value must match the required type exactly.
     * @since 9.3
     */
    public void setApplyFunctionConversionRulesToExternalVariables(boolean convert) {
        applyConversionRules = convert;
    }

    /**
     * Ask whether the function conversion rules should be applied to supplied
     * parameter values. For example, this allows an integer to be supplied as the value
     * for a parameter where the expected type is xs:double. The default is true.
     * @return true if function conversion rules are to be applied to supplied
     * values; if false, the supplied value must match the required type exactly.
     * @since 9.3
     */
    public boolean isApplyFunctionConversionRulesToExternalVariables() {
        return applyConversionRules;
    }

    /**
    * Define global parameters
    * @param params The ParameterSet passed in by the user, eg. from the command line
    */
    public void defineGlobalParameters(GlobalParameterSet params) {
        globalParameters = params;
        // Reset any existing global variables - allows the controller and bindery to be serially 
        // reused with new parameter values
        Arrays.fill(globals, null);
    }

    /**
     * Use global parameter. This is called when a global xsl:param element is processed.
     * If a parameter of the relevant name was supplied, it is bound to the xsl:param element.
     * Otherwise the method returns false, so the xsl:param default will be evaluated.
     * @param qName The name of the parameter
     * @param slot The slot number allocated to the parameter
     * @param requiredType The declared type of the parameter
     * @param context the XPath dynamic evaluation context
     * @return true if a parameter of this name was supplied, false if not
     * @throws XPathException if a dynamic error occurs, for example if the supplied parameter value
     * does not match the required type
     */
    public boolean useGlobalParameter(StructuredQName qName, int slot, SequenceType requiredType, XPathContext context)
            throws XPathException {
        if (globals != null && globals[slot] != null) {
            return true;
        }

		if (globalParameters == null) {
			return false;
		}

        Sequence val = globalParameters.convertParameterValue(qName, requiredType, applyConversionRules, context);
		if (val == null) {
			return false;
		}

        // Check that any nodes belong to the right configuration

        Configuration config = context.getConfiguration();
        SequenceIterator iter = val.iterate();
        while (true) {
            Item next = iter.next();
            if (next == null) {
                break;
            }
			if (next instanceof NodeInfo && !config.isCompatible(((NodeInfo) next).getConfiguration())) {
				throw new XPathException("A node supplied in a global parameter must be built using the same " +
					"Configuration that was used to compile the stylesheet or query", SaxonErrorCode.SXXP0004);
			}
        }

        // If the supplied value is a document node, and the document node has a systemID that is an absolute
        // URI, and the absolute URI does not already exist in the document pool, then register it in the document
        // pool, so that the document-uri() function will find it there, and so that a call on doc() will not
        // reload it.

        if (val instanceof DocumentInfo) {
            String systemId = ((DocumentInfo)val).getSystemId();
            try {
                if (systemId != null && new URI(systemId).isAbsolute()) {
                    Controller controller = context.getController();
                    assert controller != null;
                    DocumentPool pool = controller.getDocumentPool();
                    if (pool.find(systemId) == null) {
                        pool.add(((DocumentInfo)val), systemId);
                    }
                }
            } catch (URISyntaxException err) {
                LOGGER.error("Could not obtain URI", err);
            }
        }

        if (!(val instanceof GroundedValue)) {
            val = new SequenceExtent(val.iterate());
        }
		if (globals != null) {
			globals[slot] = val;
		}
        return true;
    }

    /**
    * Provide a value for a global variable
    * @param binding identifies the variable
    * @param value the value of the variable
    */
    public void setGlobalVariable(GlobalVariable binding, Sequence value) {
        globals[binding.getSlotNumber()] = value;
    }

    /**
     * Set/Unset a flag to indicate that a particular global variable is currently being
     * evaluated. Note that this code is not synchronized, so there is no absolute guarantee that
     * two threads will not both evaluate the same global variable; however, apart from wasted time,
     * it is harmless if they do.
     * @param binding the global variable in question
     * @return true if evaluation of the variable should proceed; false if it is found that the variable has now been
     * evaluated in another thread.
     * @throws net.sf.saxon.trans.XPathException If an attempt is made to set the flag when it is already set, this means
     * the definition of the variable is circular.
    */
    public boolean setExecuting(GlobalVariable binding) throws XPathException {
        long thisThread = Thread.currentThread().getId();
        int slot = binding.getSlotNumber();

        long busyThread = busy[slot];
        if (busyThread != -1L) {
            if (busyThread == thisThread) {
                // The global variable is being evaluated in this thread. This shouldn't happen, because
                // we have already tested for circularities. If it does happen, however, we fail cleanly.
                throw new XPathException.Circularity("Circular definition of variable "
                        + binding.getVariableQName().getDisplayName());
            } else {
                // The global variable is being evaluated in another thread. Give it a chance to finish.
                // It could be a circularity, or just an accident of timing. Note that in the latter case,
                // we will actually re-evaluate the variable; this normally does no harm, though there is a small
                // risk it could lead to problems with the identity of a node changing.
				for (int i = 0; i < 10; i++) {
                    try {
						Thread.sleep(20l * i);
                    } catch (InterruptedException e) {
                        LOGGER.error("Thread has been interrupted", e);
                    }
                    if (busy[slot] == -1L) {
                        // evaluation has finished in another thread
                        return false;
                    }
                }
                // We've waited long enough; there could be a deadlock if we wait any longer.
                // Continue with the evaluation; whichever thread completes the evaluation first will
                // save the value.
                return true;
            }
        }
        busy[slot] = thisThread;
        return true;
    }

    /**
     * Indicate that a global variable is not currently being evaluated
     * @param binding the global variable
     */
    public void setNotExecuting(GlobalVariable binding) {
        int slot = binding.getSlotNumber();
        busy[slot] = -1L;
    }


    /**
     * Save the value of a global variable, and mark evaluation as complete.
     * @param binding the global variable in question
     * @param value the value that this thread has obtained by evaluating the variable
     * @return the value actually given to the variable. Exceptionally this will be different from the supplied
     * value if another thread has evaluated the same global variable concurrently. The returned value should be
     * used in preference, to ensure that all threads agree on the value. They could be different if for example
     * the variable is initialized using the collection() function.
    */
    public synchronized Sequence saveGlobalVariableValue(GlobalVariable binding, Sequence value) {
        int slot = binding.getSlotNumber();
        if (globals[slot] != null) {
            // another thread has already evaluated the value
            return globals[slot];
        } else {
            busy[slot] = -1L;
            globals[slot] = value;
            return value;
        }
    }

    /**
    * Get the value of a global variable
    * @param binding the Binding that establishes the unique instance of the variable
    * @return the Value of the variable if defined, null otherwise.
    */
    public Sequence getGlobalVariableValue(GlobalVariable binding) {
        return globals[binding.getSlotNumber()];
    }

    /**
    * Get the value of a global variable whose slot number is known
    * @param slot the slot number of the required variable
    * @return the Value of the variable if defined, null otherwise.
    */
    public Sequence getGlobalVariable(int slot) {
        return globals[slot];
    }

    /**
     * Get the Global Variable Map, containing the mapping of variable names (fingerprints)
     * to slot numbers. This is provided for use by debuggers.
     * @return the SlotManager containing information about the assignment of slot numbers
     * to global variables and parameters
     */
    public SlotManager getGlobalVariableMap() {
        return globalVariableMap;
    }

    /**
     * Get all the global variables, as an array. This is provided for use by debuggers
     * that know the layout of the global variables within the array.
     * @return the array of global varaibles.
     */
    public Sequence[] getGlobalVariables() {
        return globals;
    }

    /**
     * Register the dependency of one variable ("one") upon another ("two"), throwing an exception if this would establish
     * a cycle of dependencies.
     * @param one the first (dependent) variable
     * @param two the second (dependee) variable
     * @throws XPathException if adding this dependency creates a cycle of dependencies among global variables.
     */
    public synchronized void registerDependency(GlobalVariable one, GlobalVariable two) throws XPathException {
        if (one == two) {
            throw new XPathException.Circularity("Circular dependency among global variables: "
                    + one.getVariableQName().getDisplayName() + " depends on its own value");
        }
        Set<GlobalVariable> transitiveDependencies = dependencies.get(two);
        if (transitiveDependencies != null) {
            if (transitiveDependencies.contains(one)) {
                throw new XPathException.Circularity("Circular dependency among variables: "
                        + one.getVariableQName().getDisplayName() + " depends on the value of "
                        + two.getVariableQName().getDisplayName() + ", which depends directly or indirectly on the " +
                        	"value of " + one.getVariableQName().getDisplayName());
            }
            for (GlobalVariable var : transitiveDependencies) {
                // register the transitive dependencies
                registerDependency(one, var);
            }
        }
        Set<GlobalVariable> existingDependencies = dependencies.get(one);
        if (existingDependencies == null) {
            existingDependencies = new HashSet<>();
            dependencies.put(one, existingDependencies);
        }
        existingDependencies.add(two);
    }
    
}
