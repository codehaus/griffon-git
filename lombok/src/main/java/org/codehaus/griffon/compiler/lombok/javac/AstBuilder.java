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

package org.codehaus.griffon.compiler.lombok.javac;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import lombok.javac.JavacNode;

/**
 * @author Andres Almiray
 */
public class AstBuilder {
    public static ClassDefBuilder defClass(JavacNode context, String className) {
        return new ClassDefBuilder(context, className);
    }

    public static MethodDefBuilder defMethod(JavacNode context, String methodName) {
        return new MethodDefBuilder(context, methodName);
    }

    public static VariableDefBuilder defVar(JavacNode context, String variableName) {
        return new VariableDefBuilder(context, variableName);
    }

    public static class ClassDefBuilder {
        private final JavacNode context;
        private final String className;

        private JCTree.JCModifiers modifiers;
        private List<JCTree.JCTypeParameter> typeParameters;
        private JCTree superClass;
        private List<JCTree.JCExpression> interfaces;
        private List<JCTree> members;

        public ClassDefBuilder(JavacNode context, String className) {
            this.context = context;
            this.className = className;

            modifiers = context.getTreeMaker().Modifiers(Flags.PUBLIC);
            typeParameters = List.nil();
            interfaces = List.nil();
            members = List.nil();
        }

        public ClassDefBuilder modifiers(long mods) {
            modifiers = context.getTreeMaker().Modifiers(mods);
            return this;
        }

        public ClassDefBuilder extending(JCTree superClass) {
            this.superClass = superClass;
            return this;
        }

        public ClassDefBuilder implementing(Class... interfaces) {
            ListBuffer<JCTree.JCExpression> types = new ListBuffer<JCTree.JCExpression>();
            for (Class type : interfaces) {
                types.append(HandlerUtils.chainDotsString(context.getTreeMaker(), context, type.getName()));
            }
            return implementing(HandlerUtils.toList(types));
        }

        public ClassDefBuilder implementing(String... interfaces) {
            ListBuffer<JCTree.JCExpression> types = new ListBuffer<JCTree.JCExpression>();
            for (String type : interfaces) {
                types.append(HandlerUtils.chainDotsString(context.getTreeMaker(), context, type));
            }
            return implementing(HandlerUtils.toList(types));
        }

        public ClassDefBuilder implementing(List<JCTree.JCExpression> interfaces) {
            this.interfaces = interfaces;
            return this;
        }

        public ClassDefBuilder typeParams(List<JCTree.JCTypeParameter> typeParameters) {
            this.typeParameters = typeParameters;
            return this;
        }

        public ClassDefBuilder withMembers(List<JCTree> members) {
            this.members = members;
            return this;
        }

        public ClassDefBuilder withMembers(JCTree... members) {
            ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
            for (JCTree member : members) {
                defs.append(member);
            }
            return withMembers(HandlerUtils.toList(defs));
        }

        public JCTree.JCClassDecl $() {
            return build();
        }

        public JCTree.JCClassDecl build() {
            return context.getTreeMaker().ClassDef(modifiers, context.toName(className), typeParameters, superClass, interfaces, members);
        }
    }

    public static class MethodDefBuilder {
        private final JavacNode context;
        private final String methodName;

        private JCTree.JCModifiers modifiers;
        private JCTree.JCExpression returnType;
        private List<JCTree.JCTypeParameter> typeParameters;
        private List<JCTree.JCVariableDecl> params;
        private List<JCTree.JCExpression> throwables;
        private JCTree.JCBlock body;

        public MethodDefBuilder(JavacNode context, String methodName) {
            this.context = context;
            this.methodName = methodName;

            TreeMaker m = context.getTreeMaker();
            modifiers = m.Modifiers(Flags.PUBLIC);
            returnType = m.Type(new HandlerUtils.JCNoType(HandlerUtils.getCTCint(TypeTags.class, "VOID")));
            typeParameters = List.nil();
            params = List.nil();
            throwables = List.nil();
            body = m.Block(0, List.<JCTree.JCStatement>nil());
        }

        public MethodDefBuilder modifiers(long mods) {
            modifiers = context.getTreeMaker().Modifiers(mods);
            return this;
        }

        public MethodDefBuilder returning(Class clazz) {
            return returning(clazz.getName());
        }

        public MethodDefBuilder returning(String className) {
            returnType = HandlerUtils.chainDotsString(context.getTreeMaker(), context, className);
            return this;
        }

        public MethodDefBuilder typeParams(List<JCTree.JCTypeParameter> typeParameters) {
            this.typeParameters = typeParameters;
            return this;
        }

        public MethodDefBuilder withParams(ListBuffer<JCTree.JCVariableDecl> params) {
            return withParams(HandlerUtils.toList(params));
        }

        public MethodDefBuilder withParams(List<JCTree.JCVariableDecl> params) {
            this.params = params;
            return this;
        }

        public MethodDefBuilder throwing(List<JCTree.JCExpression> throwables) {
            this.throwables = throwables;
            return this;
        }

        public MethodDefBuilder withBody(JCTree.JCBlock body) {
            this.body = body;
            return this;
        }

        public MethodDefBuilder withBody(List<JCTree.JCStatement> statements) {
            return withBody(context.getTreeMaker().Block(0, statements));
        }

        public MethodDefBuilder withBody(JCTree.JCStatement statement) {
            return withBody(List.of(statement));
        }

        public JCTree.JCMethodDecl $() {
            return build();
        }

        public JCTree.JCMethodDecl build() {
            return context.getTreeMaker().MethodDef(modifiers, context.toName(methodName), returnType, typeParameters, params, throwables, body, null);
        }
    }

    public static class VariableDefBuilder {
        private final JavacNode context;
        private final String variableName;

        private JCTree.JCModifiers modifiers;
        private JCTree.JCExpression varType;
        private JCTree.JCExpression value;

        public VariableDefBuilder(JavacNode context, String variableName) {
            this.context = context;
            this.variableName = variableName;

            TreeMaker m = context.getTreeMaker();
            modifiers = m.Modifiers(Flags.PUBLIC);
            varType = HandlerUtils.chainDotsString(context.getTreeMaker(), context, Object.class.getName());
        }

        public VariableDefBuilder modifiers(long mods) {
            modifiers = context.getTreeMaker().Modifiers(mods);
            return this;
        }

        public VariableDefBuilder type(Class clazz) {
            return type(clazz.getName());
        }

        public VariableDefBuilder type(String className) {
            varType = HandlerUtils.chainDotsString(context.getTreeMaker(), context, className);
            return this;
        }

        public VariableDefBuilder withValue(JCTree.JCExpression value) {
            this.value = value;
            return this;
        }

        public JCTree.JCVariableDecl $() {
            return build();
        }

        public JCTree.JCVariableDecl build() {
            return context.getTreeMaker().VarDef(modifiers, context.toName(variableName), varType, value);
        }
    }
}
