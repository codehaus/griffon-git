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
import griffon.util.EventPublisher;
import griffon.util.EventRouter;
import groovy.lang.Closure;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static lombok.javac.handlers.JavacHandlerUtil.*;
import static org.codehaus.griffon.compiler.lombok.javac.HandlerUtils.*;
import static org.codehaus.griffon.compiler.lombok.javac.AstBuilder.*;

/**
 * @author Andres Almiray
 */
public class HandleEventPublisher implements JavacAnnotationHandler<EventPublisher> {
    private static final Logger LOG = LoggerFactory.getLogger(HandleEventPublisher.class);
    private static final String FIELD_NAME = "this$eventrouter";

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
        TokenBuilder b = new HandlerUtils.TokenBuilder(typeNode);

        JavacNode eventRouterField = createEventRouterField(typeNode, b);
        injectAddEventListenerMethod(typeNode, eventRouterField, b);
        injectRemoveEventListenerMethod(typeNode, eventRouterField, b);
        injectEventPublisherMethod(typeNode, eventRouterField, b);
        injectEventPublisherAsyncMethod(typeNode, eventRouterField, b);

        if (LOG.isDebugEnabled()) LOG.debug("Modified " + typeNode.getName() + " as an EventPublisher.");
    }

    private void injectEventPublisherAsyncMethod(JavacNode typeNode, JavacNode eventRouterField, TokenBuilder b) {
        injectEventPublisherMethod(typeNode, eventRouterField, "publishEventAsync", "publishAsync", b);
    }

    private void injectEventPublisherMethod(JavacNode typeNode, JavacNode eventRouterField, TokenBuilder b) {
        injectEventPublisherMethod(typeNode, eventRouterField, "publishEvent", "publish", b);
    }

    private void injectRemoveEventListenerMethod(JavacNode typeNode, JavacNode eventRouterField, TokenBuilder b) {
        injectListenerManagementMethod(typeNode, eventRouterField, "removeEventListener", b);
    }

    private void injectAddEventListenerMethod(JavacNode typeNode, JavacNode eventRouterField, TokenBuilder b) {
        injectListenerManagementMethod(typeNode, eventRouterField, "addEventListener", b);
    }

    private void injectListenerManagementMethod(JavacNode typeNode, JavacNode eventRouterField, String methodName, TokenBuilder b) {
        TreeMaker m = typeNode.getTreeMaker();

        ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<JCTree.JCVariableDecl>();
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();

        JCTree.JCVariableDecl param = b.param(Flags.FINAL, Object.class, "listener");
        params.append(param);
        args.append(m.Ident(param.getName()));
        JCTree.JCStatement addListenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(eventRouterField), b.name(methodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(addListenerCall).build());

        params = new ListBuffer<JCTree.JCVariableDecl>();
        args = new ListBuffer<JCTree.JCExpression>();
        param = b.param(Flags.FINAL, String.class, "name");
        params.append(param);
        args.append(m.Ident(param.getName()));
        param = b.param(Flags.FINAL, Closure.class, "listener");
        params.append(param);
        addListenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(eventRouterField), b.name(methodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(addListenerCall).build());
    }

    private void injectEventPublisherMethod(JavacNode typeNode, JavacNode eventRouterField, String methodName, String routerMethodName, TokenBuilder b) {
        TreeMaker m = typeNode.getTreeMaker();

        ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<JCTree.JCVariableDecl>();
        ListBuffer<JCTree.JCExpression> args = new ListBuffer<JCTree.JCExpression>();

        JCTree.JCVariableDecl param = b.param(Flags.FINAL, String.class, "name");
        params.append(param);
        args.append(m.Ident(param.getName()));
        JCTree.JCStatement addListenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(eventRouterField), b.name(routerMethodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(addListenerCall).build());

        params = new ListBuffer<JCTree.JCVariableDecl>();
        args = new ListBuffer<JCTree.JCExpression>();
        param = b.param(Flags.FINAL, String.class, "name");
        params.append(param);
        args.append(m.Ident(param.getName()));
        param = b.param(Flags.FINAL, java.util.List.class, "listener");
        params.append(param);
        args.append(m.Ident(param.getName()));
        addListenerCall = m.Exec(m.Apply(NIL_EXPRESSION, m.Select(readField(eventRouterField), b.name(routerMethodName)), toList(args)));
        injectMethod(typeNode, defMethod(typeNode, methodName).withParams(params).withBody(addListenerCall).build());
    }

    private JavacNode createEventRouterField(JavacNode typeNode, TokenBuilder b) {
        TreeMaker maker = typeNode.getTreeMaker();

        JCTree.JCExpression type = b.type(EventRouter.class);
        JCTree.JCExpression instance = maker.NewClass(null, NIL_EXPRESSION, type, NIL_EXPRESSION, null);

        JCTree.JCVariableDecl fieldDecl = maker.VarDef(
                b.mods(Flags.PRIVATE | Flags.FINAL),
                b.name(FIELD_NAME), type, instance);
        injectField(typeNode, fieldDecl);

        return getField(typeNode, FIELD_NAME);
    }
}
