// Copyright (c) 2011, David J. Pearce (djp@ecs.vuw.ac.nz)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//    * Redistributions of source code must retain the above copyright
//      notice, this list of conditions and the following disclaimer.
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//    * Neither the name of the <organization> nor the
//      names of its contributors may be used to endorse or promote products
//      derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
// ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
// WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
// DISCLAIMED. IN NO EVENT SHALL DAVID J. PEARCE BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
// (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
// LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
// ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package wyautl.rw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import wyautl.core.Automaton;
import wyautl.core.Schema;
import wyautl.rw.AbstractRewriter.MinRuleComparator;

/**
 * <p>
 * A naive implementation of <code>RewriteSystem</code> which works correctly,
 * but is not efficient. This simply loops through every state and trys every
 * rule until one successfully activates. Then, it repeats until there are no
 * more activations. This is not efficient because it can result in a very high
 * number of unnecessary probes.
 * </p>
 * 
 * <p>
 * <b>NOTE:</b> this is not designed to be used in a concurrent setting.
 * </p>
 * 
 * @author David J. Pearce
 * 
 */
public final class SimpleRewriteStrategy extends AbstractRewriter.Strategy {

	/**
	 * The list of available rewrite rules.
	 */
	private final RewriteRule[] rules;

	/**
	 * Temporary list of inference activations used.
	 */	
	private final ArrayList<Activation> worklist = new ArrayList<Activation>();
	
	/**
	 * The automaton being rewritten
	 */
	private final Automaton automaton;
	
	/**
	 * The current state being explored by this strategy
	 */
	private int current;
		
	public SimpleRewriteStrategy(Automaton automaton, RewriteRule[] rules,
			ReductionRule[] reductions, Schema schema) {
		this(automaton, rules, schema,
				new MinRuleComparator<RewriteRule>());
	}

	public SimpleRewriteStrategy(Automaton automaton, RewriteRule[] rules,
			Schema schema, Comparator<RewriteRule> comparator) {
		Arrays.sort(rules, comparator);
		this.automaton = automaton;
		this.rules = rules;
	}
	
	@Override
	protected Activation next(boolean[] reachable) {
		int nStates = automaton.nStates();
		
		while (current < nStates && worklist.size() == 0) {
			if(reachable[current]) {
				// TODO: this is not efficient as it is should be probing lazily.
				for (int j = 0; j != rules.length; ++j) {
					RewriteRule rw = rules[j];
					rw.probe(automaton, current, worklist);
				}
			}
			current = current + 1;
		}
		
		if (current == nStates) {
			return null;
		} else {
			int lastIndex = worklist.size() - 1;
			Activation last = worklist.get(lastIndex);
			worklist.remove(lastIndex);
			return last;
		}
	}

	@Override
	protected void invalidate() {
		worklist.clear();
		current = 0;
	}
}