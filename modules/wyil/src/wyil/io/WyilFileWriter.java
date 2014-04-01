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

package wyil.io;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

import wycc.lang.NameID;
import wycc.util.Pair;
import wyfs.io.BinaryOutputStream;
import wyfs.lang.Path;
import wyil.lang.*;
import wyautl.util.BigRational;

/**
 * <p>
 * Responsible for writing a WyilFile to an output stream in binary form. The
 * binary format is structured to given maximum flexibility and to avoid
 * built-in limitations in terms of e.g. maximum sizes, etc.
 * </p>
 * 
 * <p>
 * The primitive component of a WyilFile is a <i>block</i>. Each block has a
 * kind, a given and then the payload. Blocks which are not recognised can be
 * ignored by skipping over their payload. Blocks are always byte aligned, but
 * their contents may not be.
 * </p>
 * 
 * <p>
 * A binary Wyil file begins with a header and a resource pool stratified into
 * sections containing the string constants, general constants, types and more.
 * Following the header are zero or more module blocks. Additional top-level
 * block kinds (e.g. for licenses) may be specified in the future. Each module
 * block consists of zero or more declaration blocks, which include method and
 * function declarations, type declarations and constant declarations.
 * </p>
 * 
 * @author David J. Pearce
 * 
 */
public final class WyilFileWriter {
	private static final int MAJOR_VERSION = 0;
	private static final int MINOR_VERSION = 1;
	
	private final BinaryOutputStream out;
	
	private final ArrayList<String> stringPool  = new ArrayList<String>();
	private final HashMap<String,Integer> stringCache = new HashMap<String,Integer>();	
	private final ArrayList<PATH_Item> pathPool = new ArrayList<PATH_Item>();
	private final HashMap<Path.ID,Integer> pathCache = new HashMap<Path.ID,Integer>();	
	private final ArrayList<NAME_Item> namePool = new ArrayList<NAME_Item>();
	private final HashMap<NameID,Integer> nameCache = new HashMap<NameID,Integer>();	
	private final ArrayList<Constant> constantPool = new ArrayList<Constant>();
	private final HashMap<Constant,Integer> constantCache = new HashMap<Constant,Integer>();	
	private final ArrayList<Type> typePool = new ArrayList<Type>();
	private final HashMap<Type,Integer> typeCache = new HashMap<Type,Integer>();
	
	public WyilFileWriter(OutputStream output) {
		this.out = new BinaryOutputStream(output); 
	}
	
	public void close() throws IOException {
		out.close();
	}	
	
	public void write(WyilFile module) throws IOException {				
		// first, write magic number
		out.write_u8(0x57); // W
		out.write_u8(0x59); // Y
		out.write_u8(0x49); // I
		out.write_u8(0x4C); // L
		out.write_u8(0x46); // F
		out.write_u8(0x49); // I
		out.write_u8(0x4C); // L
		out.write_u8(0x45); // E

		// second, build pools
		buildPools(module);
		
		// third, write head block
		writeBlock(BLOCK_Header,module,out);
		
		// fourth, write module block(s)
		writeBlock(BLOCK_Module,module,out);
		
		out.flush();
	}	
	
	private void writeBlock(int kind, Object data, BinaryOutputStream output)
			throws IOException {
		
		output.pad_u8(); // pad out to next byte boundary
		
		// determine bytes for block
		byte[] bytes = null;
		switch(kind) {
			case BLOCK_Header:
				bytes = generateHeaderBlock((WyilFile) data);
				break;
			case BLOCK_Module:
				bytes = generateModuleBlock((WyilFile) data);
				break;
			case BLOCK_Type:
				bytes = generateTypeBlock((WyilFile.TypeDeclaration) data);
				break;
			case BLOCK_Constant:
				bytes = generateConstantBlock((WyilFile.ConstantDeclaration) data);
				break;
			case BLOCK_Function:
				bytes = generateMethodBlock((WyilFile.FunctionOrMethodDeclaration) data);
				break;
			case BLOCK_Method:
				bytes = generateMethodBlock((WyilFile.FunctionOrMethodDeclaration) data);
				break;
			case BLOCK_Case:
				bytes = generateMethodCaseBlock((WyilFile.Case) data);
				break;
			case BLOCK_Body:
			case BLOCK_Precondition:
			case BLOCK_Postcondition:
			case BLOCK_Constraint:
				bytes = generateCodeBlock((Block) data);
				break;
		}
		
		output.write_uv(kind);
		output.write_uv(bytes.length);
		output.pad_u8(); // pad out to next byte boundary
		output.write(bytes);
	}
	
	/**
	 * Write the header information for this WYIL file, including the stratified
	 * resource pool.
	 * 
	 * @param module
	 * 
	 * @throws IOException
	 */
	private byte[] generateHeaderBlock(WyilFile module)
			throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();		
		BinaryOutputStream output = new BinaryOutputStream(bytes);
		
		// second, write the file version number		
		output.write_uv(MAJOR_VERSION); 
		output.write_uv(MINOR_VERSION); 
		
		// third, write the various pool sizes
		output.write_uv(stringPool.size());
		output.write_uv(pathPool.size());
		output.write_uv(namePool.size());
		output.write_uv(typePool.size());
		output.write_uv(constantPool.size());		
		
		// finally, write the number of remaining blocks
		output.write_uv(module.declarations().size());
				
		writeStringPool(output);
		writePathPool(output);
		writeNamePool(output);
		writeTypePool(output);
		writeConstantPool(output);
		
		output.close();
		
		return bytes.toByteArray();
	}
	
	/**
	 * Write the list of strings making up the string pool in UTF8.
	 * 
	 * @throws IOException
	 */
	private void writeStringPool(BinaryOutputStream output) throws IOException {
		//System.out.println("Writing " + stringPool.size() + " string item(s).");
		for (String s : stringPool) {
			try {
				byte[] bytes = s.getBytes("UTF-8");
				output.write_uv(bytes.length);				
				output.write(bytes, 0, bytes.length);
			} catch (UnsupportedEncodingException e) {
				// hmmm, this aint pretty ;)
			}
		}
	}
	
	private void writePathPool(BinaryOutputStream output) throws IOException {
		for(int i=1;i<pathPool.size();++i) {
			PATH_Item p = pathPool.get(i);
			output.write_uv(p.parentIndex);
			output.write_uv(p.stringIndex);
		}
	}

	private void writeNamePool(BinaryOutputStream output) throws IOException {
		//System.out.println("Writing " + stringPool.size() + " name item(s).");
		for (NAME_Item p : namePool) {
			//output.write_uv(p.kind.kind());
			output.write_uv(p.pathIndex);
			output.write_uv(p.nameIndex);
		}
	}

	private void writeConstantPool(BinaryOutputStream output) throws IOException {
		//System.out.println("Writing " + stringPool.size() + " constant item(s).");		
		
		for (Constant val : constantPool) {
			if(val instanceof Constant.Null) {
				output.write_uv(CONSTANT_Null);
				
			} else if(val instanceof Constant.Bool) {
				Constant.Bool b = (Constant.Bool) val; 
				output.write_uv(b.value ? CONSTANT_True : CONSTANT_False);							
				
			} else if(val instanceof Constant.Byte) {
				Constant.Byte b = (Constant.Byte) val;
				output.write_uv(CONSTANT_Byte);		
				output.write_u8(b.value);
				
			} else if(val instanceof Constant.Char) {
				Constant.Char c = (Constant.Char) val;
				output.write_uv(CONSTANT_Char);		
				output.write_uv(c.value);	
				
			} else if(val instanceof Constant.Integer) {
				Constant.Integer i = (Constant.Integer) val; 					
				BigInteger num = i.value;
				byte[] numbytes = num.toByteArray();
				output.write_uv(CONSTANT_Int);
				output.write_uv(numbytes.length);
				output.write(numbytes);			
				
			} else if(val instanceof Constant.Strung) {
				Constant.Strung s = (Constant.Strung) val;
				output.write_uv(CONSTANT_String);
				String value = s.value;
				output.write_uv(stringCache.get(value));				
				
			} else if(val instanceof Constant.Decimal) {
				Constant.Decimal r = (Constant.Decimal) val;
				output.write_uv(CONSTANT_Real);
				BigInteger mantissa = r.value.unscaledValue();
				int exponent = r.value.scale();
				byte[] bytes = mantissa.toByteArray();
				output.write_uv(bytes.length);
				output.write(bytes);
				output.write_uv(exponent);				
			} else if(val instanceof Constant.Set) {
				Constant.Set s = (Constant.Set) val;
				output.write_uv(CONSTANT_Set);
				output.write_uv(s.values.size());
				for(Constant v : s.values) {
					int index = constantCache.get(v);
					output.write_uv(index);
				}
				
			} else if(val instanceof Constant.List) {				
				Constant.List s = (Constant.List) val;
				output.write_uv(CONSTANT_List);
				output.write_uv(s.values.size());
				for(Constant v : s.values) {
					int index = constantCache.get(v);
					output.write_uv(index);
				}
				
			} else if(val instanceof Constant.Map) {
				Constant.Map m = (Constant.Map) val;
				output.write_uv(CONSTANT_Map);
				output.write_uv(m.values.size());
				for(java.util.Map.Entry<Constant,Constant> e : m.values.entrySet()) {
					int keyIndex = constantCache.get(e.getKey());
					output.write_uv(keyIndex);
					int valIndex = constantCache.get(e.getValue());
					output.write_uv(valIndex);
				}		
				
			} else if(val instanceof Constant.Record) {
				Constant.Record r = (Constant.Record) val; 
				output.write_uv(CONSTANT_Record);
				output.write_uv(r.values.size());
				for(java.util.Map.Entry<String,Constant> v : r.values.entrySet()) {
					output.write_uv(stringCache.get(v.getKey()));
					int index = constantCache.get(v.getValue());
					output.write_uv(index);
				}
				
			} else if(val instanceof Constant.Tuple) {
				Constant.Tuple t = (Constant.Tuple) val; 
				output.write_uv(CONSTANT_Tuple); 
				output.write_uv(t.values.size());
				for(Constant v : t.values) {
					int index = constantCache.get(v);
					output.write_uv(index);
				}
			} else if(val instanceof Constant.Lambda) {
				Constant.Lambda fm = (Constant.Lambda) val;
				Type.FunctionOrMethod t = fm.type();
				output.write_uv(t instanceof Type.Function ? CONSTANT_Function
						: CONSTANT_Method);
				output.write_uv(typeCache.get(t));
				output.write_uv(nameCache.get(fm.name)); 
			} else {
				throw new RuntimeException("Unknown value encountered - " + val);
			}
		}
	}
	
	private void writeTypePool(BinaryOutputStream output) throws IOException {
		//System.out.println("Writing " + stringPool.size() + " type item(s).");
		Type.BinaryWriter bout = new Type.BinaryWriter(output);
		for (Type t : typePool) {
			bout.write(t);
		}
	}
	
	private byte[] generateModuleBlock(WyilFile module) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BinaryOutputStream output = new BinaryOutputStream(bytes);
		
		output.write_uv(pathCache.get(module.id())); // FIXME: BROKEN!
		output.write_uv(MODIFIER_Public); // for now
		output.write_uv(module.declarations().size());
		
		for(WyilFile.Declaration d : module.declarations()) {
			writeModuleBlock(d,output);
		}		

        output.close();

		return bytes.toByteArray();
	}
	
	private void writeModuleBlock(WyilFile.Declaration d,
			BinaryOutputStream output) throws IOException {
		if(d instanceof WyilFile.ConstantDeclaration) {
			writeBlock(BLOCK_Constant, d ,output);			
		} else if(d instanceof WyilFile.TypeDeclaration) {
			writeBlock(BLOCK_Type, d, output);			
		} else if(d instanceof WyilFile.FunctionOrMethodDeclaration) {
			WyilFile.FunctionOrMethodDeclaration md = (WyilFile.FunctionOrMethodDeclaration) d;
			if(md.type() instanceof Type.Function) {
				writeBlock(BLOCK_Function, d, output);
			} else {
				writeBlock(BLOCK_Method, d, output);
			}
		} 
	}
		
	private byte[] generateConstantBlock(WyilFile.ConstantDeclaration cd) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BinaryOutputStream output = new BinaryOutputStream(bytes);
					
		output.write_uv(stringCache.get(cd.name()));
		output.write_uv(generateModifiers(cd.modifiers()));
		output.write_uv(constantCache.get(cd.constant()));
		output.write_uv(0); // no sub-blocks
		// TODO: write annotations
		
		output.close();
		return bytes.toByteArray();
	}
	
	private byte[] generateTypeBlock(WyilFile.TypeDeclaration td) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BinaryOutputStream output = new BinaryOutputStream(bytes);
				
		output.write_uv(stringCache.get(td.name()));
		output.write_uv(generateModifiers(td.modifiers()));
		output.write_uv(typeCache.get(td.type()));
		if(td.constraint() == null) {
			output.write_uv(0); // no sub-blocks
		} else {
			output.write_uv(1); // one sub-block
			writeBlock(BLOCK_Constraint,td.constraint(),output);
		}

		output.close();
		return bytes.toByteArray();
	}

	private byte[] generateMethodBlock(WyilFile.FunctionOrMethodDeclaration md) throws IOException {		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BinaryOutputStream output = new BinaryOutputStream(bytes);
		
		output.write_uv(stringCache.get(md.name()));
		output.write_uv(generateModifiers(md.modifiers()));
		output.write_uv(typeCache.get(md.type()));					
		output.write_uv(md.cases().size());

		for(WyilFile.Case c : md.cases()) {
			writeBlock(BLOCK_Case,c,output);		
		}
		
		// TODO: write annotations
		output.close();
		return bytes.toByteArray();
	}
	
	private byte[] generateMethodCaseBlock(WyilFile.Case c) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BinaryOutputStream output = new BinaryOutputStream(bytes);
		
		int n = 0;
		n += c.precondition() != null ? 1 : 0;
		n += c.postcondition() != null ? 1 : 0;
		n += c.body() != null ? 1 : 0;
		output.write_uv(n);
		if(c.precondition() != null) {			
			writeBlock(BLOCK_Precondition,c.precondition(),output);
		}
		if(c.postcondition() != null) {			
			writeBlock(BLOCK_Postcondition,c.postcondition(),output);			
		}
		if(c.body() != null) {
			writeBlock(BLOCK_Body,c.body(),output);			
		}
		// TODO: write annotations
		
		output.close();
		return bytes.toByteArray();
	}
	
	private byte[] generateCodeBlock(Block block) throws IOException {		
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();		
		BinaryOutputStream output = new BinaryOutputStream(bytes);
				
		HashMap<String,Integer> labels = new HashMap<String,Integer>();
		
		int nlabels = 0;
		int offset = 0;
		for(Block.Entry e : block) {
			Code code = e.code;
			if(code instanceof Code.Label) {
				Code.Label l = (Code.Label) code;
				labels.put(l.label, offset);
				nlabels++;
			} else {
				offset++;
			}
		}
		
		output.write_uv(block.size()-nlabels); // instruction count (not same as block size!)
		offset = 0;
		for(Block.Entry e : block) {		
			if(e.code instanceof Code.Label) {
				
			} else {
				writeCode(e.code, offset++, labels, output);
			}
		}
		
		output.close();
		return bytes.toByteArray();
	}
	
	private void writeCode(Code code, int offset,
			HashMap<String, Integer> labels, BinaryOutputStream output)
			throws IOException {

		// first determine whether we need some kind of wide instruction.
		int width = calculateWidth(code, offset, labels);

		switch (width) {
			case Code.OPCODE_wide :
				output.write_u8(width);
				writeBase(true, code, output);
				writeRest(false, code, offset, labels, output);
				break;
			case Code.OPCODE_widerest :
				output.write_u8(width);
				writeBase(false, code, output);
				writeRest(true, code, offset, labels, output);
				break;
			case Code.OPCODE_widewide :
				output.write_u8(width);
				writeBase(true, code, output);
				writeRest(true, code, offset, labels, output);
				break;
			default :
				writeBase(false, code, output);
				writeRest(false, code, offset, labels, output);
		}
	}
	
	/**
	 * Write the "base" part of a bytecode instruction. This includes only the
	 * opcode itself and the operands (if any).
	 * 
	 * @param wide
	 *            --- indicates whether we should be writing the base in "wide"
	 *            format. That is, using the unlimited representation of
	 *            integers. In the alternative "short" representation, all
	 *            operands are written using exactly 4 unsigned bits. This means
	 *            we can encode registers 0-15, which covers the majority of
	 *            cases. The wide format is then used for cases when we have to
	 *            write a register operand whose index is > 15.
	 * @param code
	 *            --- The bytecode to be written.
	 * @param offset
	 *            --- The current offset of this bytecode in the bytecode array
	 *            being generated. This offset is measured in complete
	 *            bytecodes, not in e.g. bytes. Therefore, the first bytecode
	 *            has offset zero, the second bytecode has offset 1, etc. The
	 *            offset is required
	 * @param output
	 *            --- The binary stream to write this bytecode to.
	 * @throws IOException
	 */
	private void writeBase(boolean wide, Code code, 
			BinaryOutputStream output) throws IOException {

		// second, deal with standard instruction formats
		output.write_u8(code.opcode());
		
		if(code instanceof Code.AbstractUnaryOp) {
			Code.AbstractUnaryOp<Type> a = (Code.AbstractUnaryOp) code;
			if(a.operand != Code.NULL_REG) { 				
				writeBase(wide,a.operand,output);
			} else {
				// possible only for empty return
			}			
		} else if(code instanceof Code.AbstractBinaryOp) {
			Code.AbstractBinaryOp<Type> a = (Code.AbstractBinaryOp) code;		
			writeBase(wide,a.leftOperand,output);
			writeBase(wide,a.rightOperand,output);
		} else if(code instanceof Code.AbstractUnaryAssignable) {
			Code.AbstractUnaryAssignable<Type> a = (Code.AbstractUnaryAssignable) code;
			writeBase(wide,a.target,output);
			writeBase(wide,a.operand,output);
		} else if(code instanceof Code.AbstractBinaryAssignable) {
			Code.AbstractBinaryAssignable<Type> a = (Code.AbstractBinaryAssignable) code;
			writeBase(wide, a.target,output);
			writeBase(wide, a.leftOperand,output);
			writeBase(wide, a.rightOperand,output);
		} else if(code instanceof Code.Lambda) {
			// Special case for lambda since their operands maybe NULL_REG.
			Code.AbstractNaryAssignable<Type> a = (Code.AbstractNaryAssignable) code;
			if(a.target != Code.NULL_REG) {
				writeBase(wide,a.target,output);
			}
			int[] operands = a.operands;			
			writeBase(wide,operands.length,output);
			for(int i=0;i!=operands.length;++i) {
				writeBase(wide,operands[i]+1,output);
			}
		} else if(code instanceof Code.AbstractNaryAssignable) {
			Code.AbstractNaryAssignable<Type> a = (Code.AbstractNaryAssignable) code;
			if(a.target != Code.NULL_REG) {
				writeBase(wide,a.target,output);
			}
			int[] operands = a.operands;			
			writeBase(wide,operands.length,output);
			for(int i=0;i!=operands.length;++i) {
				writeBase(wide,operands[i],output);
			}
		} else if(code instanceof Code.AbstractSplitNaryAssignable) {
			Code.AbstractSplitNaryAssignable<Type> a = (Code.AbstractSplitNaryAssignable) code;
			if(a.target != Code.NULL_REG) {
				writeBase(wide,a.target,output);
			}			
			int[] operands = a.operands;			
			writeBase(wide,operands.length+1,output);
			writeBase(wide,a.operand,output);
			for(int i=0;i!=operands.length;++i) {
				writeBase(wide,operands[i],output);
			}
		} else if(code instanceof Code.AbstractAssignable) {
			Code.AbstractAssignable c = (Code.AbstractAssignable) code;
			writeBase(wide,c.target,output);
		} else if(code instanceof Code.ForAll) {
			Code.ForAll l = (Code.ForAll) code;			
			int[] operands = l.modifiedOperands;	
			writeBase(wide,operands.length + 2,output);
			writeBase(wide,l.indexOperand,output);
			writeBase(wide,l.sourceOperand,output);			
			for(int i=0;i!=operands.length;++i) {
				writeBase(wide,operands[i],output);
			}
		} else if(code instanceof Code.Loop) {
			Code.Loop l = (Code.Loop) code;			
			int[] operands = l.modifiedOperands;	
			writeBase(wide,operands.length,output);
			for(int i=0;i!=operands.length;++i) {
				writeBase(wide,operands[i],output);
			}
		} else if(code instanceof Code.TryCatch) {
			Code.TryCatch tc = (Code.TryCatch) code;			
			writeBase(wide,tc.operand,output);
		} 
	}
	
	/**
	 * Write the "rest" of a bytecode instruction. This includes any additional
	 * information, such as the type and/or other pool items required for the
	 * bytecode.
	 * 
	 * @param wide
	 *            --- indicates whether we should be writing the rest in "wide"
	 *            format. That is, using the unlimited representation of
	 *            integers. In the alternative "short" representation, all pool
	 *            indices are written using exactly 8 unsigned bits. This means
	 *            we can encode pool indices 0-255, which covers a large number
	 *            of cases. The wide format is then used for cases when we have
	 *            to a pool index > 255.
	 * @param code
	 *            --- The bytecode to be written.
	 * @param offset
	 *            --- The current offset of this bytecode in the bytecode array
	 *            being generated. This offset is measured in complete
	 *            bytecodes, not in e.g. bytes. Therefore, the first bytecode
	 *            has offset zero, the second bytecode has offset 1, etc. The
	 *            offset is required for calculating jump targets for branching
	 *            instructions (e.g. goto). Since jump targets (in short form)
	 *            are encoded as a relative offset, we need to know our current
	 *            offset to compute the relative target.
	 * @param labels
	 *            --- A map from label to offset. This is required to determine
	 *            the (relative) jump offset for a branching instruction.
	 * @param output
	 *            --- The binary output stream to which this bytecode is being
	 *            written.
	 * @throws IOException
	 */
	private void writeRest(boolean wide, Code code, int offset,
			HashMap<String, Integer> labels, BinaryOutputStream output) throws IOException {
		
		if(code instanceof Code.AbstractUnaryOp) {
			Code.AbstractUnaryOp<Type> a = (Code.AbstractUnaryOp) code;
			if(a.operand != Code.NULL_REG) { 
				writeRest(wide,typeCache.get(a.type),output);
			} else {
				// possible only for empty return
			}
		} else if(code instanceof Code.AbstractBinaryOp) {
			Code.AbstractBinaryOp<Type> a = (Code.AbstractBinaryOp) code;
			writeRest(wide,typeCache.get(a.type),output);
		} else if(code instanceof Code.AbstractUnaryAssignable) {
			Code.AbstractUnaryAssignable<Type> a = (Code.AbstractUnaryAssignable) code;
			writeRest(wide,typeCache.get(a.type),output);
		} else if(code instanceof Code.AbstractBinaryAssignable) {
			Code.AbstractBinaryAssignable<Type> a = (Code.AbstractBinaryAssignable) code;
			writeRest(wide,typeCache.get(a.type),output);
		} else if(code instanceof Code.AbstractNaryAssignable) {
			Code.AbstractNaryAssignable<Type> a = (Code.AbstractNaryAssignable) code;
			writeRest(wide,typeCache.get(a.type),output);
		} else if(code instanceof Code.AbstractSplitNaryAssignable) {
			Code.AbstractSplitNaryAssignable<Type> a = (Code.AbstractSplitNaryAssignable) code;			
			writeRest(wide,typeCache.get(a.type),output);
		}	
		// now deal with non-uniform instructions
		// First, deal with special cases
		if(code instanceof Code.AssertOrAssume) {
			Code.AssertOrAssume c = (Code.AssertOrAssume) code;
			writeRest(wide,stringCache.get(c.msg),output);
		} else if(code instanceof Code.Const) {
			Code.Const c = (Code.Const) code;
			writeRest(wide,constantCache.get(c.constant),output);
		} else if(code instanceof Code.Convert) {
			Code.Convert c = (Code.Convert) code;
			writeRest(wide,typeCache.get(c.result),output);
		} else if(code instanceof Code.FieldLoad) {
			Code.FieldLoad c = (Code.FieldLoad) code;
			writeRest(wide,stringCache.get(c.field),output);			
		} else if(code instanceof Code.ForAll) {
			Code.ForAll f = (Code.ForAll) code;
			writeRest(wide,typeCache.get(f.type),output);
			int target = labels.get(f.target);
			writeTarget(wide,offset,target,output);
		} else if(code instanceof Code.IfIs) {
			Code.IfIs c = (Code.IfIs) code;
			int target = labels.get(c.target) - offset;			
			writeRest(wide,typeCache.get(c.rightOperand),output); 
			writeTarget(wide,offset,target,output);			
		} else if(code instanceof Code.If) {
			Code.If c = (Code.If) code;
			int target = labels.get(c.target);
			writeTarget(wide,offset,target,output);
		} else if(code instanceof Code.Goto) {
			Code.Goto c = (Code.Goto) code;
			int target = labels.get(c.target); 
			writeTarget(wide,offset,target,output);
		} else if(code instanceof Code.Invoke) {
			Code.Invoke c = (Code.Invoke) code;
			writeRest(wide,nameCache.get(c.name),output);			
		} else if(code instanceof Code.Lambda) {
			Code.Lambda c = (Code.Lambda) code;
			writeRest(wide,nameCache.get(c.name),output);			
		} else if(code instanceof Code.Loop) {
			Code.Loop l = (Code.Loop) code;
			int target = labels.get(l.target);
			writeTarget(wide,offset,target,output);
		} else if(code instanceof Code.Update) {
			Code.Update c = (Code.Update) code;
			List<String> fields = c.fields;
			writeRest(wide,typeCache.get(c.afterType),output);
			writeRest(wide,fields.size(),output);
			for (int i = 0; i != fields.size(); ++i) {
				writeRest(wide, stringCache.get(fields.get(i)), output);
			}
		} else if(code instanceof Code.Switch) {
			Code.Switch c = (Code.Switch) code;
			List<Pair<Constant,String>> branches = c.branches;
			int target = labels.get(c.defaultTarget) - offset; 			
			writeTarget(wide,offset,target,output);
			writeRest(wide,branches.size(),output);
			for(Pair<Constant,String> b : branches) {
				writeRest(wide,constantCache.get(b.first()),output);
				target = labels.get(b.second()); 
				writeTarget(wide,offset,target,output);
			}
		} else if(code instanceof Code.TryCatch) {
			Code.TryCatch tc = (Code.TryCatch) code;
			int target = labels.get(tc.target);
			ArrayList<Pair<Type,String>> catches = tc.catches;
			writeTarget(wide,offset,target,output);
			writeRest(wide,catches.size(),output);
			for (int i = 0; i != catches.size(); ++i) {
				Pair<Type, String> handler = catches.get(i);
				writeRest(wide, typeCache.get(handler.first()), output);
				writeTarget(wide, offset, labels.get(handler.second()), output);
			}
		} else if(code instanceof Code.TupleLoad) {
			Code.TupleLoad c = (Code.TupleLoad) code;			
			writeRest(wide,c.index,output);
		} 
	}
	
	private void writeBase(boolean wide, int value, BinaryOutputStream output) throws IOException {
		if(wide) {
			output.write_uv(value);
		} else {
			if(value >= 16) {
				throw new IllegalArgumentException("invalid base value");
			}
			output.write_un(value,4);
		}
	}
	
	private void writeRest(boolean wide, int value, BinaryOutputStream output) throws IOException {
		if(wide) {
			output.write_uv(value);
		} else {
			if(value >= 256) {
				throw new IllegalArgumentException("invalid base value");
			}
			output.write_u8(value);
		}
	}	
	
	private void writeTarget(boolean wide, int offset, int target,
			BinaryOutputStream output) throws IOException {
		if (wide) {
			output.write_uv(target);
		} else {
			target = (target - offset) + 128;
			output.write_u8(target);
		}
	}
	
	/**
	 * Calculate the "width" of a given bytecode. That is, whether or not either
	 * of the base or remainder components need to be encoded using the "wide"
	 * format. The wide format allows for unlimited precision, but occupies more
	 * space. The alternative "short" format uses fixed precision, but cannot
	 * encode all possible register operands and/or pool indices.
	 * 
	 * @param code
	 *            --- The bytecode whose width is to be determined.
	 * @param offset
	 *            --- The current offset of this bytecode in the bytecode array
	 *            being generated. This offset is measured in complete
	 *            bytecodes, not in e.g. bytes. Therefore, the first bytecode
	 *            has offset zero, the second bytecode has offset 1, etc. The
	 *            offset is required for calculating jump targets for branching
	 *            instructions (e.g. goto). Since jump targets (in short form)
	 *            are encoded as a relative offset, we need to know our current
	 *            offset to compute the relative target.
	 * @param labels
	 *            --- A map from label to offset. This is required to determine
	 *            the (relative) jump offset for a branching instruction.
	 * @return
	 */
	private int calculateWidth(Code code, int offset, HashMap<String,Integer> labels) {
		int maxBase = 0;
		int maxRest = 0;
		
		if(code instanceof Code.AbstractUnaryOp) {
			Code.AbstractUnaryOp<Type> a = (Code.AbstractUnaryOp) code;
			maxBase = a.operand;
			maxRest = typeCache.get(a.type);
		} else if(code instanceof Code.AbstractBinaryOp) {
			Code.AbstractBinaryOp<Type> a = (Code.AbstractBinaryOp) code;
			maxBase = Math.max(a.leftOperand,a.rightOperand);
			maxRest = typeCache.get(a.type);
		} else if(code instanceof Code.AbstractUnaryAssignable) {
			Code.AbstractUnaryAssignable<Type> a = (Code.AbstractUnaryAssignable) code;
			maxBase = Math.max(a.target,a.operand);
			maxRest = typeCache.get(a.type);
		} else if(code instanceof Code.AbstractBinaryAssignable) {
			Code.AbstractBinaryAssignable<Type> a = (Code.AbstractBinaryAssignable) code;
			maxBase = Math.max(a.leftOperand,a.rightOperand);
			maxBase = Math.max(a.target,maxBase);
			maxRest = typeCache.get(a.type);
		} else if(code instanceof Code.AbstractNaryAssignable) {
			Code.AbstractNaryAssignable<Type> a = (Code.AbstractNaryAssignable) code;
			int[] operands = a.operands;
			maxBase = Math.max(a.target,operands.length);
			for(int i=0;i!=operands.length;++i) {
				maxBase = Math.max(maxBase,operands[i]);
			}
			maxRest = typeCache.get(a.type);
		} else if(code instanceof Code.AbstractSplitNaryAssignable) {
			Code.AbstractSplitNaryAssignable<Type> a = (Code.AbstractSplitNaryAssignable) code;			
			int[] operands = a.operands;
			maxBase = Math.max(a.target,operands.length+1);
			maxBase = Math.max(maxBase,a.operand);
			for(int i=0;i!=operands.length;++i) {
				maxBase = Math.max(maxBase,operands[i]);
			}
			maxRest = typeCache.get(a.type);
		} else if(code instanceof Code.AbstractAssignable) {
			Code.AbstractAssignable a = (Code.AbstractAssignable) code;
			maxBase = a.target;			
		} 
		
		// now, deal with non-uniform opcodes
		if(code instanceof Code.AssertOrAssume) {
			Code.AssertOrAssume c = (Code.AssertOrAssume) code;
			maxRest = Math.max(maxRest,stringCache.get(c.msg));
		} else if(code instanceof Code.Const) {
			Code.Const c = (Code.Const) code;
			maxRest = Math.max(maxRest,constantCache.get(c.constant));
		} else if(code instanceof Code.Convert) {
			Code.Convert c = (Code.Convert) code;
			maxRest = Math.max(maxRest,typeCache.get(c.result));
		} else if(code instanceof Code.FieldLoad) {
			Code.FieldLoad c = (Code.FieldLoad) code;
			maxRest = Math.max(maxRest,stringCache.get(c.field));			
		} else if(code instanceof Code.ForAll) {
			Code.ForAll f = (Code.ForAll) code;
			int[] operands = f.modifiedOperands;
			maxBase = Math.max(f.sourceOperand, f.indexOperand);				
			for(int i=0;i!=operands.length;++i) {
				maxBase = Math.max(maxBase, operands[i]);
			}
			maxRest = Math.max(maxRest,typeCache.get(f.type));
			maxRest = Math.max(maxRest,targetWidth(f.target, offset, labels));
		} else if(code instanceof Code.IfIs) {
			Code.IfIs c = (Code.IfIs) code;
			maxRest = Math.max(maxRest,typeCache.get(c.rightOperand)); 
			maxRest = Math.max(maxRest,targetWidth(c.target, offset, labels));			
		} else if(code instanceof Code.If) {
			Code.If c = (Code.If) code;
			maxRest = Math.max(maxRest,targetWidth(c.target, offset, labels));
		} else if(code instanceof Code.Goto) {
			Code.Goto c = (Code.Goto) code;			
			maxRest = Math.max(maxRest,targetWidth(c.target, offset, labels));
		} else if(code instanceof Code.Invoke) {
			Code.Invoke c = (Code.Invoke) code;
			maxRest = Math.max(maxRest,nameCache.get(c.name));			
		} else if(code instanceof Code.Lambda) {
			Code.Lambda c = (Code.Lambda) code;
			maxRest = Math.max(maxRest,nameCache.get(c.name));			
		} else if(code instanceof Code.Loop) {
			Code.Loop l = (Code.Loop) code;
			int[] operands = l.modifiedOperands;
			maxBase = 0;				
			for(int i=0;i!=operands.length;++i) {
				maxBase = Math.max(maxBase, operands[i]);
			}
			maxRest = Math.max(maxRest,targetWidth(l.target, offset, labels));
		} else if(code instanceof Code.Update) {
			Code.Update c = (Code.Update) code;
			maxRest = Math.max(maxRest,typeCache.get(c.afterType));
			ArrayList<String> fields = c.fields; 
			for(int i=0;i!=fields.size();++i) {
				String field = fields.get(i);
				maxRest = Math.max(maxRest,stringCache.get(field));				
			}
		} else if(code instanceof Code.Switch) {
			Code.Switch c = (Code.Switch) code;
			List<Pair<Constant,String>> branches = c.branches;
			maxRest = Math.max(maxRest,targetWidth(c.defaultTarget, offset, labels));
			maxRest = Math.max(maxRest,branches.size());
			for(Pair<Constant,String> b : branches) {
				maxRest = Math.max(maxRest,constantCache.get(b.first()));
				maxRest = Math.max(maxRest,targetWidth(b.second(), offset, labels));
			}
		} else if(code instanceof Code.TryCatch) {
			Code.TryCatch tc = (Code.TryCatch) code;
			maxBase = tc.operand;			
			maxRest = Math.max(maxRest,
					targetWidth(tc.target, offset, labels));
			ArrayList<Pair<Type,String>> catches = tc.catches;
			maxRest = Math.max(maxRest,catches.size());
			for(int i=0;i!=catches.size();++i) {
				Pair<Type,String> handler = catches.get(i);				
				maxRest = Math.max(maxRest,typeCache.get(handler.first()));
				maxRest = Math.max(maxRest,
						targetWidth(handler.second(), offset, labels));
			}			
		} else if(code instanceof Code.TupleLoad) {
			Code.TupleLoad c = (Code.TupleLoad) code;			
			maxRest = Math.max(maxRest,c.index);
		} 
		
		if(maxBase < 16) {
			if(maxRest < 256) {
				return 0; // standard
			} else {
				return Code.OPCODE_widerest; 
			}
		} else {
			if(maxRest < 256) {
				return Code.OPCODE_wide; 
			} else {
				return Code.OPCODE_widewide; 
			}
		}
	}
	
	private int targetWidth(String label, int offset,
			HashMap<String, Integer> labels) {
		int target = labels.get(label) - offset;
		target = target + 128;
		if(target < 0) {
			// won't fit in a single byte
			return 256;
		} else {
			return target;
		}
	}
	
	private int generateModifiers(Collection<Modifier> modifiers) {
		int mods = 0;
		for(Modifier m : modifiers) {
			if(m == Modifier.PUBLIC) {
				mods |= MODIFIER_Public;
			} else if(m == Modifier.PROTECTED) {
				mods |= MODIFIER_Protected;
			} else if(m == Modifier.PRIVATE) {
				mods |= MODIFIER_Private;
			} else if(m == Modifier.NATIVE) {
				mods |= MODIFIER_Native;
			} else if(m == Modifier.EXPORT) {
				mods |= MODIFIER_Export;
			}
		}
		return mods;
	}
	
	private void buildPools(WyilFile module) {
		stringPool.clear();
		stringCache.clear();
		
		pathPool.clear();
		pathCache.clear();
		// preload the path root
		pathPool.add(null);
		pathCache.put(wyfs.util.Trie.ROOT,0);
		
		namePool.clear();
		nameCache.clear();
		
		constantPool.clear();
		constantCache.clear();
		
		typePool.clear();
		typeCache.clear();
		
		addPathItem(module.id());
		for(WyilFile.Declaration d : module.declarations()) {
			buildPools(d);
		}
	}
	
	private void buildPools(WyilFile.Declaration declaration) {
		if(declaration instanceof WyilFile.TypeDeclaration) {
			buildPools((WyilFile.TypeDeclaration)declaration);
		} else if(declaration instanceof WyilFile.ConstantDeclaration) {
			buildPools((WyilFile.ConstantDeclaration)declaration);
		} else if(declaration instanceof WyilFile.FunctionOrMethodDeclaration) {
			buildPools((WyilFile.FunctionOrMethodDeclaration)declaration);
		} 
	}
	
	private void buildPools(WyilFile.TypeDeclaration declaration) {
		addStringItem(declaration.name());
		addTypeItem(declaration.type());		
		buildPools(declaration.constraint());		
	}
	
	private void buildPools(WyilFile.ConstantDeclaration declaration) {
		addStringItem(declaration.name());
		addConstantItem(declaration.constant());
	}

	private void buildPools(WyilFile.FunctionOrMethodDeclaration declaration) {
		addStringItem(declaration.name());
		addTypeItem(declaration.type());
		for(WyilFile.Case c : declaration.cases()) {			
			buildPools(c.precondition());						
			buildPools(c.body());						
			buildPools(c.postcondition());			
		}
	}
	
	private void buildPools(Block block) {
		if(block == null) { return; }
		for(Block.Entry e : block) {
			buildPools(e.code);
		}
	}
	
	private void buildPools(Code code) {
		
		// First, deal with special cases
		if(code instanceof Code.AssertOrAssume) {
			Code.AssertOrAssume c = (Code.AssertOrAssume) code;
			addStringItem(c.msg);
		} else if(code instanceof Code.Const) {
			Code.Const c = (Code.Const) code;
			addConstantItem(c.constant);
		} else if(code instanceof Code.Convert) {
			Code.Convert c = (Code.Convert) code;
			addTypeItem(c.result);
		} else if(code instanceof Code.FieldLoad) {
			Code.FieldLoad c = (Code.FieldLoad) code;
			addStringItem(c.field);
		} else if(code instanceof Code.ForAll) {
			Code.ForAll s = (Code.ForAll) code;
			addTypeItem((Type)s.type);			
		}else if(code instanceof Code.IfIs) {
			Code.IfIs c = (Code.IfIs) code;
			addTypeItem(c.type);
			addTypeItem(c.rightOperand);
		} else if(code instanceof Code.Invoke) {
			Code.Invoke c = (Code.Invoke) code;
			addNameItem(c.name);			
		} else if(code instanceof Code.Lambda) {
			Code.Lambda c = (Code.Lambda) code;
			addNameItem(c.name);			
		} else if(code instanceof Code.Update) {
			Code.Update c = (Code.Update) code;
			addTypeItem(c.type);
			addTypeItem(c.afterType);
			for(Code.LVal l : c) {
				if(l instanceof Code.RecordLVal) {
					Code.RecordLVal lv = (Code.RecordLVal) l;
					addStringItem(lv.field);
				}
			}
		} else if(code instanceof Code.Switch) {
			Code.Switch s = (Code.Switch) code;
			addTypeItem(s.type);
			for(Pair<Constant,String> b : s.branches) {
				addConstantItem(b.first());
			}
		} else if(code instanceof Code.TryCatch) {
			Code.TryCatch tc = (Code.TryCatch) code;
			ArrayList<Pair<Type, String>> catches = tc.catches;
			for (int i = 0; i != catches.size(); ++i) {
				Pair<Type, String> handler = catches.get(i);
				addTypeItem(handler.first());
			}
		}
		
		// Second, deal with standard cases
		if(code instanceof Code.AbstractUnaryOp) {
			Code.AbstractUnaryOp<Type> a = (Code.AbstractUnaryOp) code;
			addTypeItem(a.type);
		} else if(code instanceof Code.AbstractBinaryOp) {
			Code.AbstractBinaryOp<Type> a = (Code.AbstractBinaryOp) code;
			addTypeItem(a.type);
		} else if(code instanceof Code.AbstractUnaryAssignable) {
			Code.AbstractUnaryAssignable<Type> a = (Code.AbstractUnaryAssignable) code;
			addTypeItem(a.type);
		} else if(code instanceof Code.AbstractBinaryAssignable) {
			Code.AbstractBinaryAssignable<Type> a = (Code.AbstractBinaryAssignable) code;
			addTypeItem(a.type);
		} else if(code instanceof Code.AbstractNaryAssignable) {
			Code.AbstractNaryAssignable<Type> a = (Code.AbstractNaryAssignable) code;
			addTypeItem(a.type);
		} else if(code instanceof Code.AbstractSplitNaryAssignable) {
			Code.AbstractSplitNaryAssignable<Type> a = (Code.AbstractSplitNaryAssignable) code;
			addTypeItem(a.type);
		}
	}
	
	private int addNameItem(NameID name) {
		Integer index = nameCache.get(name);
		if(index == null) {
			int i = namePool.size();
			nameCache.put(name, i);
			namePool.add(new NAME_Item(addPathItem(name.module()),
					addStringItem(name.name())));
			return i;			
		} else {
			return index;
		}
	}
	
	private int addStringItem(String string) {
		Integer index = stringCache.get(string);
		if(index == null) {
			int i = stringPool.size();
			stringCache.put(string, i);
			stringPool.add(string);
			return i;
		} else {
			return index;
		}
	}
	
	private int addPathItem(Path.ID pid) {
		Integer index = pathCache.get(pid);
		if(index == null) {
			int parent = addPathItem(pid.parent());
			int i = pathPool.size();
			pathPool.add(new PATH_Item(parent,addStringItem(pid.last())));
			pathCache.put(pid, i);
			return i;
		} else {
			return index;
		}
	}
	
	private int addTypeItem(Type t) {
		
		// TODO: this could be made way more efficient. In particular, we should
		// combine resources into a proper aliased pool rather than write out
		// types individually ... because that's sooooo inefficient!
		
		Integer index = typeCache.get(t);
		if(index == null) {
			int i = typePool.size();
			typeCache.put(t, i);
			typePool.add(t);			
			return i;
		} else {
			return index;
		}
	}
	
	private int addConstantItem(Constant v) {
	
		Integer index = constantCache.get(v);
		if(index == null) {
			// All subitems must have lower indices than the containing item.
			// So, we must add subitems first before attempting to allocate a
			// place for this value.   
			addConstantSubitems(v);
			
			// finally allocate space for this constant.
			int i = constantPool.size();
			constantCache.put(v, i);
			constantPool.add(v);			
			return i;
		}
		return index;
	}
	
	private void addConstantSubitems(Constant v) {
		if(v instanceof Constant.Strung) {
			Constant.Strung s = (Constant.Strung) v;
			addStringItem(s.value);
		} else if(v instanceof Constant.List) {
			Constant.List l = (Constant.List) v;				
			for (Constant e : l.values) {
				addConstantItem(e);
			}
		} else if(v instanceof Constant.Set) {
			Constant.Set s = (Constant.Set) v;
			for (Constant e : s.values) {
				addConstantItem(e);
			}
		} else if(v instanceof Constant.Map) {				
			Constant.Map m = (Constant.Map) v;
			for (Map.Entry<Constant,Constant> e : m.values.entrySet()) {
				addConstantItem(e.getKey());
				addConstantItem(e.getValue());
			}
		} else if(v instanceof Constant.Tuple) {
			Constant.Tuple t = (Constant.Tuple) v;
			for (Constant e : t.values) {
				addConstantItem(e);
			}
		} else if(v instanceof Constant.Record) {
			Constant.Record r = (Constant.Record) v;
			for (Map.Entry<String,Constant> e : r.values.entrySet()) {
				addStringItem(e.getKey());
				addConstantItem(e.getValue());
			}				
		} else if(v instanceof Constant.Lambda){
			Constant.Lambda fm = (Constant.Lambda) v;
			addTypeItem(fm.type());
			addNameItem(fm.name);
		} 
	} 
	
	/**
	 * An PATH_Item represents a path item.
	 * 
	 * @author David J. Pearce
	 *
	 */
	private class PATH_Item {
		/**
		 * The index in the path pool of the parent for this item, or -1 if it
		 * has not parent.
		 */
		public final int parentIndex;
		
		/**
		 * The index of this path component in the string pool
		 */
		public final int stringIndex;
		
		public PATH_Item(int parentIndex, int stringIndex) {
			this.parentIndex = parentIndex;
			this.stringIndex = stringIndex;
		}		
	}
	
	private enum NAME_Kind {
		PACKAGE(0), 
		MODULE(1), 
		CONSTANT(2), 
		TYPE(3), 
		FUNCTION(4), 
		METHOD(5);

		private final int kind;

		private NAME_Kind(int kind) {
			this.kind = kind;
		}

		public int kind() {
			return kind;
		}
	}
	
	/**
	 * A NAME_Item represents a named path item, such as a package, module or
	 * something within a module (e.g. a function or method declaration).
	 * 
	 * @author David J. Pearce
	 * 
	 */
	private class NAME_Item {
		/**
		 * The kind of name item this represents.
		 */
		// public final NAME_Kind kind;
		
		/**
		 * Index of path for this item in path pool
		 */
		public final int pathIndex;
		
		/**
		 * Index of string for this named item
		 */
		public final int nameIndex;
		
		public NAME_Item(/*NAME_Kind kind, */int pathIndex, int nameIndex) {
			//this.kind = kind;
			this.pathIndex = pathIndex;
			this.nameIndex = nameIndex;
		}				
	}		
		
	// =========================================================================
	// BLOCK identifiers
	// =========================================================================
	
	public final static int BLOCK_Header = 0;
	public final static int BLOCK_Module = 1;
	public final static int BLOCK_Documentation = 2;
	public final static int BLOCK_License = 3;
	// ... (anticipating some others here)
	public final static int BLOCK_Type = 10;
	public final static int BLOCK_Constant = 11;
	public final static int BLOCK_Function = 12;
	public final static int BLOCK_Method = 13;
	public final static int BLOCK_Case = 14;
	// ... (anticipating some others here)
	public final static int BLOCK_Body = 20;
	public final static int BLOCK_Precondition = 21;
	public final static int BLOCK_Postcondition = 22;
	public final static int BLOCK_Constraint = 23;
	// ... (anticipating some others here)
	
	// =========================================================================
	// CONSTANT identifiers
	// =========================================================================

	public final static int CONSTANT_Null = 0;
	public final static int CONSTANT_True = 1;
	public final static int CONSTANT_False = 2;
	public final static int CONSTANT_Byte = 3;
	public final static int CONSTANT_Char = 4;
	public final static int CONSTANT_Int = 5;
	public final static int CONSTANT_Real = 6;
	public final static int CONSTANT_Set = 7;	
	public final static int CONSTANT_String = 8;	
	public final static int CONSTANT_List = 9;
	public final static int CONSTANT_Record = 10;
	public final static int CONSTANT_Tuple = 11;
	public final static int CONSTANT_Map = 12;
	public final static int CONSTANT_Function = 13;
	public final static int CONSTANT_Method = 14;
	
	// =========================================================================
	// MODIFIER identifiers
	// =========================================================================

	public final static int MODIFIER_PROTECTION_MASK = 3;
	public final static int MODIFIER_Private = 0;
	public final static int MODIFIER_Protected = 1;
	public final static int MODIFIER_Public = 2;
	
	// public final static int MODIFIER_Package = 3;
	// public final static int MODIFIER_Module = 4;
	
	public final static int MODIFIER_MANGLE_MASK = 3 << 4;
	public final static int MODIFIER_Native = 1 << 4;
	public final static int MODIFIER_Export = 2 << 4;	
	//public final static int MODIFIER_Total = 3 << 4;		
}
