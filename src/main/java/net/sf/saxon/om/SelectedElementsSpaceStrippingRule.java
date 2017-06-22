////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;


import net.sf.saxon.event.Stripper;
import net.sf.saxon.pattern.*;
import net.sf.saxon.style.StylesheetModule;
import net.sf.saxon.trans.Rule;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;

import java.util.HashMap;

/**
 * A whitespace stripping rule that strips elected elements unless xml:space indicates that whitespace
 * should be preserved.
 */

public class SelectedElementsSpaceStrippingRule implements SpaceStrippingRule {

    // This is a cut-down version of the Mode class, which until 9.3 was used for the job, even though
    // it is over-engineered for the task.

    private Rule anyElementRule = null;
    private Rule unnamedElementRuleChain = null;
    private HashMap<NodeName, Rule> namedElementRules = new HashMap<NodeName, Rule>(32);
    private int sequence = 0;
    private boolean rejectDuplicates; // in XSLT 3.0, duplicate conflicting rules are a static error

    /**
    * Create the ruleset
    */

    public SelectedElementsSpaceStrippingRule(boolean rejectDuplicates) {
        this.rejectDuplicates = rejectDuplicates;
    }

    /**
     * Decide whether an element is in the set of white-space preserving element names
     *
     *
     * @param fingerprint Identifies the name of the element whose whitespace is to
     * be preserved
     * @return ALWAYS_PRESERVE if the element is in the set of white-space preserving
     *  element types, ALWAYS_STRIP if the element is to be stripped regardless of the
     * xml:space setting, and STRIP_DEFAULT otherwise
    */

    public byte isSpacePreserving(NodeName fingerprint) throws XPathException {

        Rule rule = getRule(fingerprint);

        if (rule==null) {
            return Stripper.ALWAYS_PRESERVE;
        }

        return (rule.getAction() == Stripper.PRESERVE ? Stripper.ALWAYS_PRESERVE : Stripper.STRIP_DEFAULT);

    }

    /**
     * Add a rule
     *
     * @param test          a NodeTest (*, *:local, prefix:*, or QName)
     * @param action           StripRuleTarget.STRIP or StripRuleTarget.PRESERVE
     * @param module the stylesheet module containing the rule
     * @param lineNumber the line where the strip-space or preserve-space instruction appears
     * @throws XPathException if this rule is a conflicting duplicate
     */

    public void addRule(NodeTest test, Stripper.StripRuleTarget action,
                        StylesheetModule module, int lineNumber) throws XPathException {

        // for fast lookup, we maintain one list for each element name for patterns that can only
        // match elements of a given name, one list for each node type for patterns that can only
        // match one kind of non-element node, and one generic list.
        // Each list is sorted in precedence/priority order so we find the highest-priority rule first

        int precedence = module.getPrecedence();
        int minImportPrecedence = module.getMinImportPrecedence();
        double priority = test.getDefaultPriority();
        Pattern pattern = new ItemTypePattern(test);
        pattern.setSystemId(module.getSourceElement().getSystemId());
        pattern.setLineNumber(lineNumber);
        Rule newRule = new Rule(pattern, action, precedence, minImportPrecedence, priority, sequence++);
        newRule.setRank((precedence << 16) + sequence);
        if (test instanceof NodeKindTest) {
            newRule.setAlwaysMatches(true);
            anyElementRule = addRuleToList(newRule, anyElementRule, true);
        } else if (test instanceof NameTest) {
            newRule.setAlwaysMatches(true);
            int fp = test.getFingerprint();
            CodedName key = new CodedName(fp, ((NameTest) test).getNamePool());
            Rule chain = namedElementRules.get(key);
            namedElementRules.put(key, addRuleToList(newRule, chain, true));
        } else {
            unnamedElementRuleChain = addRuleToList(newRule, unnamedElementRuleChain, false);
        }

    }

    /**
     * Insert a new rule into this list before others of the same precedence
     * (we rely on the fact that all rules in a list have the same priority)
     * @param newRule the new rule to be added into the list
     * @param list the Rule at the head of the list, or null if the list is empty
     * @param dropRemainder if only one rule needs to be retained
     * @return the new head of the list (which might be the old head, or the new rule if it
     * was inserted at the start)
     */


    private Rule addRuleToList(Rule newRule, Rule list, boolean dropRemainder) throws XPathException {
        if (list == null) {
            return newRule;
        }
        int precedence = newRule.getPrecedence();
        Rule rule = list;
        Rule prev = null;
        while (rule != null) {
            if (rule.getPrecedence() <= precedence) {
                if (rejectDuplicates && rule.getPrecedence() == precedence && !rule.getAction().equals(newRule.getAction())) {
                    throw new XPathException(
                            "There are conflicting xsl:strip-space and xsl:preserve-space declarations for " +
                                rule.getPattern() + " at the same import precedence", "XTSE0270");
                }
                newRule.setNext(dropRemainder ? null : rule);
                if (prev == null) {
                    return newRule;
                } else {
                    prev.setNext(newRule);
                }
                break;
            } else {
                prev = rule;
                rule = rule.getNext();
            }
        }
        if (rule == null) {
            prev.setNext(newRule);
            newRule.setNext(null);
        }
        return list;
    }

    /**
     * Get the rule corresponding to a given element node, by finding the best pattern match.
     *
     * @param nodeName the name of the element node to be matched
     * @return the best matching rule, if any (otherwise null).
     */


   /*@Nullable*/ public Rule getRule(NodeName nodeName) {

        // search the specific list for this node type / node name

        Rule bestRule = namedElementRules.get(nodeName);

        // search the list for *:local and prefix:* node tests

        if (unnamedElementRuleChain != null) {
            bestRule = searchRuleChain(nodeName, bestRule, unnamedElementRuleChain);
        }

        // See if there is a "*" rule matching all elements

        if (anyElementRule != null) {
            bestRule = searchRuleChain(nodeName, bestRule, anyElementRule);
        }

        return bestRule;
    }

    /**
     * Search a chain of rules
     * @param nodeName the name of the element node being matched
     * @param bestRule the best rule so far in terms of precedence and priority (may be null)
     * @param head the rule at the head of the chain to be searched
     * @return the best match rule found in the chain, or the previous best rule, or null
     */

    private Rule searchRuleChain(NodeName nodeName, Rule bestRule, Rule head) {
        while (head != null) {
            if (bestRule != null) {
                int rank = head.compareRank(bestRule);
                if (rank < 0) {
                    // if we already have a match, and the precedence or priority of this
                    // rule is lower, quit the search
                    break;
                } else if (rank == 0) {
                    // this rule has the same precedence and priority as the matching rule already found
                    if (head.isAlwaysMatches() ||
                            ((NodeTest)head.getPattern().getItemType()).matches(Type.ELEMENT, nodeName, -1)) {
                        // reportAmbiguity(bestRule, head);
                        // We no longer report the recoverable error XTRE0270, we always
                        // take the recovery action.
                        // choose whichever one comes last (assuming the error wasn't fatal)
                        bestRule = head;
                        break;
                    } else {
                        // keep searching other rules of the same precedence and priority
                    }
                } else {
                    // this rule has higher rank than the matching rule already found
                    if (head.isAlwaysMatches() ||
                            ((NodeTest)head.getPattern().getItemType()).matches(Type.ELEMENT, nodeName, -1)) {
                        bestRule = head;
                    }
                }
            } else if (head.isAlwaysMatches() ||
                    ((NodeTest)head.getPattern().getItemType()).matches(Type.ELEMENT, nodeName, -1)) {
                bestRule = head;
                break;
            }
            head = head.getNext();
        }
        return bestRule;
    }



}

