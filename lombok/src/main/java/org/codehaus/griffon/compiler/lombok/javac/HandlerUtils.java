package org.codehaus.griffon.compiler.lombok.javac;


import com.sun.tools.javac.tree.JCTree;

import javax.lang.model.element.Modifier;
import java.util.Set;

public class HandlerUtils {
    public static int toJavacModifier(JCTree.JCModifiers modifiers){
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
}
