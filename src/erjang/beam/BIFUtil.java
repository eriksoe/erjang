/**
 * This file is part of Erjang - A JVM-based Erlang VM
 *
 * Copyright (c) 2009 by Trifork
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package erjang.beam;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.Type;

import erjang.EAtom;
import erjang.EObject;
import erjang.modules.BinOps;
import erjang.modules.erlang;

/**
 * Used by the compiler to find and mange BIF definitions.
 * 
 * To add bifs, add the classes to the first "static" block below.
 * 
 * @author krab
 */
public class BIFUtil {

	static Map<String, BIFHandler> bifs = new HashMap<String, BIFHandler>();
	static Map<String, BIFHandler> guard_bifs = new HashMap<String, BIFHandler>();

	static {
		registerBifs(erlang.class);
		registerBifs(BinOps.class);
	}

	static class Args {
		private static final Type EOBJECT_TYPE = null;
		Class[] args;
		private Args generic;

		Args(Type[] types) {
			args = new Class[types.length];
			for (int i = 0; i < types.length; i++) {
				if (types[i] == Type.DOUBLE_TYPE) {
					args[i] = double.class;
					continue;
				}
				if (types[i] == Type.INT_TYPE) {
					args[i] = int.class;
					continue;
				}
				try {
					args[i] = Class.forName(types[i].getClassName());
				} catch (ClassNotFoundException e) {
					throw new Error(e);
				}
			}
		}

		public Args(Class[] a) {
			this.args = a;
		}

		@Override
		public int hashCode() {
			int code = 0;
			for (Class c : args) {
				code += c.hashCode();
			}
			return code;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Args) {
				Args other = (Args) obj;
				if (other.args.length == args.length) {

					for (int i = 0; i < args.length; i++) {
						if (!args[i].equals(other.args[i])) {
							return false;
						}
					}

					return true;
				}
			}
			return false;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("(");
			boolean first = true;
			for (Class c : args) {
				if (!first)
					sb.append(",");
				else
					first = false;
				sb.append(c.getName());
			}
			sb.append(")");
			return sb.toString();
		}

		public Args generic() {

			if (generic == null) {
				Class[] a = new Class[args.length];
				for (int i = 0; i < args.length; i++) {
					a[i] = EObject.class;
				}
				generic = new Args(a);
			}

			return generic;
		}
	}

	static class BIFHandler {

		Map<Args, BIF> found = new HashMap<Args, BIF>();

		private final String name;
		private final String javaName;

		public BIFHandler(String name) {
			this.name = name;
			this.javaName = name;
		}

		public BIFHandler(String name, String javaName) {
			this.name = name;
			this.javaName = javaName;
		}

		public Type getResult(Type[] parmTypes) {
			Args args = new Args(parmTypes);
			BIF m = found.get(args);
			if (m == null) {

				if ((m = found.get(args.generic())) == null) {
					throw new Error("no bif erlang:" + EAtom.intern(name) + "/"
							+ parmTypes.length + " " + args);
				}

				System.err.println("missed opportunity erlang:"
						+ EAtom.intern(name) + "/" + parmTypes.length + " "
						+ args + ", \n\tusing " + m);
			}

			return m.getReturnType();
		}

		public void registerMethod(Method method) {
			Args a = new Args(method.getParameterTypes());

			found.put(a, new BIF(method));
		}

		public void registerMethod(BIF method) {
			Args a = new Args(method.getArgumentTypes());

			found.put(a, method);
		}

		/**
		 * @param parmTypes
		 * @return
		 */
		public BIF getMethod(Type[] parmTypes) {

			Args args = new Args(parmTypes);
			BIF m = found.get(args);
			if (m == null) {

				if ((m = found.get(args.generic())) == null) {
					throw new Error("no bif erlang:" + EAtom.intern(name) + "/"
							+ parmTypes.length + " " + args);
				}

			}

			return m;

		}

	}

	public static Type getBifResult(String name, Type[] parmTypes,
			boolean isGuard) {

		Map<String, BIFHandler> tab = isGuard ? guard_bifs : bifs;

		BIFHandler bif = null;
		if (tab.containsKey(name)) {
			bif = tab.get(name);
		} else {
			throw new Error("no " + (isGuard ? "guard" : "normal")
					+ " bif named '" + name + "'");
		}

		return bif.getResult(parmTypes);
	}

	public static void registerBifs(Class<?> clazz) {
		Method[] m = clazz.getMethods();
		for (int i = 0; i < m.length; i++) {
			Method method = m[i];
			erjang.BIF ann = method.getAnnotation(erjang.BIF.class);
			if (ann != null) {
				Map<String, BIFHandler> tab = ann.type() == erjang.BIF.Type.GUARD ? guard_bifs
						: bifs;

				String bifName = ann.name();
				if (bifName.equals("__SELFNAME__")) {
					bifName = method.getName();
				}
				BIFHandler h = tab.get(bifName);
				if (h == null) {
					tab.put(bifName, h = new BIFHandler(bifName));
				}

				h.registerMethod(method);
			}
		}
	}

	/**
	 * @param name
	 * @param parmTypes
	 * @param b
	 * @return
	 */
	public static BIF getMethod(String name, Type[] parmTypes,
			boolean isGuard) {

		Map<String, BIFHandler> tab = isGuard ? guard_bifs : bifs;

		BIFHandler bif = null;
		if (tab.containsKey(name)) {
			bif = tab.get(name);
		} else {
			throw new Error("no " + (isGuard ? "guard" : "normal")
					+ " bif named '" + name + "'/" + parmTypes.length);
		}

		return bif.getMethod(parmTypes);
	}

	/**
	 * @param name
	 * @param args
	 * @param isGuard
	 * @return
	 */
	public static BIF getMethod(String name, Arg[] args, boolean isGuard) {
		Type[] parms = new Type[args.length];
		for (int i = 0; i < args.length; i++) { parms[i] = args[i].type; }
		return getMethod(name, parms, isGuard);
	}

}
