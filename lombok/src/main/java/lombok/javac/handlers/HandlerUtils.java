/*
 * Copyright 2009-2011 the original author or authors.
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
 */

package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import griffon.util.GriffonClassUtils;
import lombok.core.AST;
import lombok.javac.Javac;
import lombok.javac.JavacNode;

import javax.lang.model.element.Modifier;
import java.util.Set;

import static lombok.javac.handlers.JCNoType.voidType;
import static lombok.javac.handlers.JavacHandlerUtil.chainDots;

/**
 * @author Andres Almiray
 */
public class HandlerUtils {
    public static final List<JCTree.JCExpression> NIL_EXPRESSION = List.<JCTree.JCExpression>nil();

    public static JavacNode findTypeNodeFrom(JavacNode node) {
        JavacNode n = node;
        while (n != null && !isTypeDeclaration(n)) n = n.up();
        if (!isTypeDeclaration(n)) return null;
        return n;
    }

    public static boolean isTypeDeclaration(JavacNode node) {
        return node != null && node.get() instanceof JCTree.JCClassDecl;
    }

    public static JCTree.JCExpression thisExpression(JavacNode typeNode) {
        return chainDots(typeNode.getTreeMaker(), typeNode, "this");
    }

    public static List<JCTree.JCExpression> extractArgNames(List<JCTree.JCVariableDecl> params, TreeMaker m) {
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();
        for (JCTree.JCVariableDecl param : params) {
            args.append(m.Ident(param.getName()));
        }
        return toList(args);
    }

    public static GriffonClassUtils.MethodDescriptor methodDescriptorFor(JCTree.JCMethodDecl method) {
        java.util.List<JCTree.JCVariableDecl> parameters = method.getParameters();
        String[] parameterTypes = new String[parameters.size()];

        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = parameters.get(i).getType().type.toString();
        }

        int modifiers = toJavacModifier(method.getModifiers().getFlags());

        return new GriffonClassUtils.MethodDescriptor(method.getName().toString(), parameterTypes, modifiers);
    }

    public static boolean hasAnnotation(JavacNode node, Class annotationClass) {
        for (JavacNode child : node.down()) {
            if (child.getKind() == AST.Kind.ANNOTATION) {
                if (Javac.annotationTypeMatches(annotationClass, child)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInstanceField(JavacNode field) {
        if (field.getKind() != AST.Kind.FIELD) return false;
        JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) field.get();
        //Skip fields that start with $
        if (fieldDecl.name.toString().startsWith("$")) return false;
        //Skip static fields.
        if ((fieldDecl.mods.flags & Flags.STATIC) != 0) return false;
        return true;
    }

    public static JavacNode findMethod(String methodName, JavacNode node) {
        while (node != null && !(node.get() instanceof JCTree.JCClassDecl)) {
            node = node.up();
        }

        if (node != null && node.get() instanceof JCTree.JCClassDecl) {
            for (JCTree def : ((JCTree.JCClassDecl) node.get()).defs) {
                if (def instanceof JCTree.JCMethodDecl) {
                    String name = ((JCTree.JCMethodDecl) def).name.toString();
                    if (name.equals(methodName)) {
                        return node.getNodeFor(def);
                    }
                }
            }
        }

        return null;
    }

    public static int toJavacModifier(JCTree.JCModifiers modifiers) {
        return toJavacModifier(modifiers.getFlags());
    }

    public static int toJavacModifier(Set<Modifier> modifiers) {
        int mods = 0;

        for (Modifier mod : modifiers) {
            switch (mod) {
                case PUBLIC:
                    mods |= java.lang.reflect.Modifier.PUBLIC;
                    break;
                case PROTECTED:
                    mods |= java.lang.reflect.Modifier.PROTECTED;
                    break;
                case PRIVATE:
                    mods |= java.lang.reflect.Modifier.PRIVATE;
                    break;
                case STATIC:
                    mods |= java.lang.reflect.Modifier.STATIC;
                    break;
                case ABSTRACT:
                    mods |= java.lang.reflect.Modifier.ABSTRACT;
                    break;
                case FINAL:
                    mods |= java.lang.reflect.Modifier.FINAL;
                    break;
                case NATIVE:
                    mods |= java.lang.reflect.Modifier.NATIVE;
                    break;
                case SYNCHRONIZED:
                    mods |= java.lang.reflect.Modifier.SYNCHRONIZED;
                    break;
                case TRANSIENT:
                    mods |= java.lang.reflect.Modifier.TRANSIENT;
                    break;
                case VOLATILE:
                    mods |= java.lang.reflect.Modifier.VOLATILE;
                    break;
                case STRICTFP:
                    mods |= java.lang.reflect.Modifier.STRICT;
                    break;
            }
        }

        return mods;
    }

    public static JavacNode getField(JavacNode node, String fieldName) {
        while (node != null && !(node.get() instanceof JCTree.JCClassDecl)) {
            node = node.up();
        }

        if (node != null && node.get() instanceof JCTree.JCClassDecl) {
            for (JCTree def : ((JCTree.JCClassDecl) node.get()).defs) {
                if (def instanceof JCTree.JCVariableDecl) {
                    if (((JCTree.JCVariableDecl) def).name.contentEquals(fieldName)) {
                        return node.getNodeFor(def);
                    }
                }
            }
        }

        return null;
    }

    public static JCTree.JCExpression readField(JavacNode fieldNode) {
        return readField(fieldNode, null);
    }

    public static JCTree.JCExpression readField(JavacNode fieldNode, JCTree.JCExpression receiver) {
        TreeMaker maker = fieldNode.getTreeMaker();

        JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) fieldNode.get();

        if (receiver == null) {
            if ((fieldDecl.mods.flags & Flags.STATIC) == 0) {
                receiver = maker.Ident(fieldNode.toName("this"));
            } else {
                JavacNode containerNode = fieldNode.up();
                if (containerNode != null && containerNode.get() instanceof JCTree.JCClassDecl) {
                    JCTree.JCClassDecl container = (JCTree.JCClassDecl) fieldNode.up().get();
                    receiver = maker.Ident(container.name);
                }
            }
        }

        return receiver == null ? maker.Ident(fieldDecl.name) : maker.Select(receiver, fieldDecl.name);
    }

    // -- COPIED FROM LOMBOK !!!
    // -- REMOVE ONCE THESE METHODS BECOME AVAILABLE IN THE NEXT RELEASE

    public static <T> com.sun.tools.javac.util.List<T> toList(ListBuffer<T> collection) {
        return collection == null ? com.sun.tools.javac.util.List.<T>nil() : collection.toList();
    }

    /**
     * In javac, dotted access of any kind, from {@code java.lang.String} to {@code var.methodName}
     * is represented by a fold-left of {@code Select} nodes with the leftmost string represented by
     * a {@code Ident} node. This method generates such an expression.
     * <p/>
     * For example, maker.Select(maker.Select(maker.Ident(NAME[java]), NAME[lang]), NAME[String]).
     *
     * @see com.sun.tools.javac.tree.JCTree.JCIdent
     * @see com.sun.tools.javac.tree.JCTree.JCFieldAccess
     */
    public static JCTree.JCExpression chainDotsString(TreeMaker maker, JavacNode node, String elems) {
        return chainDots(maker, node, elems.split("\\."));
    }

    public static class TokenBuilder {
        public final JavacNode context;

        public TokenBuilder(JavacNode context) {
            this.context = context;
        }

        public Name name(String name) {
            return context.toName(name);
        }

        public JCTree.JCModifiers mods(int mods) {
            return context.getTreeMaker().Modifiers(mods);
        }

        public JCTree.JCExpression type(Class clazz) {
            return type(clazz.getName());
        }

        public JCTree.JCExpression type(String type) {
            return chainDotsString(context.getTreeMaker(), context, type);
        }

        public JCTree.JCExpression void_t() {
            return context.getTreeMaker().Type(voidType());
        }

        public JCTree.JCVariableDecl param(int modifiers, Class clazz, String identifier) {
            return param(modifiers, clazz.getName(), identifier);
        }

        public JCTree.JCVariableDecl param(int modifiers, String clazz, String identifier) {
            return context.getTreeMaker().VarDef(mods(modifiers), name(identifier), type(clazz), null);
        }

        public JCTree.JCExpression dotExpr(String expr) {
            return chainDotsString(context.getTreeMaker(), context, expr);
        }

        public JCTree.JCMethodInvocation call(JCTree.JCExpression method) {
            return call(NIL_EXPRESSION, method, NIL_EXPRESSION);
        }

        public JCTree.JCMethodInvocation call(JCTree.JCExpression method, List<JCTree.JCExpression> args) {
            return call(NIL_EXPRESSION, method, args);
        }

        public JCTree.JCMethodInvocation call(List<JCTree.JCExpression> typeArgs, JCTree.JCExpression method, List<JCTree.JCExpression> args) {
            return context.getTreeMaker().Apply(typeArgs, method, args);
        }
    }
}
