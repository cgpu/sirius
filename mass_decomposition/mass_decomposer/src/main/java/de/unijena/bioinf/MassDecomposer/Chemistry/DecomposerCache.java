/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unijena.bioinf.MassDecomposer.Chemistry;

import de.unijena.bioinf.ChemistryBase.chem.ChemicalAlphabet;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * caches decomposer and corresponding alphabet. If a dataset contains a small number of different alphabets,
 * the cache creates for each such alphabet an own decomposer instead of creating a new one each time the alphabet changes.
 */
public class DecomposerCache {

    private ChemicalAlphabet[] alphabets;
    private MassToFormulaDecomposer[] decomposers;
    private AtomicInteger[] useCounter;
    private int dirtyState;
    private int size;

    private ReentrantLock lock = new ReentrantLock();

    public DecomposerCache(int size) {
        this.alphabets = new ChemicalAlphabet[size];
        this.decomposers = new MassToFormulaDecomposer[size];
        this.useCounter = new AtomicInteger[size];
        this.dirtyState = 0;
        for (int k=0; k < size; ++k) useCounter[k] = new AtomicInteger(0);
        this.size = 0;
    }

    public MassToFormulaDecomposer getDecomposer(ChemicalAlphabet alphabet) {
        while (true) {
            int state = dirtyState;
            MassToFormulaDecomposer d = findDecomposer(alphabet,state);
            if (d == null) {
                lock.lock();
                try {
                    if (state == dirtyState) {
                        //System.err.println(this.toString() + " will decompose " + alphabet + " from " + Thread.currentThread().getName() + " with state " + state);
                        d = addNewDecomposer(alphabet);
                    } else {
                        //System.err.println(this.toString() + " STATE CHANGED " + alphabet + " from " + Thread.currentThread().getName() + " with state " + state);
                        continue;
                    }
                } finally {
                    lock.unlock();
                }
                return d;
            } else if (state == dirtyState) return d;
        }
    }

    private MassToFormulaDecomposer findDecomposer(ChemicalAlphabet alphabet, int state) {
        for (int i=0; i < size; ++i) {
            if (alphabets[i].equals(alphabet)) {
                useCounter[i].incrementAndGet();
                return decomposers[i];
            }
        }
        //System.err.println("Search " + alphabet + " in " + Arrays.toString(alphabets) + " without success at state " + state + " in thread " + Thread.currentThread().getName() );
        return null;
    }

    private MassToFormulaDecomposer addNewDecomposer(ChemicalAlphabet alphabet) {
        ++dirtyState;
        try {
        if (size < alphabets.length) {
            decomposers[size] = new MassToFormulaDecomposer(alphabet);
            decomposers[size].init();
            alphabets[size] = alphabet;
            return decomposers[size++];
        } else {
            int mindex = 0;
            for (int i=1; i < useCounter.length; ++i)
                if (useCounter[i].get() < useCounter[mindex].get()) mindex = i;
            decomposers[mindex] =  new MassToFormulaDecomposer(alphabet);
            decomposers[mindex].init();
            alphabets[mindex] = alphabet;
            return decomposers[mindex];
        } } finally {
            ++dirtyState;
            //System.err.println(alphabet.toString() + " IS ADDED BY " + Thread.currentThread().getName() + " with state " + dirtyState);
        }
    }

    public DecomposerCache() {
        this(10);
    }

}
