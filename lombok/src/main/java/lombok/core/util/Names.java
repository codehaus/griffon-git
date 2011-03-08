/*
 * Created on Nov 30, 2010
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Copyright @2010 the original author or authors.
 */
package lombok.core.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

import static java.lang.Character.*;
import static lombok.core.util.Arrays.array;

/**
 * Utility methods related to names.
 *
 * @author Alex Ruiz
 */
public final class Names {
    /**
     * Splits the name of the class using "\." as the regular expression. For example, {@code java.lang.String} will be
     * split into { "java", "lang", "String" }.
     *
     * @param type the given class.
     * @return the name of the type split using "\." as the regular expression.
     */
    public static String[] splitNameOf(Class<?> type) {
        return type.getName().split("\\.");
    }

    /**
     * Creates the name of the constant that holds the name of a property. For example, if the name of a property is
     * "firstName," this method will return "PROP_FIRST_NAME."
     *
     * @param propertyName the name of the property.
     * @return the name of the constant that holds the name of a property.
     */
    public static String nameOfConstantBasedOnProperty(String propertyName) {
        char[] chars = propertyName.toCharArray();
        StringBuilder b = new StringBuilder();
        b.append("PROP_");
        int charCount = chars.length;
        for (int i = 0; i < charCount; i++) {
            char c = chars[i];
            if (isUpperCase(c) && i > 0) b.append('_');
            if (isLowerCase(c)) c = toUpperCase(c);
            b.append(c);
        }
        return b.toString();
    }

    private Names() {
    }
}
