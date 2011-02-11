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
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import groovy.beans.Bindable;
import groovy.lang.Closure;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeSupport;

import static lombok.javac.handlers.JavacHandlerUtil.*;
import static org.codehaus.griffon.compiler.lombok.javac.AstBuilder.defMethod;
import static org.codehaus.griffon.compiler.lombok.javac.HandlerUtils.*;

/**
 * @author Andres Almiray
 */
public class HandleBindable implements JavacAnnotationHandler<Bindable> {
    private static final Logger LOG = LoggerFactory.getLogger(HandleBindable.class);
    private static final String FIELD_NAME = "this$propertyChangeSupport";

    public boolean handle(AnnotationValues<Bindable> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        markAnnotationAsProcessed(annotationNode, Bindable.class);

        JavacNode typeNode = annotationNode.up();
        TokenBuilder b = new TokenBuilder(typeNode);
        switch (typeNode.getKind()) {
            case TYPE:
                if ((((JCTree.JCClassDecl) typeNode.get()).mods.flags & Flags.INTERFACE) != 0) {
                    annotationNode.addError("@Bindable is legal only on classes.");
                    return true;
                }

                if (fieldExists(FIELD_NAME, typeNode) != MemberExistsResult.NOT_EXISTS) {
                    annotationNode.addWarning("Field '" + FIELD_NAME + "' already exists.");
                    return true;
                }

                addBindableSupportToClass(typeNode);

                for (JavacNode field : typeNode.down()) {
                    if (isCandidateField(field) && !hasAnnotation(field, Bindable.class)) {
                        createOrAdjustSetter(typeNode, field, b);
                    }
                }
                return true;
            case FIELD:
                if (isCandidateField(typeNode)) {
                    if (fieldExists(FIELD_NAME, typeNode.up()) == MemberExistsResult.NOT_EXISTS) {
                        addBindableSupportToClass(typeNode.up());
                    }
                    createOrAdjustSetter(typeNode.up(), typeNode, b);
                }
                return true;
            default:
                annotationNode.addError("@Bindable is legal only on types or fields.");
                return true;
        }
    }

    private void addBindableSupportToClass(JavacNode typeNode) {
        TokenBuilder b = new TokenBuilder(typeNode);

        JavacNode propertyChangeSupportField = createPropertyChangeSupportField(typeNode, b);
        injectAddPropertyChangeListenerMethod(typeNode, propertyChangeSupportField, b);
        injectRemovePropertyChangeListenerMethod(typeNode, propertyChangeSupportField, b);
        injectGetPropertyChangeListenerMethod(typeNode, propertyChangeSupportField, b);
        injectFirePropertyChangeMethod(typeNode, propertyChangeSupportField, b);

        if (LOG.isDebugEnabled()) LOG.debug("Modified " + typeNode.getName() + " as a Bindable class.");
    }

    private boolean isCandidateField(JavacNode node) {
        if (!isInstanceField(node)) return false;
        JCTree.JCVariableDecl field = (JCTree.JCVariableDecl) node.get();
        if(FIELD_NAME.equals(field.name.toString())) return false;
        if ((field.mods.flags & Flags.FINAL) != 0) return false;

        return (field.mods.flags & Flags.PRIVATE) == 0;
    }

    private void createOrAdjustSetter(JavacNode clazz, JavacNode field, TokenBuilder b) {

    }

    private void injectRemovePropertyChangeListenerMethod(JavacNode typeNode, JavacNode propertyChangeSupportField, TokenBuilder b) {
        injectListenerManagementMethod(typeNode, propertyChangeSupportField, "removePropertyChangeListener", b);
    }

    private void injectAddPropertyChangeListenerMethod(JavacNode typeNode, JavacNode propertyChangeSupportField, TokenBuilder b) {
        injectListenerManagementMethod(typeNode, propertyChangeSupportField, "addPropertyChangeListener", b);
    }

    private void injectGetPropertyChangeListenerMethod(JavacNode typeNode, JavacNode propertyChangeSupportField, TokenBuilder b) {
        injectListenerManagementMethod2(typeNode, propertyChangeSupportField, "getPropertyChangeListener", b);
    }

    private void injectFirePropertyChangeMethod(JavacNode typeNode, JavacNode propertyChangeSupportField, TokenBuilder b) {
        TreeMaker m = typeNode.getTreeMaker();

        ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<JCTree.JCVariableDecl>();
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();

        JCTree.JCVariableDecl param = b.param(0, String.class, "propertyName");
        params.append(param);
        args.append(m.Ident(param.getName()));
        param = b.param(0, Object.class, "oldValue");
        params.append(param);
        args.append(m.Ident(param.getName()));
        param = b.param(0, Object.class, "newValue");
        params.append(param);
        args.append(m.Ident(param.getName()));
        JCTree.JCStatement call = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(propertyChangeSupportField), b.name("firePropertyChange")), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, "firePropertyChange").withParams(params).withBody(call).$());
    }

    private void injectListenerManagementMethod(JavacNode typeNode, JavacNode propertyChangeSupportField, String methodName, TokenBuilder b) {
        TreeMaker m = typeNode.getTreeMaker();

        ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<JCTree.JCVariableDecl>();
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();

        JCTree.JCVariableDecl param = b.param(0, Object.class, "listener");
        params.append(param);
        args.append(m.Ident(param.getName()));
        JCTree.JCStatement listenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(propertyChangeSupportField), b.name(methodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(listenerCall).$());

        params = new ListBuffer<JCTree.JCVariableDecl>();
        args = new ListBuffer<JCTree.JCExpression>();
        param = b.param(0, String.class, "name");
        params.append(param);
        args.append(m.Ident(param.getName()));
        param = b.param(0, Closure.class, "listener");
        params.append(param);
        args.append(m.Ident(param.getName()));
        listenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(propertyChangeSupportField), b.name(methodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(listenerCall).$());
    }

    private void injectListenerManagementMethod2(JavacNode typeNode, JavacNode propertyChangeSupportField, String methodName, TokenBuilder b) {
        TreeMaker m = typeNode.getTreeMaker();

        ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<JCTree.JCVariableDecl>();
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();

        JCTree.JCStatement addListenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(propertyChangeSupportField), b.name(methodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(addListenerCall).$());

        params = new ListBuffer<JCTree.JCVariableDecl>();
        args = new ListBuffer<JCTree.JCExpression>();
        JCTree.JCVariableDecl param = b.param(0, String.class, "name");
        params.append(param);
        args.append(m.Ident(param.getName()));
        addListenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(propertyChangeSupportField), b.name(methodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(addListenerCall).$());
    }

    private JavacNode createPropertyChangeSupportField(JavacNode typeNode, TokenBuilder b) {
        TreeMaker maker = typeNode.getTreeMaker();

        JCTree.JCExpression thisRef = maker.Ident(typeNode.toName("this"));
        JCTree.JCExpression type = b.type(PropertyChangeSupport.class);
        JCTree.JCExpression instance = maker.NewClass(null, NIL_EXPRESSION, type, List.of(thisRef), null);

        JCTree.JCVariableDecl fieldDecl = maker.VarDef(
                b.mods(Flags.PRIVATE | Flags.FINAL),
                b.name(FIELD_NAME), type, instance);
        injectField(typeNode, fieldDecl);

        return getField(typeNode, FIELD_NAME);
    }
}
