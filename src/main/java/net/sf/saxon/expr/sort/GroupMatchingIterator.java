////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.sort;

import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.AtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ListIterator;
import net.sf.saxon.tree.iter.LookaheadIterator;
import net.sf.saxon.value.SequenceExtent;

import java.util.List;

/**
 * A GroupMatchingIterator contains code shared between GroupStartingIterator and GroupEndingIterator
 */

public abstract class GroupMatchingIterator implements LookaheadIterator, GroupIterator {

    protected SequenceIterator population;
    protected Pattern pattern;
    protected XPathContext baseContext;
    protected XPathContext runningContext;
    protected List currentMembers;
    /*@Nullable*/ protected Item next;
    protected Item current = null;
    protected int position = 0;
    private int groupSlot = -1;

    public void setGroupSlot(int groupSlot) {
        this.groupSlot = groupSlot;
    }

    public void setKeySlot(int keySlot) {
        // never used for this grouping method
    }


    protected abstract void advance() throws XPathException;

    public AtomicSequence getCurrentGroupingKey() {
        return null;
    }

    public SequenceIterator iterateCurrentGroup() {
        return new ListIterator(currentMembers);
    }

    public boolean hasCurrentGroup() {
        return groupSlot < 0;
    }

    public boolean hasCurrentGroupingKey() {
        return false;
    }

    public boolean hasNext() {
        return next != null;
    }

    public Item next() throws XPathException {
        if (next != null) {
            current = next;
            position++;
            advance();
            if (groupSlot >= 0) {
                runningContext.setLocalVariable(groupSlot, new SequenceExtent(currentMembers));
            }
            return current;
        } else {
            current = null;
            position = -1;
            return null;
        }
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

    public int getProperties() {
        return LOOKAHEAD;
    }


}

