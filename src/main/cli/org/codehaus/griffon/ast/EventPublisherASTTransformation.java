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

package org.codehaus.griffon.ast;

import griffon.core.EventPublisher;
import org.codehaus.griffon.runtime.core.EventRouter;
import griffon.util.RunnableWithArgs;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.codehaus.griffon.ast.GriffonASTUtils.*;

/**
 * Handles generation of code for the {@code @EventPublisher} annotation.
 * <p/>
 * Generally, it adds (if needed) a EventRouter field and
 * the needed add/removeEventListener methods to support the
 * listeners.
 * <p/>
 *
 * @author Andres Almiray
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class EventPublisherASTTransformation extends AbstractASTTransformation {
    private static final Logger LOG = LoggerFactory.getLogger(EventPublisherASTTransformation.class);
    private static final ClassNode RUNNABLE_WITH_ARGS_CLASS = ClassHelper.makeWithoutCaching(RunnableWithArgs.class);
    private static final ClassNode EVENT_HANDLER_CLASS = ClassHelper.makeWithoutCaching(EventPublisher.class);
    private static final ClassNode EVENT_PUBLISHER_CLASS = ClassHelper.makeWithoutCaching(griffon.transform.EventPublisher.class);
    private static final ClassNode EVENT_ROUTER_CLASS = ClassHelper.makeWithoutCaching(EventRouter.class);

    /**
     * Convenience method to see if an annotated node is {@code @EventPublisher}.
     *
     * @param node the node to check
     * @return true if the node is an event publisher
     */
    public static boolean hasEventPublisherAnnotation(AnnotatedNode node) {
        for (AnnotationNode annotation : node.getAnnotations()) {
            if (EVENT_PUBLISHER_CLASS.equals(annotation.getClassNode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the bulk of the processing, mostly delegating to other methods.
     *
     * @param nodes  the ast nodes
     * @param source the source unit for the nodes
     */
    public void visit(ASTNode[] nodes, SourceUnit source) {
        checkNodesForAnnotationAndType(nodes[0], nodes[1]);
        addEventRouterToClass(source, (ClassNode) nodes[1]);
    }

    public static void addEventRouterToClass(SourceUnit source, ClassNode classNode) {
        if (needsEventRouter(classNode, source)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Injecting "+ EventPublisher.class.getName() +" into "+ classNode.getName());
            }
            addEventRouter(classNode);
        }
    }

    /**
     * Snoops through the declaring class and all parents looking for methods<ul>
     * <li>void addEventListener(Object)</li>
     * <li>void addEventListener(String, Closure)</li>
     * <li>void addEventListener(String, RunnableWithArgs)</li>
     * <li>void removeEventListener(Object)</li>
     * <li>void removeEventListener(String, Closure)</li>
     * <li>void removeEventListener(String, RunnableWithArgs)</li>
     * <li>void publishEvent(String, List = [])</li>
     * <li>void publishEventOutside(String, List = [])</li>
     * <li>void publishEventAsync(String, List = [])</li>
     * </ul>If any are defined all
     * must be defined or a compilation error results.
     *
     * @param declaringClass the class to search
     * @param sourceUnit     the source unit, for error reporting. {@code @NotNull}.
     * @return true if property change support should be added
     */
    protected static boolean needsEventRouter(ClassNode declaringClass, SourceUnit sourceUnit) {
        boolean foundAdd = false, foundRemove = false, foundPublish = false;
        ClassNode consideredClass = declaringClass;
        while (consideredClass != null) {
            for (MethodNode method : consideredClass.getMethods()) {
                // just check length, MOP will match it up
                foundAdd = foundAdd || method.getName().equals("addEventListener") && method.getParameters().length == 1;
                foundAdd = foundAdd || method.getName().equals("addEventListener") && method.getParameters().length == 2;
                foundRemove = foundRemove || method.getName().equals("removeEventListener") && method.getParameters().length == 1;
                foundRemove = foundRemove || method.getName().equals("removeEventListener") && method.getParameters().length == 2;
                foundPublish = foundPublish || method.getName().equals("publishEvent") && method.getParameters().length == 1;
                foundPublish = foundPublish || method.getName().equals("publishEvent") && method.getParameters().length == 2;
                foundPublish = foundPublish || method.getName().equals("publishEventOutside") && method.getParameters().length == 1;
                foundPublish = foundPublish || method.getName().equals("publishEventOutside") && method.getParameters().length == 2;
                foundPublish = foundPublish || method.getName().equals("publishEventAsync") && method.getParameters().length == 1;
                foundPublish = foundPublish || method.getName().equals("publishEventAsync") && method.getParameters().length == 2;
                if (foundAdd && foundRemove && foundPublish) {
                    return false;
                }
            }
            consideredClass = consideredClass.getSuperClass();
        }
        if (foundAdd || foundRemove || foundPublish) {
            sourceUnit.getErrorCollector().addErrorAndContinue(
                    new SimpleMessage("@EventPublisher cannot be processed on "
                            + declaringClass.getName()
                            + " because some but not all of addEventListener, removeEventListener, publishEvent, publishEventAsync and publishEventOutside were declared in the current class or super classes.",
                            sourceUnit)
            );
            return false;
        }
        return true;
    }

    /**
     * Adds the necessary field and methods to support event firing.
     * <p/>
     * Adds a new field:
     * <code>protected final org.codehaus.griffon.runtime.core.EventRouter this$eventRouter = new org.codehaus.griffon.runtime.core.EventRouter()</code>
     * <p/>
     * Also adds support methods:
     * <code>public void addEventListener(Object)</code><br/>
     * <code>public void addEventListener(String, Closure)</code><br/>
     * <code>public void addEventListener(String, RunnableWithArgs)</code><br/>
     * <code>public void removeEventListener(Object)</code><br/>
     * <code>public void removeEventListener(String, Closure)</code><br/>
     * <code>public void removeEventListener(String, RunnableWithArgs)</code><br/>
     * <code>public void publishEvent(String,List = [])</code><br/>
     * <code>public void publishEventOutside(String,List = [])</code><br/>
     * <code>public void publishEventAsync(String,List = [])</code><br/>
     *
     * @param declaringClass the class to which we add the support field and methods
     */
    protected static void addEventRouter(ClassNode declaringClass) {
        declaringClass.addInterface(EVENT_HANDLER_CLASS);

        // add field:
        // protected final EventRouter this$eventRouter = new org.codehaus.griffon.runtime.core.EventRouter()
        FieldNode erField = declaringClass.addField(
                "this$eventRouter",
                ACC_FINAL | ACC_PRIVATE | ACC_SYNTHETIC,
                EVENT_ROUTER_CLASS,
                ctor(EVENT_ROUTER_CLASS, NO_ARGS));

        // add method:
        // void addEventListener(listener) {
        //     this$eventRouter.addEventListener(listener)
        //  }
        declaringClass.addMethod(new MethodNode(
                "addEventListener",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(ClassHelper.DYNAMIC_TYPE, "listener")),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "addEventListener",
                        vars("listener")))
        ));

        // add method:
        // void addEventListener(String name, Closure listener) {
        //     this$eventRouter.addEventListener(name, listener)
        //  }
        declaringClass.addMethod(new MethodNode(
                "addEventListener",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(ClassHelper.CLOSURE_TYPE, "listener")),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "addEventListener",
                        vars("name", "listener")))
        ));

        // add method:
        // void addEventListener(String name, RunnableWithArgs listener) {
        //     this$eventRouter.addEventListener(name, listener)
        //  }
        declaringClass.addMethod(new MethodNode(
                "addEventListener",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(RUNNABLE_WITH_ARGS_CLASS, "listener")),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "addEventListener",
                        vars("name", "listener")))
        ));

        // add method:
        // void removeEventListener(listener) {
        //    return this$eventRouter.removeEventListener(listener);
        // }
        declaringClass.addMethod(new MethodNode(
                "removeEventListener",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(ClassHelper.DYNAMIC_TYPE, "listener")),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "removeEventListener",
                        vars("listener")))
        ));

        // add method:
        // void removeEventListener(String name, Closure listener) {
        //    return this$eventRouter.removeEventListener(name, listener);
        // }
        declaringClass.addMethod(new MethodNode(
                "removeEventListener",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(ClassHelper.CLOSURE_TYPE, "listener")),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "removeEventListener",
                        vars("name", "listener")))
        ));

        // add method:
        // void removeEventListener(String name, RunnableWithArgs listener) {
        //    return this$eventRouter.removeEventListener(name, listener);
        // }
        declaringClass.addMethod(new MethodNode(
                "removeEventListener",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(RUNNABLE_WITH_ARGS_CLASS, "listener")),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "removeEventListener",
                        vars("name", "listener")))
        ));

        // add method:
        // void publishEvent(String name, List args = []) {
        //     this$eventRouter.publishEvent(name, args)
        //  }
        declaringClass.addMethod(new MethodNode(
                "publishEvent",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(ClassHelper.LIST_TYPE, "args", new ListExpression())),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "publish",
                        vars("name", "args")))
        ));

        // add method:
        // void publishEventOutside(String name, List args = []) {
        //     this$eventRouter.publishEventOutside(name, args)
        //  }
        declaringClass.addMethod(new MethodNode(
                "publishEventOutside",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(ClassHelper.LIST_TYPE, "args", new ListExpression())),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "publishOutside",
                        vars("name", "args")))
        ));

        // add method:
        // void publishEventAsync(String name, List args = []) {
        //     this$eventRouter.publishEventAsync(name, args)
        //  }
        declaringClass.addMethod(new MethodNode(
                "publishEventAsync",
                ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(
                        param(ClassHelper.STRING_TYPE, "name"),
                        param(ClassHelper.LIST_TYPE, "args", new ListExpression())),
                ClassNode.EMPTY_ARRAY,
                stmnt(call(
                        field(erField),
                        "publishAsync",
                        vars("name", "args")))
        ));
    }
}
