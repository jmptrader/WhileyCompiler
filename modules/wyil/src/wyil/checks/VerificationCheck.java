// Copyright (c) 2012, David J. Pearce (djp@ecs.vuw.ac.nz)
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

package wyil.checks;

import java.util.*;

import wyautl.core.Automaton;
import wyautl.io.PrettyAutomataWriter;
import wyautl.util.BigRational;
import wybs.lang.Builder;
import wybs.lang.Path;
import wybs.lang.SyntacticElement;
import wybs.lang.SyntaxError;
import wyil.lang.*;
import wyil.transforms.RuntimeAssertions;
import wyil.util.ErrorMessages;
import wyil.util.Pair;
import static wybs.lang.SyntaxError.*;
import static wyil.util.ErrorMessages.errorMessage;
import wyil.Transform;
import wycs.Solver;
import static wycs.Solver.*;

/**
 * Responsible for compile-time checking of constraints. This involves
 * converting WYIL into the appropriate form for the automated theorem prover
 * (wyone).
 * 
 * @author David J. Pearce
 * 
 */
public class VerificationCheck implements Transform {

	/**
	 * Determines whether verification is enabled or not.
	 */
	private boolean enabled = getEnable();

	/**
	 * Enables debugging information to be printed.
	 */
	private boolean debug = getDebug();

	private final Builder builder;

	private String filename;

	public VerificationCheck(Builder builder) {
		this.builder = builder;
	}

	public static String describeEnable() {
		return "Enable/disable compile-time verification";
	}

	public static boolean getEnable() {
		return false; // default value
	}

	public void setEnable(boolean flag) {
		this.enabled = flag;
	}

	public static String describeDebug() {
		return "Enable/disable debugging information";
	}

	public static boolean getDebug() {
		return false; // default value
	}

	public void setDebug(boolean flag) {
		this.debug = flag;
	}

	public static String describeMaxSteps() {
		return "Set maximum number of steps constraint solver can apply for a given assertion";
	}

	public static long getMaxSteps() {
		return Solver.MAX_STEPS;
	}

	public void setMaxSteps(long steps) {
		Solver.MAX_STEPS = steps;
	}

	public void setMaxSteps(int steps) {
		Solver.MAX_STEPS = steps;
	}

	public void apply(WyilFile module) {
		if (enabled) {
			this.filename = module.filename();
			for (WyilFile.TypeDeclaration type : module.types()) {
				transform(type);
			}
			for (WyilFile.MethodDeclaration method : module.methods()) {
				transform(method);
			}
		}
	}

	protected void transform(WyilFile.TypeDeclaration def) {

	}

	protected void transform(WyilFile.MethodDeclaration method) {
		for (WyilFile.Case c : method.cases()) {
			transform(c, method);
		}
	}

	protected void transform(WyilFile.Case methodCase,
			WyilFile.MethodDeclaration method) {
		if (!RuntimeAssertions.getEnable()) {
			// inline constraints if they have not already been done.
			RuntimeAssertions rac = new RuntimeAssertions(builder, filename);
			methodCase = rac.transform(methodCase, method);
		}

		// add type information available from parameters
		if (debug) {
			System.err.println("============================================");
			Type.FunctionOrMethod fmt = method.type();
			String paramString = fmt.params().toString();
			paramString = paramString.substring(1, paramString.length() - 1);
			if (method.type() instanceof Type.Function) {
				System.err.println("function " + method.name() + " "
						+ paramString + " -> " + fmt.ret());
			} else {
				System.err.println("METHOD: " + fmt.ret() + " " + method.name()
						+ "(" + paramString + ")");
			}
		}

		Type.FunctionOrMethod fmm = method.type();
		int paramStart = 0;

		Block body = methodCase.body();

		VerificationBranch master = new VerificationBranch(new Automaton(),
				body);

		for (int i = paramStart; i != fmm.params().size(); ++i) {
			Type paramType = fmm.params().get(i);
			int var = master.automaton().add(new Automaton.Strung(Integer.toString(i)));
			master.write(i, Var(master.automaton(), var));
			// FIXME: add type information

			// WVariable pv = new WVariable(i + "$" + 0);
			// constraint = WFormulas.and(branch.automaton(),
			// WTypes.subtypeOf(pv, convert(paramType)));

		}

		Block precondition = methodCase.precondition();

		if (precondition != null) {
			VerificationBranch precond = new VerificationBranch(
					master.automaton(), precondition);
			// FIXME: following seems like a hack --- there must be a more
			// elegant way of doing this?
			for (int i = paramStart; i != fmm.params().size(); ++i) {
				precond.write(i,master.read(i));
			}
			int constraint = precond.transform(new VerificationTransformer(
					builder, methodCase, filename, true, debug));
			
			master.assume(constraint);
		}

		master.transform(new VerificationTransformer(builder, methodCase,
				filename, false, debug));
	}
}