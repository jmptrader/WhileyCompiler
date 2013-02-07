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

import static wybs.lang.SyntaxError.internalFailure;
import static wybs.lang.SyntaxError.syntaxError;
import static wycs.solver.Solver.*;
import static wyil.util.ErrorMessages.errorMessage;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import wyautl.core.Automaton;
import wyautl.util.BigRational;
import wybs.lang.*;
import wyil.lang.*;
import wyil.util.ErrorMessages;

import wycs.io.WycsFileWriter;
import wycs.lang.*;
import wycs.solver.Verifier;

/**
 * Responsible for converting a given Wyil bytecode into an appropriate
 * constraint which encodes its semantics.
 * 
 * @author David J. Pearce
 * 
 */
public class VerificationTransformer {
	private final Builder builder;
	private final WyilFile.Case method;
	private final String filename;
	private final boolean assume;
	private final boolean debug;

	public VerificationTransformer(Builder builder, WyilFile.Case method,
			String filename, boolean assume, boolean debug) {
		this.builder = builder;
		this.filename = filename;
		this.assume = assume;
		this.debug = debug;
		this.method = method;
	}

	public String filename() {
		return filename;
	}


	public void end(VerificationBranch.LoopScope scope, VerificationBranch branch) {
		// not sure what really needs to be done here, in fact.
	}


	public void exit(VerificationBranch.LoopScope scope, VerificationBranch branch) {
		for(Stmt s : scope.constraints) {
			branch.add(s);
		}
	}

	private static int counter = 0;

	public void end(VerificationBranch.ForScope scope, VerificationBranch branch) {
		// we need to build up a quantified formula here.

//		Automaton automaton = branch.automaton();
//		int root = And(automaton,scope.constraints);
//		int qvar = QVar(automaton,"X" + counter++);
//		int idx = qvar;		
//		root = automaton.substitute(root, scope.index, idx);
//		branch.assume(ForAll(automaton,qvar,scope.source,root));
	}

	public void exit(VerificationBranch.ForScope scope,
			VerificationBranch branch) {
		
//		Automaton automaton = branch.automaton();
//		int root = And(automaton, scope.constraints);
//		int qvar = QVar(automaton, "X" + counter++);
//		int idx = qvar;		
//		root = automaton.substitute(root, scope.index, idx);
//		branch.assume(Exists(automaton, qvar, scope.source, root));
	}

	public void exit(VerificationBranch.TryScope scope, VerificationBranch branch) {
		
	}
	
	protected void transform(Code.Assert code, VerificationBranch branch) {
		Expr test = buildTest(code.op, code.leftOperand, code.rightOperand,
				code.type, branch);
		if (!assume) {						
			List<Stmt> constraints = branch.constraints();
			constraints.add(Stmt.Assert(code.msg, test, branch.entry().attributes()));
			WycsFile file = new WycsFile(filename,constraints);
			
			// TODO: at some point, I think it would make sense to separate the generation of the WycsFile from here. 
			
			if(debug) {			
				try {
					new WycsFileWriter(new PrintStream(System.err, true, "UTF8")).write(file);
				} catch(UnsupportedEncodingException e) {
					// back up plan
					new WycsFileWriter(System.err).write(file);				
				}
				System.err.println();
			}				
			
			List<Boolean> results = new Verifier(debug).verify(file);
			for(int i=0,j=0;i!=constraints.size();++i) {
				Stmt stmt = constraints.get(i);
				if(stmt instanceof Stmt.Assert) {				
					if(!results.get(j++)) {
						Stmt.Assert sa = (Stmt.Assert) stmt;
						syntaxError(sa.message,filename,stmt);
					}
				}
			}
		}
		
		branch.add(Stmt.Assume(test, branch.entry().attributes()));
	}
	
	protected void transform(Code.Assume code, VerificationBranch branch) {
		// At this point, what we do is invert the condition being asserted and
		// check that it is unsatisfiable.
		Expr test = buildTest(code.op, code.leftOperand, code.rightOperand,
				code.type, branch);
		branch.add(Stmt.Assume(test, branch.entry().attributes()));
	}

	protected void transform(Code.Assign code, VerificationBranch branch) {
		branch.write(code.target, branch.read(code.operand));
	}

	protected void transform(Code.BinArithOp code, VerificationBranch branch) {		
		Expr lhs = branch.read(code.leftOperand);
		Expr rhs = branch.read(code.rightOperand);
		int result;
		Expr.Binary.Op op;

		switch (code.kind) {
		case ADD:
			op = Expr.Binary.Op.ADD;
			break;
		case SUB:
			op = Expr.Binary.Op.SUB;
			break;
		case MUL:
			op = Expr.Binary.Op.MUL;
			break;
		case DIV:			
			op = Expr.Binary.Op.DIV;
			break;
		case RANGE:
			op = Expr.Binary.Op.RANGE;
			break;
		default:
			internalFailure("unknown binary operator", filename, branch.entry());
			return;
		}

		branch.write(code.target, Expr.Binary(op,lhs,rhs, branch.entry().attributes()));
	}

	protected void transform(Code.BinListOp code, VerificationBranch branch) {		
		Expr lhs = branch.read(code.leftOperand);
		Expr rhs = branch.read(code.rightOperand);		

		switch (code.kind) {
		case APPEND:
			// do nothing
			break;
		case LEFT_APPEND:			
			rhs = Expr.Nary(Expr.Nary.Op.LIST, new Expr[]{rhs}, branch.entry().attributes());
			break;
		case RIGHT_APPEND:
			lhs = Expr.Nary(Expr.Nary.Op.LIST, new Expr[]{lhs}, branch.entry().attributes());
			break;		
		default:
			internalFailure("unknown binary operator", filename, branch.entry());
			return;
		}
		
		branch.write(code.target, Expr.Binary(Expr.Binary.Op.APPEND, lhs, rhs, branch.entry().attributes()));

	}

	protected void transform(Code.BinSetOp code, VerificationBranch branch) {
		Expr lhs = branch.read(code.leftOperand);
		Expr rhs = branch.read(code.rightOperand);
		Expr.Nary.Op op;

		switch (code.kind) {
		case UNION:
			op = Expr.Nary.Op.UNION;
			break;
		case LEFT_UNION:
			op = Expr.Nary.Op.UNION;
			rhs = Expr.Nary(Expr.Nary.Op.SET, new Expr[]{rhs}, branch.entry().attributes());			
			break;
		case RIGHT_UNION:
			op = Expr.Nary.Op.UNION;
			lhs = Expr.Nary(Expr.Nary.Op.SET, new Expr[]{lhs}, branch.entry().attributes());
			break;
		case INTERSECTION:
			op = Expr.Nary.Op.INTERSECTION;			
			break;
		case LEFT_INTERSECTION:
			op = Expr.Nary.Op.INTERSECTION;
			rhs = Expr.Nary(Expr.Nary.Op.SET, new Expr[]{rhs}, branch.entry().attributes());
			break;
		case RIGHT_INTERSECTION:
			op = Expr.Nary.Op.INTERSECTION;
			lhs = Expr.Nary(Expr.Nary.Op.SET, new Expr[]{lhs}, branch.entry().attributes());
			break;		
		case LEFT_DIFFERENCE:
			rhs = Expr.Nary(Expr.Nary.Op.SET, new Expr[]{rhs}, branch.entry().attributes());
		case DIFFERENCE:			
			branch.write(code.target, Expr.Binary(Expr.Binary.Op.DIFFERENCE, lhs, rhs, branch.entry().attributes()));
			return;
		default:
			internalFailure("unknown binary operator", filename, branch.entry());
			return;

		}

		branch.write(code.target, Expr.Nary(op, new Expr[]{lhs, rhs}, branch.entry().attributes()));
	}

	protected void transform(Code.BinStringOp code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.Convert code, VerificationBranch branch) {
		Expr result = branch.read(code.operand);
		// TODO: actually implement some or all coercions?
		branch.write(code.target, result);
	}

	protected void transform(Code.Const code, VerificationBranch branch) {
		branch.write(code.target, Expr.Constant(code.constant, branch.entry().attributes()));
	}

	protected void transform(Code.Debug code, VerificationBranch branch) {
		// do nout
	}

	protected void transform(Code.Dereference code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.FieldLoad code, VerificationBranch branch) {
		Expr src = branch.read(code.operand);		
		branch.write(code.target, Expr.FieldOf(src,code.field,branch.entry().attributes()));
	}

	protected void transform(Code.If code, VerificationBranch falseBranch,
			VerificationBranch trueBranch) {		
		// First, cover true branch
		Expr.Binary trueTest = buildTest(code.op, code.leftOperand, code.rightOperand,
				code.type, trueBranch);
		trueBranch.add(Stmt.Assume(trueTest, trueBranch.entry().attributes()));
		falseBranch.add(Stmt.Assume(invert(trueTest), falseBranch.entry().attributes()));
	}

	protected void transform(Code.IfIs code, VerificationBranch falseBranch,
			VerificationBranch trueBranch) {
		// TODO
	}

	protected void transform(Code.IndirectInvoke code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.Invoke code, VerificationBranch branch)
			throws Exception {
//		int[] code_operands = code.operands;
//				
//		if (code.target != Code.NULL_REG) {
//			// Need to assume the post-condition holds.
//			Block postcondition = findPostcondition(code.name, code.type,
//					branch.entry());
//			int[] operands = new int[code_operands.length + 1];
//			for (int i = 0; i != code_operands.length; ++i) {
//				operands[i + 1] = branch.read(code_operands[i]);
//			}
//			
//			operands[0] = branch.automaton().add(new Automaton.Strung(code.name.toString()));
//			int fn = Fn(branch.automaton(),operands);
//			branch.write(code.target, fn);
//			
//			if (postcondition != null) {
//				operands = Arrays.copyOf(operands, operands.length);
//				operands[0] = branch.read(code.target);
//				int constraint = transformExternalBlock(postcondition, 
//						operands, branch);
//				// assume the post condition holds
//				branch.assume(constraint);
//			}		
//		}
	}

	protected void transform(Code.Invert code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.IndexOf code, VerificationBranch branch) {				
		Expr src = branch.read(code.leftOperand);
		Expr idx = branch.read(code.rightOperand);
		branch.write(code.target, Expr.Binary(Expr.Binary.Op.INDEXOF, src, idx, branch.entry().attributes()));
	}

	protected void transform(Code.LengthOf code, VerificationBranch branch) {
		Expr src = branch.read(code.operand);
		branch.write(code.target, Expr.Unary(Expr.Unary.Op.LENGTHOF, src, branch.entry().attributes()));
	}

	protected void transform(Code.Loop code, VerificationBranch branch) {
//		Automaton automaton = branch.automaton();
//		if (code instanceof Code.ForAll) {
//			Code.ForAll forall = (Code.ForAll) code;
//			// int end = findLabel(branch.pc(),forall.target,body);
//			int src = branch.read(forall.sourceOperand);
//			int idx = branch.read(forall.indexOperand);			
//			branch.assume(ElementOf(branch, idx, src, forall.type));
//		}
//		
		// FIXME: assume loop invariant?
	}

	protected void transform(Code.Move code, VerificationBranch branch) {
		branch.write(code.target, branch.read(code.operand));
	}

	protected void transform(Code.NewMap code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.NewList code, VerificationBranch branch) {
		int[] code_operands = code.operands;
		Expr[] vals = new Expr[code_operands.length];
		for (int i = 0; i != vals.length; ++i) {
			vals[i] = branch.read(code_operands[i]);
		}
		branch.write(code.target, Expr.Nary(Expr.Nary.Op.LIST, vals, branch.entry().attributes()));
	}

	protected void transform(Code.NewSet code, VerificationBranch branch) {
		int[] code_operands = code.operands;
		Expr[] vals = new Expr[code_operands.length];
		for (int i = 0; i != vals.length; ++i) {
			vals[i] = branch.read(code_operands[i]);
		}
		branch.write(code.target, Expr.Nary(Expr.Nary.Op.SET, vals, branch.entry().attributes()));
	}

	protected void transform(Code.NewRecord code, VerificationBranch branch) {
//		int[] code_operands = code.operands;
//		Type.Record type = code.type;
//		ArrayList<String> fields = new ArrayList<String>(type.fields().keySet());
//		Collections.sort(fields);
//		int[] vals = new int[fields.size()];
//		for (int i = 0; i != fields.size(); ++i) {
//			int k = branch.automaton().add(new Automaton.Strung(fields.get(i)));
//			int v = branch.read(code_operands[i]);
//			vals[i] = branch.automaton().add(new Automaton.List(k, v));
//		}
//
//		int result = Record(branch.automaton(), vals);
//		branch.write(code.target, result);
	}

	protected void transform(Code.NewObject code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.NewTuple code, VerificationBranch branch) {
		int[] code_operands = code.operands;
		Expr[] vals = new Expr[code_operands.length];
		for (int i = 0; i != vals.length; ++i) {
			vals[i] = branch.read(code_operands[i]);
		}
		branch.write(code.target, Expr.Nary(Expr.Nary.Op.TUPLE, vals, branch.entry().attributes()));
	}

	protected void transform(Code.Nop code, VerificationBranch branch) {
		// do nout
	}

	protected void transform(Code.Return code, VerificationBranch branch) {
		// nothing to do
	}

	protected void transform(Code.SubString code, VerificationBranch branch) {
//		Automaton automaton = branch.automaton();
//		int src = branch.read(code.operands[0]);
//		int start = branch.read(code.operands[1]);
//		int end = branch.read(code.operands[2]);
//		int result = SubList(automaton, src, start, end);
//		branch.write(code.target, result);
	}

	protected void transform(Code.SubList code, VerificationBranch branch) {
//		Automaton automaton = branch.automaton();
//		int src = branch.read(code.operands[0]);
//		int start = branch.read(code.operands[1]);
//		int end = branch.read(code.operands[2]);
//		int result = SubList(automaton, src, start, end);
//		branch.write(code.target, result);
	}

	protected void transform(Code.Throw code, VerificationBranch branch) {
		// TODO
	}

	protected void transform(Code.TupleLoad code, VerificationBranch branch) {
//		Automaton automaton = branch.automaton();
//		int src = branch.read(code.operand);
//		int idx = automaton.add(new Automaton.Int(code.index));
//		int result = TupleLoad(automaton, src, idx);
//		branch.write(code.target, result);
	}

	protected void transform(Code.TryCatch code, VerificationBranch branch) {
		// FIXME: do something here?
	}
	
	protected void transform(Code.UnArithOp code, VerificationBranch branch) {		
		if (code.kind == Code.UnArithKind.NEG) {
			Expr operand = branch.read(code.operand);
			branch.write(code.target, Expr.Unary(Expr.Unary.Op.NEG, operand, branch.entry().attributes()));
		} else {
			// TODO
		}
	}

	protected void transform(Code.Update code, VerificationBranch branch) {
//		int result = branch.read(code.operand);
//		int source = branch.read(code.target);
//		branch.write(code.target,
//				updateHelper(code.iterator(), source, result, branch));
	}

//	protected int updateHelper(Iterator<Code.LVal> iter, int source,
//			int result, VerificationBranch branch) {
//		if (!iter.hasNext()) {
//			return result;
//		} else {
//			Code.LVal lv = iter.next();
//			if (lv instanceof Code.RecordLVal) {
//				Code.RecordLVal rlv = (Code.RecordLVal) lv;
//				int field = branch.automaton().add(
//						new Automaton.Strung(rlv.field));
//				result = updateHelper(iter,
//						FieldOf(branch.automaton(), source, field), result,
//						branch);
//				return FieldUpdate(branch.automaton(), source, field, result);
//			} else if (lv instanceof Code.ListLVal) {
//				Code.ListLVal rlv = (Code.ListLVal) lv;
//				int index = branch.read(rlv.indexOperand);
//				result = updateHelper(iter,
//						IndexOf(branch.automaton(), source, index), result,
//						branch);
//				return ListUpdate(branch.automaton(), source, index, result);
//			} else if (lv instanceof Code.MapLVal) {
//				return source; // TODO
//			} else if (lv instanceof Code.StringLVal) {
//				return source; // TODO
//			} else {
//				return source; // TODO
//			}
//		}
// 	}

	protected Block findPrecondition(NameID name, Type.FunctionOrMethod fun,
			SyntacticElement elem) throws Exception {
		Path.Entry<WyilFile> e = builder.namespace().get(name.module(),
				WyilFile.ContentType);
		if (e == null) {
			syntaxError(
					errorMessage(ErrorMessages.RESOLUTION_ERROR, name.module()
							.toString()), filename, elem);
		}
		WyilFile m = e.read();
		WyilFile.MethodDeclaration method = m.method(name.name(), fun);

		for (WyilFile.Case c : method.cases()) {
			// FIXME: this is a hack for now
			return c.precondition();
		}
		return null;
	}

	protected Block findPostcondition(NameID name, Type.FunctionOrMethod fun,
			SyntacticElement elem) throws Exception {
		Path.Entry<WyilFile> e = builder.namespace().get(name.module(),
				WyilFile.ContentType);
		if (e == null) {
			syntaxError(
					errorMessage(ErrorMessages.RESOLUTION_ERROR, name.module()
							.toString()), filename, elem);
		}
		WyilFile m = e.read();
		WyilFile.MethodDeclaration method = m.method(name.name(), fun);

		for (WyilFile.Case c : method.cases()) {
			// FIXME: this is a hack for now
			return c.postcondition();
		}
		return null;
	}

	/**
	 * Generate a constraint representing an external block (e.g. a
	 * pre/post-condition or invariant).
	 * 
	 * @param externalBlock
	 *            --- the external block of code being translated.
	 * @param prefix
	 *            --- a prefix to use to ensure that local variables to the
	 *            external block will not clash with variables in the branch.
	 * @param operands
	 *            --- operand register in containing branch which should map to
	 *            the inputs of the block being translated.
	 * @param branch
	 *            --- branch into which the resulting constraint is to be
	 *            placed.
	 * @return
	 */
//	protected int transformExternalBlock(Block externalBlock, 
//			int[] operands, VerificationBranch branch) {
//		Automaton automaton = branch.automaton();
//		
//		// first, generate a constraint representing the post-condition.
//		VerificationBranch master = new VerificationBranch(automaton,
//				externalBlock);
//		
//		// second, set initial environment
//		for(int i=0;i!=operands.length;++i) {
//			master.write(i, operands[i]);
//		}
//		
//		return master.transform(new VerificationTransformer(builder,
//				method, filename, true, debug));
//	}

	/**
	 * Generate a formula representing a condition from an Code.IfCode or
	 * Code.Assert bytecodes.
	 * 
	 * @param op
	 * @param stack
	 * @param elem
	 * @return
	 */
	private Expr.Binary buildTest(Code.Comparator cop, int leftOperand,
			int rightOperand, Type type, VerificationBranch branch) {
		Expr lhs = branch.read(leftOperand);
		Expr rhs = branch.read(rightOperand);
		Expr.Binary.Op op;
		switch (cop) {
		case EQ:
			op = Expr.Binary.Op.EQ;
			break;
		case NEQ:
			op = Expr.Binary.Op.NEQ;
			break;
		case GTEQ:
			op = Expr.Binary.Op.GTEQ;
			break;
		case GT:
			op = Expr.Binary.Op.GT;
			break;
		case LTEQ:
			op = Expr.Binary.Op.LTEQ;
			break;		
		case LT:
			op = Expr.Binary.Op.LT;
			break;
		case SUBSET:
			op = Expr.Binary.Op.SUBSET;
			break;
		case SUBSETEQ:
			op = Expr.Binary.Op.SUBSETEQ;
			break;
		case ELEMOF:
			op = Expr.Binary.Op.IN;
			break;
		default:
			internalFailure("unknown comparator (" + cop + ")", filename,
					branch.entry());
			return null;
		}
		
		return Expr.Binary(op, lhs, rhs, branch.entry().attributes());
	}
	
	/**
	 * Generate the logically inverted expression corresponding to this comparator.
	 * @param cop
	 * @param leftOperand
	 * @param rightOperand
	 * @param type
	 * @param branch
	 * @return
	 */
	private Expr invert(Expr.Binary test) {		
		Expr.Binary.Op op;
		switch (test.op) {
		case EQ:
			op = Expr.Binary.Op.NEQ;
			break;
		case NEQ:
			op = Expr.Binary.Op.EQ;
			break;
		case GTEQ:
			op = Expr.Binary.Op.LT;
			break;
		case GT:
			op = Expr.Binary.Op.LTEQ;
			break;
		case LTEQ:
			op = Expr.Binary.Op.GT;
			break;		
		case LT:
			op = Expr.Binary.Op.GTEQ;
			break;
		case SUBSET:
			op = Expr.Binary.Op.SUPSETEQ;
			break;
		case SUBSETEQ:
			op = Expr.Binary.Op.SUPSET;
			break;
		case SUPSET:
			op = Expr.Binary.Op.SUBSETEQ;
			break;
		case SUPSETEQ:
			op = Expr.Binary.Op.SUBSET;
			break;		
		case IN:
			op = Expr.Binary.Op.IN;
			return Expr.Unary(Expr.Unary.Op.NOT, Expr.Binary(op, test.leftOperand, test.rightOperand, test.attributes()),test.attributes());			
		default:
			internalFailure("unknown comparator (" + test.op + ")", filename,
					test);
			return null;
		}
		
		return Expr.Binary(op, test.leftOperand, test.rightOperand, test.attributes());
	}
}
