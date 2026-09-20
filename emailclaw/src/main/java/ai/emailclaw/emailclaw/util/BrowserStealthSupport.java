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
package ai.emailclaw.emailclaw.util;

import com.microsoft.playwright.BrowserContext;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class providing anti-bot fingerprint hardening and evasion scripts for Playwright.
 *
 * <p>Removes automation artifacts, normalizes WebGL/User-Agent/Plugins signatures, and injects
 * evasion scripts to prevent Cloudflare Turnstile and Bot Management triggers.
 */
public final class BrowserStealthSupport {

    private static final Logger LOGGER = Logger.getLogger(BrowserStealthSupport.class.getName());

    /**
     * Comprehensive stealth JavaScript snippet injected into every document context before any page
     * scripts execute.
     */
    private static final String STEALTH_INIT_SCRIPT =
            """
            (() => {
              // Register patched functions so Function.prototype.toString returns native code format
              const patchedFunctions = new WeakSet();
              try {
                const nativeToString = Function.prototype.toString;
                Function.prototype.toString = function() {
                  if (patchedFunctions.has(this)) {
                    return `function ${this.name || ''}() { [native code] }`;
                  }
                  return nativeToString.apply(this, arguments);
                };
                patchedFunctions.add(Function.prototype.toString);
              } catch (e) {}

              function markAsNative(fn) {
                if (typeof fn === 'function') {
                  patchedFunctions.add(fn);
                }
                return fn;
              }

              // 1. Remove and mask navigator.webdriver flag
              try {
                if (Object.getOwnPropertyDescriptor(navigator, 'webdriver')) {
                  delete navigator.webdriver;
                }
                Object.defineProperty(Navigator.prototype, 'webdriver', {
                  get: () => false,
                  enumerable: true,
                  configurable: true
                });
              } catch (e) {}

              // 2. Remove Headless references from User-Agent and appVersion if present
              try {
                const ua = navigator.userAgent;
                if (ua && ua.includes('HeadlessChrome')) {
                  const cleanUa = ua.replace(/HeadlessChrome/g, 'Chrome');
                  Object.defineProperty(Navigator.prototype, 'userAgent', {
                    get: () => cleanUa,
                    enumerable: true,
                    configurable: true
                  });
                  Object.defineProperty(Navigator.prototype, 'appVersion', {
                    get: () => navigator.appVersion.replace(/HeadlessChrome/g, 'Chrome'),
                    enumerable: true,
                    configurable: true
                  });
                }
              } catch (e) {}

              // 3. Mock window.chrome object with runtime, app, csi, loadTimes
              try {
                if (!window.chrome) {
                  window.chrome = {};
                }
                if (!window.chrome.runtime) {
                  window.chrome.runtime = {
                    OnInstalledReason: {
                      CHROME_UPDATE: 'chrome_update',
                      INSTALL: 'install',
                      SHARED_MODULE_UPDATE: 'shared_module_update',
                      UPDATE: 'update'
                    },
                    OnRestartRequiredReason: {
                      APP_UPDATE: 'app_update',
                      OS_UPDATE: 'os_update',
                      PERIODIC: 'periodic'
                    },
                    PlatformArch: {
                      ARM: 'arm',
                      ARM64: 'arm64',
                      MIPS: 'mips',
                      MIPS64: 'mips64',
                      X86_32: 'x86-32',
                      X86_64: 'x86-64'
                    },
                    PlatformNaclArch: {
                      ARM: 'arm',
                      MIPS: 'mips',
                      MIPS64: 'mips64',
                      X86_32: 'x86-32',
                      X86_64: 'x86-64'
                    },
                    PlatformOs: {
                      ANDROID: 'android',
                      CROS: 'cros',
                      LINUX: 'linux',
                      MAC: 'mac',
                      OPENBSD: 'openbsd',
                      WIN: 'win'
                    },
                    RequestUpdateCheckStatus: {
                      NO_UPDATE: 'no_update',
                      THROTTLED: 'throttled',
                      UPDATE_AVAILABLE: 'update_available'
                    },
                    connect: markAsNative(function() {}),
                    sendMessage: markAsNative(function() {})
                  };
                }
                if (!window.chrome.app) {
                  window.chrome.app = {
                    isInstalled: false,
                    InstallState: {
                      DISABLED: 'disabled',
                      INSTALLED: 'installed',
                      NOT_INSTALLED: 'not_installed'
                    },
                    RunningState: {
                      CANNOT_RUN: 'cannot_run',
                      READY_TO_RUN: 'ready_to_run',
                      RUNNING: 'running'
                    },
                    getDetails: markAsNative(function() {}),
                    getIsInstalled: markAsNative(function() { return false; }),
                    installState: markAsNative(function() {})
                  };
                }
                if (!window.chrome.csi) {
                  window.chrome.csi = markAsNative(function() {});
                }
                if (!window.chrome.loadTimes) {
                  window.chrome.loadTimes = markAsNative(function() {});
                }
              } catch (e) {}

              // 4. Ensure non-empty plugins array
              try {
                if (navigator.plugins && navigator.plugins.length === 0) {
                  const fakePlugins = [
                    {
                      name: 'PDF Viewer',
                      description: 'Portable Document Format',
                      filename: 'internal-pdf-viewer',
                      length: 1
                    },
                    {
                      name: 'Chrome PDF Viewer',
                      description: 'Portable Document Format',
                      filename: 'internal-pdf-viewer',
                      length: 1
                    },
                    {
                      name: 'Chromium PDF Viewer',
                      description: 'Portable Document Format',
                      filename: 'internal-pdf-viewer',
                      length: 1
                    },
                    {
                      name: 'Microsoft Edge PDF Viewer',
                      description: 'Portable Document Format',
                      filename: 'internal-pdf-viewer',
                      length: 1
                    },
                    {
                      name: 'WebKit built-in PDF',
                      description: 'Portable Document Format',
                      filename: 'internal-pdf-viewer',
                      length: 1
                    }
                  ];
                  fakePlugins.item = markAsNative(function(index) { return this[index]; });
                  fakePlugins.namedItem = markAsNative(function(name) {
                    return this.find(p => p.name === name) || null;
                  });
                  fakePlugins.refresh = markAsNative(function() {});
                  Object.defineProperty(Navigator.prototype, 'plugins', {
                    get: () => fakePlugins,
                    enumerable: true,
                    configurable: true
                  });
                }
              } catch (e) {}

              // 5. Ensure navigator.languages is non-empty
              try {
                if (!navigator.languages || navigator.languages.length === 0) {
                  Object.defineProperty(Navigator.prototype, 'languages', {
                    get: () => ['en-US', 'en'],
                    enumerable: true,
                    configurable: true
                  });
                }
              } catch (e) {}

              // 6. Notification permission consistency
              try {
                if (window.navigator && window.navigator.permissions) {
                  const originalQuery = window.navigator.permissions.query;
                  const queryProxy = function(parameters) {
                    if (parameters && parameters.name === 'notifications') {
                      const permState = (typeof Notification !== 'undefined' && Notification.permission)
                          ? Notification.permission : 'default';
                      return Promise.resolve({
                        state: permState,
                        name: 'notifications',
                        onchange: null
                      });
                    }
                    return originalQuery.apply(this, arguments);
                  };
                  markAsNative(queryProxy);
                  window.navigator.permissions.query = queryProxy;
                }
              } catch (e) {}

              // 7. WebGL vendor and renderer masking for virtual / software rasterizers
              try {
                const patchWebGL = (proto) => {
                  if (!proto || !proto.getParameter) return;
                  const originalGetParameter = proto.getParameter;
                  const getParameterProxy = function(param) {
                    // UNMASKED_VENDOR_WEBGL
                    if (param === 37445) {
                      const res = originalGetParameter.apply(this, arguments);
                      if (!res || res.includes('Google') || res.includes('VMware') || res.includes('Mesa')) {
                        return 'Intel Inc.';
                      }
                      return res;
                    }
                    // UNMASKED_RENDERER_WEBGL
                    if (param === 37446) {
                      const res = originalGetParameter.apply(this, arguments);
                      if (!res || res.includes('SwiftShader') || res.includes('llvmpipe') || res.includes('Softpipe')) {
                        return 'Intel Iris OpenGL Engine';
                      }
                      return res;
                    }
                    return originalGetParameter.apply(this, arguments);
                  };
                  markAsNative(getParameterProxy);
                  proto.getParameter = getParameterProxy;
                };
                if (typeof WebGLRenderingContext !== 'undefined') {
                  patchWebGL(WebGLRenderingContext.prototype);
                }
                if (typeof WebGL2RenderingContext !== 'undefined') {
                  patchWebGL(WebGL2RenderingContext.prototype);
                }
              } catch (e) {}
            })();
            """;

    private BrowserStealthSupport() {
        // Utility class, instantiation prohibited.
    }

    /**
     * Applies anti-detection stealth configuration to the specified browser context.
     *
     * @param context the target Playwright browser context
     */
    public static void applyStealth(BrowserContext context) {
        if (context == null) {
            LOGGER.warning("Cannot apply stealth hardening: BrowserContext is null");
            return;
        }
        LOGGER.info("Applying anti-bot stealth hardening to Playwright browser context...");
        try {
            context.addInitScript(STEALTH_INIT_SCRIPT);
            context.setExtraHTTPHeaders(Map.of("Accept-Language", "en-US,en;q=0.9"));
            LOGGER.info("Anti-bot stealth hardening successfully applied.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to apply anti-bot stealth hardening", e);
        }
    }
}
