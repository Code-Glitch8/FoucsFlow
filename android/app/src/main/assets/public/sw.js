/**
 * Copyright 2018 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// If the loader is already loaded, just stop.
if (!self.define) {
  let registry = {};

  // Used for `eval` and `importScripts` where we can't get script URL by other means.
  // In both cases, it's safe to use a global var because those functions are synchronous.
  let nextDefineUri;

  const singleRequire = (uri, parentUri) => {
    uri = new URL(uri + ".js", parentUri).href;
    return registry[uri] || (
      
        new Promise(resolve => {
          if ("document" in self) {
            const script = document.createElement("script");
            script.src = uri;
            script.onload = resolve;
            document.head.appendChild(script);
          } else {
            nextDefineUri = uri;
            importScripts(uri);
            resolve();
          }
        })
      
      .then(() => {
        let promise = registry[uri];
        if (!promise) {
          throw new Error(`Module ${uri} didn’t register its module`);
        }
        return promise;
      })
    );
  };

  self.define = (depsNames, factory) => {
    const uri = nextDefineUri || ("document" in self ? document.currentScript.src : "") || location.href;
    if (registry[uri]) {
      // Module is already loading or loaded.
      return;
    }
    let exports = {};
    const require = depUri => singleRequire(depUri, uri);
    const specialDeps = {
      module: { uri },
      exports,
      require
    };
    registry[uri] = Promise.all(depsNames.map(
      depName => specialDeps[depName] || require(depName)
    )).then(deps => {
      factory(...deps);
      return exports;
    });
  };
}
define(['./workbox-afac4cd2'], (function (workbox) { 'use strict';

  self.skipWaiting();
  workbox.clientsClaim();
  /**
   * The precacheAndRoute() method efficiently caches and responds to
   * requests for URLs in the manifest.
   * See https://goo.gl/S9QRab
   */
  workbox.precacheAndRoute([{
    "url": "registerSW.js",
    "revision": "1872c500de691dce40960bb85481de07"
  }, {
    "url": "pwa-maskable-512x512.png",
    "revision": "2d2be1403e47413c1a92ccc93e0a5ef3"
  }, {
    "url": "pwa-512x512.png",
    "revision": "33ce90ccfe5da58ce3d4c2df1944920b"
  }, {
    "url": "pwa-192x192.png",
    "revision": "a820cecbfe1630ac02840bafeb89ec66"
  }, {
    "url": "index.html",
    "revision": "7f37b3b8f8f98edf73edfa3d55ee57c2"
  }, {
    "url": "icon.svg",
    "revision": "3e73cec329a4560bab86efacdd10ae21"
  }, {
    "url": "icon-maskable.svg",
    "revision": "68e379d4a6eea0332f285317a12a348f"
  }, {
    "url": "apple-touch-icon.png",
    "revision": "f3fb03f8c58bd52aa924b7caef9053df"
  }, {
    "url": "assets/index-D-oj_aDI.js",
    "revision": null
  }, {
    "url": "assets/index-BsTsKBGE.css",
    "revision": null
  }, {
    "url": "apple-touch-icon.png",
    "revision": "f3fb03f8c58bd52aa924b7caef9053df"
  }, {
    "url": "icon.svg",
    "revision": "3e73cec329a4560bab86efacdd10ae21"
  }, {
    "url": "pwa-192x192.png",
    "revision": "a820cecbfe1630ac02840bafeb89ec66"
  }, {
    "url": "pwa-512x512.png",
    "revision": "33ce90ccfe5da58ce3d4c2df1944920b"
  }, {
    "url": "pwa-maskable-512x512.png",
    "revision": "2d2be1403e47413c1a92ccc93e0a5ef3"
  }, {
    "url": "manifest.webmanifest",
    "revision": "819bb0cbac9b1c6eaa9e66c66f94a774"
  }], {});
  workbox.cleanupOutdatedCaches();
  workbox.registerRoute(new workbox.NavigationRoute(workbox.createHandlerBoundToURL("index.html"), {
    denylist: [/\.zip$/]
  }));
  workbox.registerRoute(/^https:\/\/fonts\.googleapis\.com\/.*/i, new workbox.CacheFirst({
    "cacheName": "google-fonts-cache",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 10,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');
  workbox.registerRoute(/^https:\/\/fonts\.gstatic\.com\/.*/i, new workbox.CacheFirst({
    "cacheName": "gstatic-fonts-cache",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 10,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');

}));
