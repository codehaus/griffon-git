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
import griffon.util.EventPublisher;
import griffon.util.EventRouter;
import groovy.lang.Closure;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.PRIVATE;
import static lombok.javac.handlers.AstBuilder.defMethod;
import static lombok.javac.handlers.AstBuilder.defVar;
import static lombok.javac.handlers.HandlerUtils.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * @author Andres Almiray
 */
public class HandleEventPublisher implements JavacAnnotationHandler<EventPublisher> {
    private static final Logger LOG = LoggerFactory.getLogger(HandleEventPublisher.class);
    private static final String FIELD_NAME = "this$eventrouter";
    private static final String NAME_PARAM = "name";
    private static final String LISTENER_PARAM = "listener";
    private static final String ARGS_PARAM = "args";

    public boolean handle(AnnotationValues<EventPublisher> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        markAnnotationAsProcessed(annotationNode, EventPublisher.class);

        JavacNode typeNode = annotationNode.up();
        switch (typeNode.getKind()) {
            case TYPE:
                if ((((JCTree.JCClassDecl) typeNode.get()).mods.flags & Flags.INTERFACE) != 0) {
                    annotationNode.addError("@EventPublisher is legal only on classes and enums.");
                    return true;
                }

                if (fieldExists(FIELD_NAME, typeNode) != JavacHandlerUtil.MemberExistsResult.NOT_EXISTS) {
                    annotationNode.addWarning("Field '" + FIELD_NAME + "' already exists.");
                    return true;
                }

                addEventPublisherSupport(typeNode);
                return true;
            default:
                annotationNode.addError("@EventPublisher is legal only on types.");
                return true;
        }
    }

    private void addEventPublisherSupport(JavacNode typeNode) {
        createEventRouterField(typeNode);
        injectListenerManagementMethod(typeNode, "addEventListener");
        injectListenerManagementMethod(typeNode, "removeEventListener");
        injectEventPublisherMethod(typeNode, "publishEventAsync", "publishAsync");
        injectEventPublisherMethod(typeNode, "publishEvent", "publish");

        if (LOG.isDebugEnabled()) LOG.debug("Modified " + typeNode.getName() + " as an EventPublisher.");
    }

    private void injectListenerManagementMethod(JavacNode typeNode, String methodName) {
        TreeMaker treeMaker = typeNode.getTreeMaker();

        List<JCTree.JCVariableDecl> params = List.of(
                defVar(LISTENER_PARAM)
                        .modifiers(FINAL)
                        .type(Object.class)
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
                        .type(Closure.class)
                        .$(typeNode));
        args = extractArgNames(params, treeMaker);

        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .withBody(body(methodName, args, typeNode))
                .$(typeNode));
    }

    private void injectEventPublisherMethod(JavacNode typeNode, String methodName, String routerMethodName) {
        TreeMaker treeMaker = typeNode.getTreeMaker();

        List<JCTree.JCVariableDecl> params = List.of(
                defVar(NAME_PARAM)
                        .modifiers(FINAL)
                        .type(String.class)
                        .$(typeNode));
        List<JCTree.JCExpression> args = extractArgNames(params, treeMaker);
        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .withBody(body(routerMethodName, args, typeNode))
                .$(typeNode));

        params = List.of(
                defVar(NAME_PARAM)
                        .modifiers(FINAL)
                        .type(String.class)
                        .$(typeNode),
                defVar(ARGS_PARAM)
                        .modifiers(FINAL)
                        .type(java.util.List.class)
                        .$(typeNode));
        args = extractArgNames(params, treeMaker);

        injectMethod(typeNode, defMethod(methodName)
                .withParams(params)
                .withBody(body(routerMethodName, args, typeNode))
                .$(typeNode));
    }


    private List<JCTree.JCStatement> body(String methodName, List<JCTree.JCExpression> args, JavacNode typeNode) {
        TreeMaker treeMaker = typeNode.getTreeMaker();
        JCTree.JCExpression delegateToPropertySupport = delegateToeventRouter(methodName, args, typeNode);
        return List.<JCTree.JCStatement>of(treeMaker.Exec(delegateToPropertySupport));
    }

    private JCTree.JCExpression delegateToeventRouter(String methodName, List<JCTree.JCExpression> args, JavacNode typeNode) {
        TreeMaker treeMaker = typeNode.getTreeMaker();
        JCTree.JCExpression fn = chainDots(treeMaker, typeNode, FIELD_NAME, methodName);
        return treeMaker.Apply(List.<JCTree.JCExpression>nil(), fn, args);
    }

    private void createEventRouterField(JavacNode typeNode) {
        TreeMaker maker = typeNode.getTreeMaker();

        JCTree.JCExpression type = chainDotsString(maker, typeNode, EventRouter.class.getName());
        JCTree.JCExpression instance = maker.NewClass(null, NIL_EXPRESSION, type, NIL_EXPRESSION, null);

        injectField(typeNode, defVar(FIELD_NAME)
                .modifiers(PRIVATE | FINAL)
                .type(EventRouter.class)
                .withValue(instance)
                .$(typeNode));

    }
}
