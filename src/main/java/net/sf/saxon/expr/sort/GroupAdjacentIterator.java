////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.AtomicArray;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;

/**
 * A GroupAdjacentIterator iterates over a sequence of groups defined by
 * xsl:for-each-group group-adjacent="x". The groups are returned in
 * order of first appearance.
 * <p/>
 * Each step of this iterator advances to the first item of the next group,
 * leaving the members of that group in a saved list.
 */

public class GroupAdjacentIterator implements GroupIterator, LookaheadIterator {

    private SequenceIterator population;
    private Expression keyExpression;
    private AtomicComparer comparer;
    private XPathContext baseContext;
    private XPathContext runningContext;
    private List<ComparisonKey> currentComparisonKey;
    private AtomicSequence currentKey;
    private AtomicSequence currentKeyItems;
    private List<Item> currentMembers;
    private List<ComparisonKey> nextComparisonKey;
    private List<AtomicValue> nextKey = null;
    private Item next;
    private Item current = null;
    private int position = 0;
    private int groupSlot = -1;
    private int keySlot = -1;
    private boolean composite = false;

    public GroupAdjacentIterator(SequenceIterator population, Expression keyExpression,
                                 XPathContext baseContext, AtomicComparer comparer, boolean composite)
            throws XPathException {
        this.population = population;
        this.keyExpression = keyExpression;
        this.baseContext = baseContext;
        this.runningContext = baseContext.newMinorContext();
        runningContext.setCurrentIterator(population);
        this.comparer = comparer;
        this.composite = composite;
        next = population.next();
        if (next != null) {
            nextKey = getKey(runningContext);
            nextComparisonKey = getComparisonKey(nextKey);
        }
    }

    private List<AtomicValue> getKey(XPathContext context) throws XPathException {
        List<AtomicValue> key = new ArrayList<AtomicValue>();
        SequenceIterator iter = keyExpression.iterate(context);
        while (true) {
            AtomicValue val = (AtomicValue) iter.next();
            if (val == null) {
                break;
            }
            key.add(val);
        }
        return key;
    }

    private List<ComparisonKey> getComparisonKey(List<AtomicValue> key) throws XPathException {
        List<ComparisonKey> ckey = new ArrayList<ComparisonKey>(key.size());
        for (AtomicValue aKey : key) {
            ckey.add(comparer.getComparisonKey(aKey));
        }
        return ckey;
    }

    public void setGroupSlot(int groupSlot) {
        this.groupSlot = groupSlot;
    }

    public void setKeySlot(int keySlot) {
        this.keySlot = keySlot;
    }

    private void advance() throws XPathException {
        currentMembers = new ArrayList<Item>(20);
        currentMembers.add(current);
        while (true) {
            Item nextCandidate = population.next();
            if (nextCandidate == null) {
                break;
            }
            List<AtomicValue> newKey = getKey(runningContext);
            List<ComparisonKey> newComparisonKey = getComparisonKey(newKey);

            try {
                if (currentComparisonKey.equals(newComparisonKey)) {
                    currentMembers.add(nextCandidate);
                } else {
                    next = nextCandidate;
                    nextComparisonKey = newComparisonKey;
                    nextKey = newKey;
                    return;
                }
            } catch (ClassCastException e) {
                StringBuilder message = new StringBuilder("Grouping key values are of non-comparable types");
                if (currentKeyItems.getLength() != 0 && !newKey.isEmpty()) {
                    String t1 = Type.displayTypeName(currentKeyItems.itemAt(0));
                    String t2 = Type.displayTypeName(newKey.get(0));
                    if (!t1.equals(t2)) {
                        message.append(" (").append(t1).append(" and ").append(t2).append(')');
                    }
                }
                XPathException err = new XPathException(message.toString());
                err.setIsTypeError(true);
                err.setXPathContext(runningContext);
                throw err;
            }
        }
        next = null;
        nextKey = null;
    }

    public AtomicSequence getCurrentGroupingKey() {
        return currentKey;
    }

    public SequenceIterator iterateCurrentGroup() {
        return new ListIterator(currentMembers);
    }

    public boolean hasCurrentGroup() {
        return groupSlot < 0;
    }

    public boolean hasCurrentGroupingKey() {
        return keySlot < 0;
    }

    public boolean hasNext() {
        return next != null;
    }

    public Item next() throws XPathException {
        if (next == null) {
            current = null;
            position = -1;
            return null;
        }
        current = next;
        if (nextKey.size() == 1) {
            currentKey = nextKey.get(0);
        } else {
            currentKey = new AtomicArray(nextKey.toArray(new AtomicValue[nextKey.size()]));
        }
        currentComparisonKey = nextComparisonKey;
        position++;
        advance();
        if (groupSlot >= 0) {
            runningContext.setLocalVariable(groupSlot, new SequenceExtent(currentMembers));
        }
        if (keySlot >= 0) {
            runningContext.setLocalVariable(keySlot, currentKey);
        }
        return current;
    }

    public Item current() {
        return current;
    }

    public int position() {
        return position;
    }

    public void close() {
        population.close();
    }

    /*@NotNull*/
    public SequenceIterator getAnother() throws XPathException {
        return new GroupAdjacentIterator(population.getAnother(), keyExpression, baseContext, comparer, composite);
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
        return LOOKAHEAD;
    }



}

