package org.codehaus.griffon.compiler.lombok.javac;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import griffon.util.GriffonClassUtils;
import griffon.util.Threading;

import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

import static lombok.javac.handlers.JavacHandlerUtil.*;
import static org.codehaus.griffon.compiler.lombok.javac.HandlerUtils.*;
import static org.codehaus.griffon.ast.ThreadingASTTransformation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HandleThreading implements JavacAnnotationHandler<Threading> {
    private static final Logger LOG = LoggerFactory.getLogger(HandleThreading.class);

    public boolean handle(AnnotationValues<Threading> annotation, JCAnnotation ast, JavacNode annotationNode) {
        markAnnotationAsProcessed(annotationNode, Threading.class);
        JavacNode methodNode = annotationNode.up();

        if (methodNode == null || methodNode.getKind() != Kind.METHOD || !(methodNode.get() instanceof JCTree.JCMethodDecl)) {
            annotationNode.addError("@Threading is legal only on methods.");
            return true;
        }

        JCTree.JCMethodDecl method = (JCTree.JCMethodDecl) methodNode.get();

        if ((method.mods.flags & Flags.ABSTRACT) != 0) {
            annotationNode.addError("@Threading is legal only on concrete methods.");
            return true;
        }

        Threading.Policy threadingPolicy = annotation.getInstance().value();
        if (threadingPolicy == Threading.Policy.SKIP) return true;

        String threadingMethod = getThreadingMethod(threadingPolicy);
        handleMethodForInjection(methodNode.up().getName(), method, threadingMethod);

        return true;
    }

    private void handleMethodForInjection(String declaringClassName, JCTree.JCMethodDecl method, String threadingMethod) {
        GriffonClassUtils.MethodDescriptor md = methodDescriptorFor(method);
        System.err.println(md);
        if (GriffonClassUtils.isPlainMethod(md) &&
                !GriffonClassUtils.isEventHandler(md) &&
                !skipInjection(declaringClassName + "." + method.getName())) {
            wrapStatements(declaringClassName, method, threadingMethod);
        }
    }

    private void wrapStatements(String declaringClassName, JCTree.JCMethodDecl method, String threadingMethod) {
        // 1. make method parameters final
        for (JCTree.JCVariableDecl parameter : method.getParameters()) {
            makeFinal(parameter);
        }

        // 2. create Runnable anonymous inner class wrapping method body

        // 3. make call for UIThreadHelper.getInstance().$threadingMethod(runnable)

        // 4. substitute method body
    }

    private void makeFinal(JCTree.JCVariableDecl parameter) {
        if ((parameter.mods.flags & Flags.FINAL) == 0) {
            parameter.mods.flags |= Flags.FINAL;
        }
    }

    private GriffonClassUtils.MethodDescriptor methodDescriptorFor(JCTree.JCMethodDecl method) {
        List<JCTree.JCVariableDecl> parameters = method.getParameters();
        String[] parameterTypes = new String[parameters.size()];

        for (int i = 0; i < parameterTypes.length; i++) {
            parameterTypes[i] = parameters.get(i).getType().type.toString();
        }

        int modifiers = toJavacModifier(method.getModifiers().getFlags());

        return new GriffonClassUtils.MethodDescriptor(method.getName().toString(), parameterTypes, modifiers);
    }
}
