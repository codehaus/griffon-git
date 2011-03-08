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
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import groovy.beans.Bindable;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.lang.reflect.Modifier;

import static com.sun.tools.javac.code.Flags.*;
import static lombok.core.handlers.TransformationsUtil.toSetterName;
import static lombok.core.util.Names.nameOfConstantBasedOnProperty;
import static lombok.javac.handlers.AstBuilder.defMethod;
import static lombok.javac.handlers.AstBuilder.defVar;
import static lombok.javac.handlers.HandlerUtils.*;
import static lombok.javac.handlers.HandlerUtils.toJavacModifier;
import static lombok.javac.handlers.JavacHandlerUtil.*;
import static lombok.javac.handlers.Lombok.newFieldAccessor;
import static lombok.javac.handlers.MemberChecks.fieldAlreadyExists;
import static lombok.javac.handlers.MemberChecks.methodAlreadyExists;

/**
 * @author Andres Almiray
 */
public class HandleBindable implements JavacAnnotationHandler<Bindable> {
    private static final Logger LOG = LoggerFactory.getLogger(HandleBindable.class);
    private static final String PROPERTY_SUPPORT_FIELD_NAME = "this$propertyChangeSupport";
    private static final String NAME_PARAM = "name";
    private static final String LISTENER_PARAM = "listener";
    private static final String PROPERTY_NAME_PARAM = "propertyName";
    private static final String OLD_VALUE_PARAM = "oldValue";
    private static final String NEW_VALUE_PARAM = "newValue";

    public boolean handle(AnnotationValues<Bindable> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        markAnnotationAsProcessed(annotationNode, Bindable.class);

        JavacNode typeNode = annotationNode.up();
        switch (typeNode.getKind()) {
            case TYPE:
                if ((((JCTree.JCClassDecl) typeNode.get()).mods.flags & Flags.INTERFACE) != 0) {
                    annotationNode.addError("@Bindable is legal only on classes.");
                    return true;
                }

                if (fieldExists(PROPERTY_SUPPORT_FIELD_NAME, typeNode) != MemberExistsResult.NOT_EXISTS) {
                    annotationNode.addWarning("Field '" + PROPERTY_SUPPORT_FIELD_NAME + "' already exists.");
                    return true;
                }

                addBindableSupportToClass(typeNode);

                for (JavacNode field : typeNode.down()) {
                    if (isCandidateField(field) && !hasAnnotation(field, Bindable.class)) {
                        createOrAdjustProperty(typeNode, field);
                    }
                }
                return true;
            case FIELD:
                if (isCandidateField(typeNode)) {
                    if (fieldExists(PROPERTY_SUPPORT_FIELD_NAME, typeNode.up()) == MemberExistsResult.NOT_EXISTS) {
                        addBindableSupportToClass(findTypeNodeFrom(typeNode));
                    }
                    createOrAdjustProperty(typeNode.up(), typeNode);
                }
                return true;
            default:
                annotationNode.addError("@Bindable is legal only on types or fields.");
                return true;
        }
    }

    private void addBindableSupportToClass(JavacNode typeNode) {
        createPropertyChangeSupportField(typeNode);
        injectListenerManagementMethod("addPropertyChangeListener", typeNode);
        injectListenerManagementMethod("removePropertyChangeListener", typeNode);
        //injectListenerQueryMethod(typeNode);
        injectFirePropertyChangeMethod(typeNode);

        if (LOG.isDebugEnabled()) LOG.debug("Modified " + typeNode.getName() + " as a Bindable class.");
    }

    private boolean isCandidateField(JavacNode node) {
        if (!isInstanceField(node)) return false;
        JCTree.JCVariableDecl field = (JCTree.JCVariableDecl) node.get();
        if (PROPERTY_SUPPORT_FIELD_NAME.equals(field.name.toString())) return false;
        if ((field.mods.flags & Flags.FINAL) != 0) return false;

        return Modifier.isPrivate(toJavacModifier(field.mods));
    }

    private void createOrAdjustProperty(JavacNode typeNode, JavacNode field) {
        String propertyNameFieldName = nameOfConstantBasedOnProperty(field.getName());
        generatePropertyNameConstant(propertyNameFieldName, field, typeNode);
        generateSetter(propertyNameFieldName, field);
    }

    private void injectFirePropertyChangeMethod(JavacNode typeNode) {
        String methodName = "firePropertyChange";
        TreeMaker treeMaker = typeNode.getTreeMaker();
        List<JCTree.JCVariableDecl> params = List.of(
                defVar(PROPERTY_NAME_PARAM)
                        .modifiers(FINAL)
                        .type(String.class)
                        .$(typeNode),
                defVar(OLD_VALUE_PARAM)
                        .modifiers(FINAL)
                        .type(Object.class)
                        .$(typeNode),
                defVar(NEW_VALUE_PARAM)
                        .modifiers(FINAL)
                        .type(Object.class)
                        .$(typeNode));
        List<JCTree.JCExpression> args = extractArgNames(params, treeMaker);

        injectMethod(typeNode, defMethod(methodName)
                .modifiers(PROTECTED)
                .withParams(params)
                .withBody(body(methodName, args, typeNode))
                .$(typeNode));
    }

    private void injectListenerQueryMethod(JavacNode typeNode) {
        String methodName = "getPropertyChangeListeners";
        TreeMaker treeMaker = typeNode.getTreeMaker();

        List<JCTree.JCVariableDecl> params = List.nil();
        List<JCTree.JCExpression> args = List.nil();
        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .returning(PropertyChangeListener[].class)
                .withBody(bodyWithReturn(methodName, args, typeNode))
                .$(typeNode));

        params = List.of(
                defVar(NAME_PARAM)
                        .modifiers(FINAL)
                        .type(String.class)
                        .$(typeNode));
        args = extractArgNames(params, treeMaker);

        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .returning(PropertyChangeListener[].class)
                .withBody(bodyWithReturn(methodName, args, typeNode))
                .$(typeNode));
    }

    private void injectListenerManagementMethod(String methodName, JavacNode typeNode) {
        TreeMaker treeMaker = typeNode.getTreeMaker();

        List<JCTree.JCVariableDecl> params = List.of(
                defVar(LISTENER_PARAM)
                        .modifiers(FINAL)
                        .type(PropertyChangeListener.class)
                        .$(typeNode));
        List<JCTree.JCExpression> args = extractArgNames(params, treeMaker);
        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .withBody(body(methodName, args, typeNode))
                .$(typeNode));

        params = List.of(
                defVar(NAME_PARAM)
                        .modifiers(FINAL)
                        .type(String.class)
                        .$(typeNode),
                defVar(LISTENER_PARAM)
                        .modifiers(FINAL)
                        .type(PropertyChangeListener.class)
                        .$(typeNode));
        args = extractArgNames(params, treeMaker);

        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .withBody(body(methodName, args, typeNode))
                .$(typeNode));
    }

    private List<JCStatement> body(String methodName, List<JCTree.JCExpression> args, JavacNode typeNode) {
        TreeMaker treeMaker = typeNode.getTreeMaker();
        JCExpression delegateToPropertySupport = delegateToPropertySupport(methodName, args, typeNode);
        return List.<JCStatement>of(treeMaker.Exec(delegateToPropertySupport));
    }

    private List<JCStatement> bodyWithReturn(String methodName, List<JCTree.JCExpression> args, JavacNode typeNode) {
        TreeMaker treeMaker = typeNode.getTreeMaker();
        JCExpression delegateToPropertySupport = delegateToPropertySupport(methodName, args, typeNode);
        return List.<JCStatement>of(treeMaker.Return(delegateToPropertySupport));
    }

    private JCExpression delegateToPropertySupport(String methodName, List<JCTree.JCExpression> args, JavacNode typeNode) {
        TreeMaker treeMaker = typeNode.getTreeMaker();
        JCExpression fn = chainDots(treeMaker, typeNode, PROPERTY_SUPPORT_FIELD_NAME, methodName);
        return treeMaker.Apply(List.<JCExpression>nil(), fn, args);
    }

    private void createPropertyChangeSupportField(JavacNode typeNode) {
        injectField(typeNode, defVar(PROPERTY_SUPPORT_FIELD_NAME)
                .modifiers(PRIVATE | FINAL)
                .type(PropertyChangeSupport.class)
                .withArgs(thisExpression(typeNode))
                .$(typeNode));
    }

    private void generatePropertyNameConstant(String propertyNameFieldName, JavacNode fieldNode, JavacNode typeNode) {
        // generates:
        // public static final String PROP_FIRST_NAME = "firstName";
        String propertyName = fieldNode.getName();
        if (fieldAlreadyExists(propertyNameFieldName, fieldNode)) return;
        injectField(typeNode, defVar(propertyNameFieldName)
                .modifiers(PRIVATE | STATIC | FINAL)
                .type(String.class)
                .withValue(fieldNode.getTreeMaker().Literal(propertyName))
                .$(typeNode));
    }

    private void generateSetter(String propertyNameFieldName, JavacNode fieldNode) {
        String setterName = toSetterName(fieldNode.getName());
        if (methodAlreadyExists(setterName, fieldNode)) return;
        injectMethod(fieldNode.up(), createSetterDecl(propertyNameFieldName, setterName, fieldNode));
    }

    private JCMethodDecl createSetterDecl(String propertyNameFieldName, String setterName,
                                          JavacNode fieldNode) {
        // public void setFirstName(String value) {
        //   final String oldValue = firstName;
        //   firstName = value;
        //   this$propertySupport.firePropertyChange(PROP_FIRST_NAME, oldValue, firstName);
        // }
        JCVariableDecl fieldDecl = (JCVariableDecl) fieldNode.get();
        List<JCTree.JCVariableDecl> params = List.of(
                defVar(propertyNameFieldName)
                        .type(fieldDecl.vartype)
                        .$(fieldNode));
        return defMethod(setterName)
                .withParams(params)
                .withBody(setterBody(propertyNameFieldName, fieldNode))
                .$(fieldNode);
    }

    private JCBlock setterBody(String propertyNameFieldName, JavacNode fieldNode) {
        Name oldValueName = fieldNode.toName(OLD_VALUE_PARAM);
        JCStatement[] statements = new JCStatement[3];
        statements[0] = oldValueVariableDecl(oldValueName, fieldNode);
        statements[1] = assignNewValueToFieldDecl(fieldNode);
        statements[2] = fireChangeEventMethodDecl(propertyNameFieldName, oldValueName, fieldNode);
        return fieldNode.getTreeMaker().Block(0, List.from(statements));
    }

    private JCStatement oldValueVariableDecl(Name oldValueName, JavacNode fieldNode) {
        TreeMaker treeMaker = fieldNode.getTreeMaker();
        JCVariableDecl varDecl = (JCVariableDecl) fieldNode.get();
        JCExpression init = newFieldAccessor(fieldNode);
        return treeMaker.VarDef(treeMaker.Modifiers(FINAL), oldValueName, varDecl.vartype, init);
    }

    private JCStatement assignNewValueToFieldDecl(JavacNode fieldNode) {
        JCVariableDecl fieldDecl = (JCVariableDecl) fieldNode.get();
        TreeMaker treeMaker = fieldNode.getTreeMaker();
        JCExpression fieldRef = newFieldAccessor(fieldNode);
        JCAssign assign = treeMaker.Assign(fieldRef, treeMaker.Ident(fieldDecl.name));
        return treeMaker.Exec(assign);
    }

    private JCStatement fireChangeEventMethodDecl(String propertyNameFieldName, Name oldValueName, JavacNode fieldNode) {
        TreeMaker treeMaker = fieldNode.getTreeMaker();
        JCExpression fn = chainDots(treeMaker, fieldNode, PROPERTY_SUPPORT_FIELD_NAME, "firePropertyChange");
        List<JCExpression> args = List.<JCExpression>of(treeMaker.Ident(fieldNode.toName(propertyNameFieldName)),
                treeMaker.Ident(oldValueName),
                newFieldAccessor(fieldNode));
        JCMethodInvocation m = treeMaker.Apply(List.<JCExpression>nil(), fn, args);
        return treeMaker.Exec(m);
    }
}
