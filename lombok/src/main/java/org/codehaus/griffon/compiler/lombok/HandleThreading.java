package org.codehaus.griffon.compiler.lombok;

import griffon.util.Threading;

import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;

public class HandleThreading implements JavacAnnotationHandler<Threading> {
    @Override public boolean handle(AnnotationValues<Threading> annotation, JCAnnotation ast, JavacNode annotationNode) {
        System.err.println(ast+" "+annotationNode);
        if (annotationNode.up().getKind() != Kind.FIELD && annotationNode.up().getKind() != Kind.METHOD) {
            return false;
        }
        return true;
    }
}
