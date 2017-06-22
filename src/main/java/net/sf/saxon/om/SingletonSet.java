////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2013 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.om;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A set with exactly one (non-null) member
 * @param <T> the type of the member
 */
public class SingletonSet<T> extends AbstractSet<T> {

        final private T member;

        public SingletonSet(T e) {
            member = e;
        }

        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private boolean more = true;
                public boolean hasNext() {
                    return more;
                }
                public T next() {
                    if (more) {
                        more = false;
                        return member;
                    }
                    throw new NoSuchElementException();
                }
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        public int size() {
            return 1;
        }

        public boolean contains(Object o) {
            return o.equals(member);
        }
    }



