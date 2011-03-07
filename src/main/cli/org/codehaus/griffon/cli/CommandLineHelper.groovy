/* Copyright 2004-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.cli

import org.codehaus.groovy.runtime.DefaultGroovyMethods

/**
 * Utility methods for use on the command line, including method to accept user input etc. 
 *
 * @author Graeme Rocher (Grails 1.2)
 */
public class CommandLineHelper {
    private PrintStream out = System.out

    public CommandLineHelper() {
        // default
    }

    public CommandLineHelper(PrintStream out) {
        this.out = out
    }

   /**
     * Replacement for AntBuilder.input() to eliminate dependency of
     * GriffonScriptRunner on the Ant libraries. Prints a message and
     * returns whatever the user enters (once they press &ltreturn&gt).
     * @param message The message/question to display.
     * @return The line of text entered by the user. May be a blank
     * string.
     */
    String userInput(String message) {
        return userInput(message, null)
    }

    /**
     * Replacement for AntBuilder.input() to eliminate dependency of
     * GriffonScriptRunner on the Ant libraries. Prints a message and
     * list of valid responses, then returns whatever the user enters
     * (once they press &ltreturn&gt). If the user enters something
     * that is not in the array of valid responses, the message is
     * displayed again and the method waits for more input. It will
     * display the message a maximum of three times before it gives up
     * and returns <code>null</code>.
     * @param message The message/question to display.
     * @param validResponses An array of responses that the user is
     * allowed to enter. Displayed after the message.
     * @return The line of text entered by the user, or <code>null</code>
     * if the user never entered a valid string.
     */
    String userInput(String message, String[] validResponses) {
        String responsesString = null
        if (validResponses != null) {
            responsesString = DefaultGroovyMethods.join(validResponses, ",")
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))

        for (int it = 0; it < 3; it++) {
            out.print(message)
            if (responsesString != null) {
                out.print(" [")
                out.print(responsesString)
                out.print("] ")
            }

            try {
                String line = reader.readLine()

                if (validResponses == null) return line

                for (String validResponse : validResponses) {
                    if (line != null && line.equalsIgnoreCase(validResponse)) {
                        return line
                    }
                }

                out.println()
                out.println("Invalid option '" + line + "' - must be one of: [" + responsesString + "]")
                out.println()
            } catch (IOException ex) {
                ex.printStackTrace()
                break
            }
        }

        // No valid response given.
        out.println("No valid response entered - giving up asking.")
        return null
    }    

    boolean confirmInput(String msg) {
        userInput(msg, ['y','n'] as String[]) == 'y'
    }

    String askAndDo(String message, Closure yesCallback = null, Closure noCallback = null) {
        confirmInput(message) ? yesCallback?.call() : noCallback?.call()
    }
}
