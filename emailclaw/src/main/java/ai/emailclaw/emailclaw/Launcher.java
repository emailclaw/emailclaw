/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw;

/**
 * Only used to solve the bootstrap issue of running JavaFX applications with Maven.
 * The actual application startup logic is in App.java.
 */
public class Launcher {
    public static void main(String[] args) {
        boolean isService = false;
        for (String arg : args) {
            if ("--service".equalsIgnoreCase(arg) || "-s".equalsIgnoreCase(arg)) {
                isService = true;
                break;
            }
        }
        if (isService) {
            ServiceApp.main(args);
        } else {
            // Directly call App's main method, bypassing Maven's default JavaFX bootstrap mechanism
            App.main(args);
        }
    }
}
