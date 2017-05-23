(ns miraj.boot-miraj
  {:boot/export-tasks true}
  (:refer-clojure :exclude [compile])
  (:require [clojure.java.io              :as io]
            [clojure.set                  :as set]
            [clojure.string               :as str]
            [clojure.tools.namespace.repl :as ctnr :refer [refresh set-refresh-dirs]]
            [miraj.co-dom                 :refer [*pprint*]]
            [miraj.core                   :as miraj]
            [stencil.core                 :as stencil]
            [boot.pod                     :as pod]
            [boot.core                    :as boot]
            [boot.util                    :as util :refer [dbug info]]
            [boot.task.built-in           :as builtin]))

(defn ns->path
  [n]
  (let [nss (str n)
        nspath (str (-> nss (str/replace \- \_) (str/replace "." "/")))]
    nspath))

(defn- find-mainfiles [fs]
  (->> fs
       boot/input-files
       (boot/by-ext [".clj"])))

#_(defn do-build
  [f]
  (println "aot-compile task: " ~(boot/tmp-path f))

  ;; TODO: load the files and filter for components here, THEN call build-components

  (miraj.co-dom/build-component ~(.getPath (boot/tmp-file f))))

(def ^:private deps
  '[[miraj/co-dom "0.1.0-SNAPSHOT"]])

(def webcomponents-edn "webcomponents.edn")
(def webstyles-edn     "webstyles.edn")
(def webextensions-edn "webextensions.edn")

;; this works from edn files. for deflibrary vars, use link-component-libs
;; FIXME: move mustache template etc. to core/compiler.clj
(defn- compile-libraries
  [verbose]
  (if verbose (util/info "Running fn 'compile-libraries'\n"))
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [workspace (boot/tmp-dir!)
            webcomponents-edn-files (->> fileset
                                         boot/input-files
                                         (boot/by-re [(re-pattern (str webcomponents-edn "$"))]))
            webcomponents-edn-f (condp = (count webcomponents-edn-files)
                                  0 (throw (Exception. webcomponents-edn-files " file not found"))
                                  1 (first webcomponents-edn-files)
                                  (throw (Exception.
                                          (str "Only one " webcomponents-edn " file allowed"))))
            webcomponents-edn-map (-> (boot/tmp-file webcomponents-edn-f) slurp read-string)
            ;; _ (println "webcomponents-edn-map: " webcomponents-edn-map)

            target-middleware identity
            target-handler (target-middleware next-handler)]
        (doseq [library webcomponents-edn-map]
          (let [content (stencil/render-file
                         "miraj/boot_miraj/webcomponents.mustache"
                         library)
                library-out-path (str (ns->path (:miraj/ns library)) "_impl.clj")
                library-out-file (doto (io/file workspace library-out-path) io/make-parents)]
            (if verbose (util/info "Emitting %s\n" library-out-file))
            (spit library-out-file content)))
        (target-handler (-> fileset
                            (boot/add-source workspace)
                            boot/commit!))))))

(defn- compile-extensions
  [verbose]
  (if verbose (util/info "Running fn 'compile-extentions'\n"))
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [workspace (boot/tmp-dir!)
            webcomponents-edn-files (->> fileset
                                         boot/input-files
                                         (boot/by-re [(re-pattern (str webcomponents-edn "$"))]))
            webcomponents-edn-f (condp = (count webcomponents-edn-files)
                                  0 (throw (Exception. webcomponents-edn-files " file not found"))
                                  1 (first webcomponents-edn-files)
                                  (throw (Exception.
                                          (str "Only one " webcomponents-edn " file allowed"))))
            webcomponents-edn-map (-> (boot/tmp-file webcomponents-edn-f) slurp read-string)
            ;; _ (println "webcomponents-edn-map: " webcomponents-edn-map)

            ;; FIXME: move to compile-extensions

            webextensions-edn-files (->> fileset
                                     boot/input-files
                                     (boot/by-re [(re-pattern (str webextensions-edn "$"))]))
            webextensions-edn-f (if webextensions-edn-files
                              (condp = (count webextensions-edn-files)
                                0 nil
                                1 (first webextensions-edn-files)
                                (throw (Exception.
                                        (str "Only one " webextensions-edn " file allowed"))))
                              nil)
            webextensions-edn-map (if webextensions-edn-f
                                (-> (boot/tmp-file webextensions-edn-f) slurp read-string) nil)
            _ (println "webextensions-edn-map: " webextensions-edn-map)

            target-middleware identity
            target-handler (target-middleware next-handler)]
        (doseq [component webcomponents-edn-map]
          (let [content (stencil/render-file
                         "miraj/boot_miraj/webcomponents.mustache"
                         component)
                component-out-path (str (ns->path (:miraj/ns component)) ".clj")
                component-out-file (doto (io/file workspace component-out-path) io/make-parents)]
            (spit component-out-file content)))
        #_(if webextensions-edn-map
          (doseq [behavior webextensions-edn-map]
            (let [content (stencil/render-file
                           "miraj/boot_miraj/behaviors.mustache"
                           (merge {:properties [] :listeners false} behavior))
                  behavior-out-path (str (ns->path (:ns behavior)) ".clj")
                  behavior-out-file (doto (io/file workspace behavior-out-path) io/make-parents)]
              (spit behavior-out-file content))))
        (target-handler (-> fileset
                            (boot/add-resource workspace)
                            boot/commit!))))))

(defn- compile-styles
  [verbose]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info "Running fn 'compile-styles'\n"))
      (let [workspace (boot/tmp-dir!)
            webstyles-edn-files (->> fileset
                                         boot/input-files
                                         (boot/by-re [(re-pattern (str webstyles-edn "$"))]))
            webstyles-edn-f (condp = (count webstyles-edn-files)
                                  0 (throw (Exception. (format " file %s not found" webstyles-edn)))
                                  1 (first webstyles-edn-files)
                                  (throw (Exception.
                                          (str "Only one " webstyles-edn " file allowed"))))
            webstyles-edn-map (-> (boot/tmp-file webstyles-edn-f) slurp read-string)
            ;; _ (util/info (format "webstyles-edn-map: %s\n" webstyles-edn-map))

            target-middleware identity
            target-handler (target-middleware next-handler)]
        (doseq [style webstyles-edn-map]
          (let [content (stencil/render-file
                         "miraj/boot_miraj/webstyles.mustache"
                         style)
                style-out-path (str (ns->path (:miraj/ns style)) ".clj")
                style-out-file (doto (io/file workspace style-out-path) io/make-parents)]
            (spit style-out-file content)))
        (target-handler (-> fileset
                            (boot/add-resource workspace)
                            boot/commit!))))))

;; TODO: split into two fns, cljs and html
(defn- compile-components
  "Compile webcomponents."
  [opts]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if (or (:debug opts) (:verbose opts))
        (util/info (format "Running fn 'compile-components' for %s\n" opts)))
      (let [;; namespace-set (:components opts)
            html-workspace (boot/tmp-dir!)
            cljs-workspace (boot/tmp-dir!)
            cljs-handler (if (:keep opts) boot/add-resource boot/add-source)
            out-pfx "" ;;"assets"
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (binding [miraj/*debug* (:debug opts)
                  miraj/*verbose* (:verbose opts)
                  miraj/*keep* (:keep opts)
                  miraj.co-dom/*pprint* (:pprint opts)
                  *compile-path* (.getPath cljs-workspace)]
          (miraj/compile-webcomponent-vars-cljs opts))
        (binding [miraj/*debug* (:debug opts)
                  miraj/*verbose* (:verbose opts)
                  miraj/*keep* (:keep opts)
                  miraj.co-dom/*pprint* (:pprint opts)
                  *compile-path* (.getPath (doto (io/file html-workspace out-pfx) io/make-parents))]
          (miraj/compile-webcomponent-vars-html opts)) ;; pprint verbose))
        (target-handler (-> fileset
                            (cljs-handler cljs-workspace)
                            (boot/add-resource html-workspace)
                            boot/commit!))))))

(defn- compile-component-nss
  "Compile webcomponent namespaces."
  [opts] ;; debug keep pprint verbose]
  (if (or (:debug opts) (:verbose opts))
    (util/info (format "Running fn 'compile-component-nss' for %s\n" opts)))
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [html-workspace (boot/tmp-dir!)
            cljs-workspace (boot/tmp-dir!)
            cljs-handler (if (:keep opts) boot/add-resource boot/add-source)
            out-pfx "" ;;"assets"
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (binding [miraj/*debug* (:debug opts)
                  miraj/*verbose* (:verbose opts)
                  miraj/*keep* (:keep opts)
                  miraj.co-dom/*pprint* (:pprint opts)
                  *compile-path* (.getPath cljs-workspace)]
          (miraj/compile-webcomponents-cljs opts))
        (binding [miraj/*debug* (:debug opts)
                  miraj/*verbose* (:verbose opts)
                  miraj/*keep* (:keep opts)
                  miraj.co-dom/*pprint* (:pprint opts)
                  *compile-path* (.getPath (doto (io/file html-workspace out-pfx) io/make-parents))]
          (miraj/compile-webcomponents-html opts))
        (target-handler (-> fileset
                            (cljs-handler cljs-workspace)
                            (boot/add-resource html-workspace)
                            boot/commit!))))))


(defn- link-component-libs
  "Link webcomponent namespaces."
  [namespace-set keep debug pprint verbose]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info (format "Running fn 'link-component-libs' for %s\n" namespace-set)))
      (let [workspace (boot/tmp-dir!)
            ;; html-workspace (boot/tmp-dir!)
            ;; cljs-workspace (boot/tmp-dir!)
            add-handler (if keep boot/add-resource boot/add-source)
            out-pfx "" ;;"assets"
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (binding [miraj/*debug* debug
                  miraj/*verbose* verbose
                  miraj/*keep* keep
                  miraj.co-dom/*pprint* pprint
                  *compile-path* (.getPath workspace)]
          (miraj/link-libraries namespace-set))
          ;; (miraj/link-component-libs namespace-set))
        (target-handler (-> fileset
                            (add-handler workspace)
                            boot/commit!))))))

(defn- link-libraries
  "Link component libraries (deflibrary)."
  [opts]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if (:verbose opts) (util/info (format "Running fn 'link-libraries' for %s\n" opts)))
      (let [workspace (boot/tmp-dir!)
            ;; html-workspace (boot/tmp-dir!)
            ;; cljs-workspace (boot/tmp-dir!)
            add-handler (if (:keep opts) boot/add-resource boot/add-source)
            out-pfx "" ;;"assets"
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (binding [miraj/*debug* (:debug opts)
                  miraj/*verbose* (:verbose opts)
                  miraj/*keep* (:keep opts)
                  miraj.co-dom/*pprint* (:pprint opts)
                  *compile-path* (.getPath workspace)]
          (miraj/link-libraries (:libraries opts)))
        (target-handler (-> fileset
                            (add-handler workspace)
                            boot/commit!))))))

(defn- compile-page-vars
  "Compile page vars."
  [page-var-set opts]
  (if (:debug opts) (util/info (format "Running fn 'compile-page-vars %s'\n" page-var-set)))
  (if (:debug opts) (util/info (format "opts: %s\n" opts)))
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [workspace   (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (doseq [[idx pv] (map-indexed vector page-var-set)]
          (let [ns (symbol (->  pv namespace))]
            (binding [miraj/*debug* (:debug opts)
                      miraj/*verbose* (or (:verbose opts) (:debug opts))
                      miraj/*keep* (:keep opts)
                      miraj.co-dom/*pprint* (or (:debug opts) (:pprint opts))
                      *compile-path* (.getPath workspace)]
              (require ns)
              (miraj/compile-page-ref (find-var pv)))))
        (target-handler (-> fileset (boot/add-resource workspace) boot/commit!))))))

;; (defn- compile-page
;;   "Compile page."
;;   [page-ref opts]
;;   (if (:debug opts) (util/info (format "Running fn 'compile-page %s'\n" page-ref)))
;;   (if (:debug opts) (util/info (format "opts: %s\n" opts)))
;;   (fn middleware [next-handler]
;;     (fn handler [fileset]
;;       (let [vopts (mapcat identity (into [] opts))
;;             _ (util/info (format "VOPTS %s\n" (seq vopts)))
;;             workspace   (boot/tmp-dir!)
;;             target-middleware identity
;;             target-handler (target-middleware next-handler)]
;;         (let [ns (if-let [nsp (namespace page-ref)]
;;                    (symbol nsp)
;;                    ;; must be an ns symbol already
;;                    page-ref)]
;;             (binding [miraj/*debug* (:debug opts)
;;                       miraj/*verbose* (or (:verbose opts) (:debug opts))
;;                       miraj/*keep* (:keep opts)
;;                       miraj.co-dom/*pprint* (or (:debug opts) (:pprint opts))
;;                       *compile-path* (.getPath workspace)]
;;               (require ns :reload)
;;               (miraj/mcc vopts))
;;             (target-handler
;;              (-> fileset (boot/add-resource workspace) boot/commit!)))))))

(defn- compile-pages
  "Compile defpages"
  ;; FIXME: to support watch loop, only compile changed namespaces
  [opts] ;; debug pprint verbose]
  (let [namespace-set (:pages opts)]
    ;; (util/info (format "namespace-set: %s\n" (seq namespace-set)))
    (fn middleware [next-handler]
      (fn handler [fileset]
        ;; (if (or (:debug opts) (:verbose opts))
        ;;   (util/info (format "Running fn 'compile-pages' for %s\n" opts)))
        ;; (if (or (:debug opts) (:verbose opts))
        ;;   (util/info (format "opts %s\n" opts)))

        ;; filter for pagespaces/pagenss
        (let [page-nss namespace-set
              ;; page-nss (filter (fn [maybe-pns]
              ;;                    (util/info "maybe pagespace %s\n" maybe-pns (var? maybe-pns))
              ;;                    ;; (clojure.core/remove-ns maybe-pns)
              ;;                    (clojure.core/require (if (var? maybe-pns)
              ;;                                            (-> maybe-pns meta :ns)
              ;;                                            maybe-pns)) ;; :reload)
              ;;                    (let [maybe-page-ns (find-ns maybe-pns)
              ;;                          ;; _ (util/info " maybe pagespace ns %s\n" maybe-page-ns)
              ;;                          ;; _ (util/info " pagespace meta %s\n"
              ;;                          ;;              (-> maybe-page-ns clojure.core/meta))
              ;;                          ps (or (-> maybe-page-ns clojure.core/meta
              ;;                                     :miraj/miraj :miraj/pagespace)
              ;;                                 (-> maybe-page-ns clojure.core/meta
              ;;                                     :miraj/miraj :miraj/defpage))]
              ;;                      (util/info "pagespace? %s\n" ps)
              ;;                      ps))
              ;;                      namespace-set)
              opts (assoc opts :pages page-nss)
              ]
           ;; (util/info (format "Page-Nss: %s\n" (seq page-nss)))
           (if page-nss
             (let [workspace (boot/tmp-dir!)
                   target-middleware identity
                   target-handler (target-middleware next-handler)]
               ;; (if (:debug opts) (clojure.core/require '[miraj.co-dom]
               ;;                                         '[miraj.core]
               ;;                                         :reload))
               (binding [miraj/*debug* (:debug opts)
                         miraj/*verbose* (or (:debug opts) (:verbose opts))
                         miraj/*keep* (:keep opts)
                         miraj.co-dom/*pprint* (or (:debug opts) (:pprint opts))
                         *compile-path* (.getPath workspace)]
                 ;; (miraj/compile-pages namespace-set #_page-nss opts))
                 ;; (require ns :reload)
                 (miraj/mcc opts))
               (target-handler (-> fileset (boot/add-resource workspace) boot/commit!)))))))))

(defn- compile-pagespaces
  "Compile pagespaces"
  [opts] ;; debug pprint verbose]
  (let [namespace-set (:pages opts)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (if (or (:debug opts) (:verbose opts))
          (util/info (format "Running fn 'compile-pagespaces' for %s\n" opts)))
        (if (or (:debug opts) (:verbose opts))
          (util/info (format "opts %s\n" opts)))

        ;; filter for pagespaces
        ;; (let [page-nss (filter (fn [maybe-pns]
        ;;                          (util/info "maybe pagespace %s\n" maybe-pns)
        ;;                          ;; (clojure.core/remove-ns maybe-pns)
        ;;                          ;; (clojure.core/require maybe-pns :reload)
        ;;                          (let [maybe-page-ns (find-ns maybe-pns)
        ;;                                _ (util/info " maybe pagespace ns %s\n" maybe-page-ns)
        ;;                                _ (util/info " pagespace meta %s\n"
        ;;                                             (-> maybe-page-ns clojure.core/meta))
        ;;                                ps (-> maybe-page-ns clojure.core/meta
        ;;                                       :miraj/miraj :miraj/pagespace)]
        ;;                            (util/info "pagespace? %s\n" ps)
        ;;                            ps))
        ;;                            namespace-set)]
        ;;   (util/info (format "Page-Nss: %s\n" (seq page-nss)))
        (let [workspace (boot/tmp-dir!)
              target-middleware identity
              target-handler (target-middleware next-handler)]
          ;; (if (:debug opts) (clojure.core/require '[miraj.co-dom]
          ;;                                         '[miraj.core]
          ;;                                         :reload))
          (binding [miraj/*debug* (:debug opts)
                    miraj/*verbose* (or (:debug opts) (:verbose opts))
                    miraj/*keep* (:keep opts)
                    miraj.co-dom/*pprint* (or (:debug opts) (:pprint opts))
                    *compile-path* (.getPath workspace)]
            ;; (miraj/compile-pages namespace-set #_page-nss opts))
            ;; (require ns :reload)
            (miraj/mcc opts))
          (target-handler (-> fileset (boot/add-resource workspace) boot/commit!)))))))

(defn- assetize
  "Install assets from miraj dependencies."
  [dep]
  (util/info (format "Running fn 'assetize' for %s\n" dep))
  (fn middleware [next-handler]
    (fn handler [fileset]
      (let [workspace (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)
            env (boot/get-env)
            ;; pod          (-> env
            ;;                  pod/make-pod  ;; use pod-pool?
            ;;                  future)
            deps  (seq (:dependencies env))
            _ (util/info "DEPS: " deps)
            deps (filter #(= "miraj" (:scope (pod/coord->map %))) deps)
            _ (set (for [d deps]
                     (util/info "MIRAJ DEP: " d)))
            webjars (set (for [d deps] (pod/resolve-dependency-jar env d)))
            ]
        (util/info "webjars: " webjars)
        (let [out-dir (io/file workspace)]
          (doseq [webjar webjars]
            (pod/unpack-jar webjar out-dir)))
        (-> fileset (boot/add-asset workspace :exclude [#"META-INF.*"]) boot/commit!)))))

(defn- link-pages
  "link page namespaces"
  [namespace-set opts] ;; debug pprint verbose]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if (:verbose opts) (util/info (format "Running fn 'link-pages' for %s\n" namespace-set)))
      (let [workspace (boot/tmp-dir!)
            assets-workspace (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)

            ;; FIXME: only do this once, only if polymer assets actually used
            newfs (if (= (:assets opts) :polymer)
                    (do (util/info (format "Copying assets\n"))
                        (let [env (boot/get-env)
                              ;; pod          (-> env
                              ;;                  pod/make-pod  ;; use pod-pool?
                              ;;                  future)
                              deps  (seq (:dependencies env))
                              ;; _ (util/info "DEPS: %s\n" deps)
                              deps (filter #(= 'miraj.polymer/assets
                                               (first %))
                                           deps)
                              _ (set (for [d deps]
                                       (util/info "MIRAJ DEP: %s\n" d)))
                              assets (set (for [d deps] (pod/resolve-dependency-jar env d)))
                              ]
                          ;; (util/info "assets: %s\n" assets)
                          (let [out-dir (io/file workspace)]
                            (doseq [asset assets]
                              (util/info "UNPACKING asset: %s\n" asset)
                              (pod/unpack-jar asset out-dir)))
                          (-> fileset (boot/add-asset workspace
                                                      :exclude [#"META-INF.*"])
                              boot/commit!)))
                    fileset)]
        (binding [miraj/*debug* (:debug opts)
                  miraj/*verbose* (or (:debug opts) (:verbose opts))
                  miraj/*keep* (:keep opts)
                  miraj.co-dom/*pprint* (or (:debug opts) (:pprint opts))
                  *compile-path* (.getPath workspace)]
          (miraj/link-pages namespace-set opts))
        (target-handler (-> newfs
                            (boot/add-resource workspace)
                            boot/commit!))))))

(defn- compile-test-pages
  "Generate test pages for component libs"
  [namespace-set keep pprint verbose]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info (format "Running fn 'compile-test-pages' for %s\n" namespace-set)))
      (let [workspace (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (binding [miraj/*debug* true
                  miraj/*verbose* verbose
                  miraj/*keep* keep
                  miraj.co-dom/*pprint* pprint
                  *compile-path* (.getPath workspace)]
          (miraj/create-test-pages namespace-set))
        (target-handler (-> fileset (boot/add-resource workspace) boot/commit!))))))

(defn- link-lib-testpage
  "Generate and link test page for component lib"
  [namespace-set debug keep pprint verbose]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info (format "Running fn 'link-lib-testpage' for %s\n" namespace-set)))
      (let [workspace (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (require '[miraj.compiler] :reload)
        (binding [miraj/*debug* debug
                  miraj/*verbose* verbose
                  miraj/*keep* keep
                  miraj.co-dom/*pprint* pprint
                  *compile-path* (.getPath workspace)]
          (miraj/create-lib-test-pages namespace-set))
        (target-handler (-> fileset (boot/add-resource workspace) boot/commit!))))))

(defn- link-test-pages
  "Generate and link test page for component lib"
  [namespace-set debug keep pprint verbose]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info (format "Running fn 'link-test-pages' for %s\n" namespace-set)))
      (let [workspace (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (require '[miraj.compiler] :reload)
        (binding [miraj/*debug* debug
                  miraj/*verbose* verbose
                  miraj/*keep* keep
                  miraj.co-dom/*pprint* pprint
                  *compile-path* (.getPath workspace)]
          (miraj/link-test-pages namespace-set))
        (target-handler (-> fileset (boot/add-resource workspace) boot/commit!))))))

#_(boot/deftask assemble
  "Processes deflibrary vars to link webcomponent libraries."
  [n namespace NS sym "namespace for library (NOT the implementation namespace of the components)."
   p pprint     bool        "Pretty-print generated HTML."
   v verbose bool "verbose"]
  (fn middleware [next-handler]
    (fn handler [fileset]
      (if verbose (util/info (format "Running task 'assemble' for namespace %s\n" namespace)))
      (let [all-nses (->> fileset boot/fileset-namespaces)]
        (doseq [app-ns all-nses]
          (util/dbug "Requiring ns: " app-ns)
          (require app-ns)))
      (let [workspace (boot/tmp-dir!)
            target-middleware identity
            target-handler (target-middleware next-handler)]
        (binding [*compile-path* (.getPath workspace)]
          (miraj/assemble-component-lib-for-ns namespace pprint verbose))
        (target-handler (-> fileset (boot/add-resource workspace) boot/commit!))))))

#_(boot/deftask assetize
  "Install assets from miraj dependencies."
  [v verbose bool "Print trace messages"]

  ;; [c configs EDN  edn "config spec"
  ;;  n namespaces NSS #{str} "root dir for component output"
  ;;  r root    PATH str "root dir for component output"]
  (println "TASK: miraj/assetize")
  (let [workspace (boot/tmp-dir!)
        env (boot/get-env)
        ;; pod          (-> env
        ;;                  pod/make-pod  ;; use pod-pool?
        ;;                  future)
        deps  (seq (:dependencies env))
        _ (println "DEPS: " deps)
        deps (filter #(= "miraj" (:scope (pod/coord->map %))) deps)
        _ (set (for [d deps] (println "MIRAJ DEP: " d)))


        webjars (set (for [d deps] (pod/resolve-dependency-jar env d)))
        ]
    (println "webjars: " webjars)
    (boot/with-pre-wrap fileset
      (let [out-dir (io/file workspace)]
        (doseq [webjar webjars]
          (pod/unpack-jar webjar out-dir)))
      (-> fileset (boot/add-asset workspace :exclude [#"META-INF.*"]) boot/commit!))))

(boot/deftask compile
  "Compile miraj components, pages, etc. Default is to compile
  everything in all namespaces. Use -m to compile one miraj var, -n to
  compile all miraj vars in a namespace."
  [c components COMPS #{sym}      "Compile webcomponents."
   d debug      bool        "Debug mode - pretty-print, keep, etc."
   k keep       bool        "Keep transient work products (e.g. cljs files)."
   i imports    IMPORTS [str] "Import resources"
   l libraries  bool        "Generate Clojure libraries from webcomponents.edn files."
   m miraj-var  SYM  #{sym} "Compile miraj var."
   p pages      PAGES #{sym} "Compile defpages (#{} for all)"
   ;; P pagespaces NS  #{sym}  "Compile all defpages in pagespace NSs (#{} for all)"
   ;; _ page       PAGE sym    "Compile specific page"
   _ pprint     bool        "Pretty print"
   _ polyfill   POLYFILL kw "Compile with polyfill"
   _ source-paths    SRC  #{str} "Paths to add to :source-paths"
   s styles     bool        "Compile webstyles.edn files."
   t test       bool        "Generate test page."
   v verbose    bool        "verbose"]
  ;;(let [all (and (empty? pagespace) (empty? miraj-var))
  ;; default: do all components and pages
  (if (or debug verbose) (util/info "Running task 'compile' with %s\n" *opts*))
  (let [
        ;; pod-env (update-in (boot/get-env) [:dependencies]
        ;;                    conj '[boot/core "RELEASE"]
        ;;                    ;; '[tmp/components "0.1.0-SNAPSHOT"]
        ;;                    '[org.clojure/clojure "1.9.0-alpha16"]
        ;;                    '[miraj/core "0.1.0-SNAPSHOT"]
        ;;                    '[miraj/co-dom "0.1.0-SNAPSHOT"]
        ;;                    '[stencil "0.5.0"])
        ;; pod-env (if (nil? source-paths)
        ;;           pod-env
        ;;           (update-in pod-env [:source-paths] clojure.set/union source-paths))
        ;; pod (future (pod/make-pod pod-env))
        ;; _ (pod/with-eval-in @pod
        ;;     (require '[boot.core :as boot]
        ;;              '[boot.pod :as pod]
        ;;              '[boot.util :as util])
        ;;                  ;; '[clojure.java.io :as io] '[clojure.string :as str]
        ;;                  ;; '[clojure.java.shell :refer [sh]])
        ;;     (util/info "POD srcs: %s\n"  (:source-paths pod/env))
        ;;     (util/info "POD CP: %s\n"  (keys pod/env)))


        ;; do-components (if components true (if (and (not pages)
        ;;                                            (not libraries) (not styles))
        ;;                                     true false))
        ;; do-pages (if (not (nil? pages))
        ;;            true
        ;;            (if (and (not components) (not libraries)
        ;;                     (not styles))
        ;;              true false))
        ;; do-libraries (if libraries true (if (and (not components) (not pages)
        ;;                                          (not styles))
        ;;                                   true false))
        ;; do-styles (if styles true (if (and (not components) (not pages)
        ;;                                    (not styles))
        ;;                             true false))
        opts (assoc *opts*
                    :keep (or keep debug)
                    :pprint (or pprint debug)
                    :verbose (or verbose debug))]
    (fn middleware [next-handler]
      (fn handler [fileset]
        #_(pod/with-eval-in @pod
          (require '[boot.core :as boot]
                   '[boot.task.built-in :as builtin]
                   '[boot.pod :as pod]
                   '[boot.util :as util])
          (pod/add-classpath "src/page")
          ;; (util/info "OPTS %s\n" ~opts)
          (util/info "POD srcs: %s\n"  (:source-paths pod/env))
          (util/info "POD Fake CP: %s\n"
                     (clojure.string/replace (:fake-class-path pod/env) ":" "\n"))
          (println (clojure.string/join "\n" (seq (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))))

        (doseq [p source-paths]
          (pod/add-classpath p))

          (let [;; opts ~opts
                target-middleware (comp
                                 (if (:keep opts)
                                   (builtin/sift :to-resource #{(re-pattern ".*cljs")})
                                   identity)

                                 (if (:components opts)
                                   (if (empty? (:components opts))
                                     (compile-component-nss (assoc
                                                             (dissoc opts :components)
                                                             :namespaces
                                                             (->> fileset boot/fileset-namespaces)))
                                     (compile-components opts))
                                   identity)

                                 (if pages
                                   (if (empty? pages)
                                     (compile-pagespaces (assoc (dissoc opts :pages)
                                                                :pagespaces
                                                              (->> fileset boot/fileset-namespaces)))
                                     (compile-pages opts)) ;; namespace debug #_keep pprint verbose))
                                   identity)

                                 (if test (compile-test-pages (->> fileset boot/fileset-namespaces)
                                                              keep pprint verbose)
                                     identity)

                                 (if libraries (compile-libraries verbose) identity)

                                 ;; (if (not (empty? namespace))
                                 ;;   (compile-pages namespace pprint verbose)
                                 ;;   identity)

                                 (if styles (compile-styles verbose) identity)

                                 ;; (if page ;; (not (empty? miraj-var))
                                 ;;   (compile-page page opts)
                                 ;;   identity)

                                 (if (or components libraries
                                         pages styles miraj-var)
                                   identity
                                   (do
                                     (util/warn (str "WARNNING: nothing compiled. Please specify --components, --libraries, --page, --pages, or --styles.\n"))
                                     identity)))
              target-handler (target-middleware next-handler)]
          (target-handler fileset))))))

(boot/deftask demo-page
  "Generate a master demo page for a component library."
  [d debug    bool       "debug"
   p pprint   bool       "pprint"
   v verbose  bool       "verbose"]
  (if verbose (util/info (format "Running task 'demo-page'\n")))
  (let [pprint (or debug false)
        verbose (or debug verbose)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [workspace   (boot/tmp-dir!)
              nss (->> fileset boot/fileset-namespaces)
              _ (util/info (format "NSS %s\n" (seq nss)))
              target-middleware identity
              target-handler (target-middleware next-handler)]
          (binding [miraj/*debug* debug
                    miraj/*verbose* verbose
                    miraj/*keep* false
                    miraj.co-dom/*pprint* pprint
                    *compile-path* (.getPath workspace)]
            (miraj/create-master-demo-page nss))
          (target-handler (-> fileset
                              (boot/add-resource workspace)
                              boot/commit!)))))))

(boot/deftask link
  "Processes deflibrary vars to link webcomponent libraries."
  [;;p pprint     bool     "Pretty-print generated HTML."
   a assets     ASSETS kw  "Copy assets from jar to resources dir"
   c components bool       "Link components"
   d debug      bool       "Debug mode - keep, pprint, verbose, etc."
   l libraries  LIBS #{sym} "Link component libraries."
   n namespace  NS  #{sym} "Link all miraj vars in namespace NS."
   p pages      NS  #{sym} "Link pages (use #{} for all)"
   _ pprint     bool        "Pretty print"
   t test       bool       "Generate and link test webpage"
   v verbose    bool       "verbose"]
  (if (or debug verbose) (util/info "Running task 'link' with %s\n" *opts*))
  (let [pprint (or debug false)
        verbose (or debug verbose)]
    (fn middleware [next-handler]
      (fn handler [fileset]
        (let [do-components (if components true (if (and (not libraries) (not pages))
                                                         true false))
              do-libraries (if libraries true (if (and (not components) (not pages))
                                                true false))
              do-pages (if pages true (if (and (not components) (not libraries))
                                        true false))
              workspace (boot/tmp-dir!)
              target-middleware (comp
                                 (if do-components
                                   (if namespace
                                     (link-component-libs namespace keep debug pprint verbose)
                                     (link-component-libs (->> fileset boot/fileset-namespaces)
                                                          keep debug pprint verbose))
                                   identity)

                                 (if libraries
                                   (if (empty? libraries)
                                     (link-libraries (assoc *opts*
                                                            :libraries
                                                            (->> fileset boot/fileset-namespaces)))
                                     (link-libraries namespace keep debug pprint verbose))
                                   identity)

                                 (if do-pages
                                   (if (empty? pages)
                                     (link-pages (->> fileset boot/fileset-namespaces)
                                                 *opts*) ;; #_keep pprint test verbose))
                                     (link-pages pages *opts*)) ;; #_keep pprint test verbose)
                                   identity)

                                 (if test
                                   (if do-libraries
                                     (link-lib-testpage (->> fileset boot/fileset-namespaces)
                                                        debug keep pprint verbose)
                                     (if do-pages
                                       (link-test-pages (->> fileset boot/fileset-namespaces)
                                                        debug keep pprint verbose)
                                       identity))
                                   identity))

              target-handler (target-middleware next-handler)]
          (target-handler fileset))))))

(boot/deftask config
  "config component resources for web app"
  [c configs EDN  edn "config spec"
   n namespaces NSS #{str} "root dir for component output"
   r root    PATH str "root dir for component output"]
  (println "TASK: miraj/config")
  (let [tmp-dir (boot/tmp-dir!)
        pod          (-> (boot/get-env)
                         pod/make-pod  ;; use pod-pool?
                         future)]
    (boot/with-pre-wrap fileset
      (doseq [[ns-sym config] configs]
        (println "config: " ns-sym config)
        (require ns-sym)
        (let [ns-interns (ns-interns ns-sym)]
          (doseq [[csym cvar] ns-interns]
            (if (:component (meta cvar))
              (let [path (str/join "/" [(str/replace (str (:ns (meta cvar))) #"\." "/")])
                    html (str csym ".html")
                    cljs (str csym ".cljs")
                    html-in-path (str/join "/" ["miraj" path html])
                    cljs-in-path (str/join "/" ["miraj" path cljs])
                    html-out-path (str/join "/" [(.getPath (io/file tmp-dir))
                                                 (:resources config)
                                                 path csym html])
                    cljs-out-path (str/join "/" [(.getPath (io/file tmp-dir))
                                                 (:resources config)
                                                 path csym cljs])]
                (println "COMPONENT: " cvar)
                (println "\t" html-in-path " -> " html-out-path)
            (pod/with-eval-in @pod
              (require '[boot.pod :as pod])
              (pod/copy-resource ~html-in-path ~html-out-path)
              (pod/copy-resource ~cljs-in-path ~cljs-out-path))))))
        (boot/sync! root tmp-dir))
        fileset)))

(boot/deftask install-polymer
  "Cache bower polymer packages"
  [c clean-cache bool "clean reinstall (empty cache at start)"
   p pkg-name PKG str "package name"
   t pkg-type PGKMGR kw "one of :bower, :npm :polymer, or :webjars; default is all 4)"
   v verbose bool "verbose"]
  (println "install-polymer: " pkg-name pkg-type)
  (let [workspace     (boot/tmp-dir!)
        bower-cache (boot/cache-dir! :bowdlerize/bower :global true)
        _ (if clean-cache (do (if verbose (util/info (str "Cleaning bower cache\n")))
                              (boot/empty-dir! bower-cache)))
        local-bower  (io/as-file "./node_modules/bower/bin/bower")
        global-bower (io/as-file "/usr/local/bin/bower")
        bcmd        (cond (.exists local-bower) (.getPath local-bower)
                          (.exists global-bower) (.getPath global-bower)
                          :else "bower")]
    (boot/with-pre-wrap fileset
      (boot/empty-dir! workspace)
      (let [deps (->> (boot/get-env) :dependencies)
            polymers  (filter #(= :polymer (:webcomponents (util/dep-as-map %))) deps)
            _ (doseq [p polymers] (println "polymer: " p))

            pod-env (update-in (boot/get-env) [:dependencies]
                               #(identity %2)
                               '[[boot/aether "RELEASE"]
                                 [boot/core "RELEASE"]
                                 [boot/pod "RELEASE"]])
            pod (future (pod/make-pod pod-env))

            ;; [bowdlerize-f edn-content pod] (->bowdlerize-pod fileset)
            ;; bower-specs {pkg-type (get edn-content pkg-type)}
            ;; bower-pkgs (if pkg-name
            ;;              (list pkg-name)
            ;;              (get-bower-pkgs bower-specs))
            ]
        ;; (println "bower-specs: " bower-specs)
        ;; (println "bower-pkgs: " bower-pkgs)
        #_(if (empty? bower-pkgs)
          fileset
          (do (if verbose (util/info (str "Installing " pkg-type " packages\n")))
              (pod/with-eval-in @pod
                (require '[boot.pod :as pod] '[boot.util :as util]
                         '[clojure.java.io :as io] '[clojure.string :as str]
                         '[clojure.java.shell :refer [sh]])
                (doseq [bower-pkg '~bower-pkgs]
                  (let [_ (println "PKG: " bower-pkg)
                        seg (subs (str (first bower-pkg)) 1)
                        path (str ~bower-repo "/" (last bower-pkg))
                        repo-file (io/file ~(.getPath bower-cache) seg)]
                    (println "REPO-FILE: " repo-file)
                    ;;(println "PATH: " path)
                    (if (.exists repo-file)
                      (if ~verbose (util/info (format "Found cached bower pkg: %s\n" bower-pkg)))
                      (let [c [~bcmd "install" (fnext bower-pkg) :dir ~(.getPath bower-cache)]]
                        (println "bower cmd: " c)
                        (if ~verbose (util/info (format "Installing bower pkg:   %s\n" bower-pkg)))
                        (apply sh c)))))))))
      (-> fileset (boot/add-asset bower-cache) boot/commit!))))

#_(defn hcompile-pod-env
  [current-env]
  (assoc current-env
         :directories (boot/env->directories current-env)
         :dependencies (concat (:dependencies current-env)
                               @@(resolve 'boot.repl/*default-dependencies*))
         :middleware @@(resolve 'boot.repl/*default-middleware*)))

(boot/deftask webdeps
  "Download and cache web resource dependencies."
  [c clean       bool  "clear cache"
   k keep                     bool  "keep intermediate products"
   n ns-str          NS       str  "namespace from which to extract commponents"
   r root-output-dir PATH     str  "relative root of html and cljs output paths. Default: 'miraj'"
   a assets-output-dir PATH     str  "relative root fro assets output. Default: '.'"
   m html-output-dir PATH     str  "relative root for HTML output. Default '.'"
   j cljs-output-dir PATH     str  "relative root for cljs output. Default '.'"
   v verbose bool "verbose"]
   ;; ack: https://github.com/Lambda-X/lambone/blob/master/resources/leiningen/new/lambone/common/dev/boot.clj
   ;; 1. launch repl
   ;; 2. refresh (ctn)
   ;; 3  miraj/hcompile
  (println "TASK: webdeps")
  (let [;; pod-env (hcompile-pod-env (:env options))
        ;; pod-env (boot/get-env)
        ;; ;; pod-env (update-in (boot/get-env) [:dependencies] conj '[miraj/core "0.1.0-SNAPSHOT"])
        ;; pod (future (pod/make-pod pod-env))
        ;; {:keys [port init-ns]} (:repl options)]

        bower-cache (boot/cache-dir! :bowdlerize/bower :global true)]
    ;; (comp
     ;;(boot/with-pre-wrap fileset
       (apply set-refresh-dirs (-> pod/env :directories))
       (let [cfg-map (miraj/webdeps bower-cache)
             cfg-path (str/join "/" [(.getPath bower-cache) "bowdlerize.edn"])]
         (spit cfg-path cfg-map)
         (println "CONFIG MAP: " cfg-path))))

#_(boot/deftask webc
  "web compile"
  [f miraj-file      FILE     str  "input file. If not present, all .clj files will be processed."
   c component       VAR      str  "component to extract.  Must be namespace qualified."
   k keep                     bool  "keep intermediate products"
   n ns-str          NS       str  "namespace from which to extract commponents"
   r root-output-dir PATH     str  "relative root of html and cljs output paths. Default: 'miraj'"
   a assets-output-dir PATH     str  "relative root fro assets output. Default: '.'"
   m html-output-dir PATH     str  "relative root for HTML output. Default '.'"
   j cljs-output-dir PATH     str  "relative root for cljs output. Default '.'"]
   ;; ack: https://github.com/Lambda-X/lambone/blob/master/resources/leiningen/new/lambone/common/dev/boot.clj
   ;; 1. launch repl
   ;; 2. refresh (ctn)
   ;; 3  miraj/hcompile
  (println "TASK: webc")
  (let [;; pod-env (hcompile-pod-env (:env options))
        pod-env (boot/get-env)
        ;; pod-env (update-in (boot/get-env) [:dependencies] conj '[miraj/core "0.1.0-SNAPSHOT"])
        pod (future (pod/make-pod pod-env))
        ;; {:keys [port init-ns]} (:repl options)]

        bower-cache (boot/cache-dir! :miraj/bower)

        ;; root-dir (if root-output-dir root-output-dir "miraj")
        assets-tmp-dir (boot/tmp-dir!)
        assets-output-dir   (if assets-output-dir (io/file assets-tmp-dir assets-output-dir) assets-tmp-dir)
        assets-output-path  (.getPath assets-output-dir)

        html-tmp-dir (boot/tmp-dir!)
        html-output-dir   (if html-output-dir (io/file html-tmp-dir html-output-dir) html-tmp-dir)
        html-output-path  (.getPath html-output-dir)

        cljs-tmp-dir (boot/tmp-dir!)
        cljs-output-dir   (if cljs-output-dir (io/file cljs-tmp-dir cljs-output-dir) cljs-tmp-dir)
        cljs-output-path  (.getPath cljs-output-dir)
        last-fileset (atom nil)]
    (boot/empty-dir! bower-cache)
    (comp
     ;; (with-pass-thru _
     ;;   (util/dbug "[dev-backend] options:\n%s\n" (with-out-str (pprint options)))
     ;;   (util/dbug "[dev-backend] pod env:\n%s\n" (with-out-str (pprint pod-env))))
     (boot/with-pre-wrap fileset
       (apply set-refresh-dirs (-> pod/env :directories))
       ;; (binding [*ns* *ns*]
       ;;   (let [e (ctnr/refresh)]
       ;;     (if e (println e)
       ;;       (clojure.repl/pst)))
       (let [cfg-map (miraj/compile :html-out html-output-path
                                         :cljs-out cljs-output-path
                                         :assets-out assets-output-path)
             cfg-path (str/join "/" [(.getPath bower-cache) "bowdlerize.edn"])]
         (spit cfg-path cfg-map)
         (println "CONFIG MAP: " cfg-path)

         (-> fileset
             ((fn [fs]
                (if keep
                  (boot/add-resource fs cljs-tmp-dir)
                  (boot/add-source fs cljs-tmp-dir))))
             (boot/add-asset html-tmp-dir)
             (boot/add-asset assets-tmp-dir)
             boot/commit!))))))
      ;; (boot/sync! root-dir tmp-dir)
      ;; fileset)))

(boot/deftask pkg
  "pom, jar, install"
   []
   (comp (builtin/pom) (builtin/jar) (builtin/install)))

;;OBSOLETE
;; (boot/deftask clj2web
;;   "extract and store html and cljs from miraj Clojure component definitions"

;;   [f miraj-file      FILE     str  "input file. If not present, all .clj files will be processed."
;;    c component       VAR      str  "component to extract.  Must be namespace qualified."
;;    n ns-str          NS       str  "namespace from which to extract commponents"
;;    r root-output-dir PATH     str  "relative root of html and cljs output paths. Default: 'miraj'"
;;    m html-output-dir PATH     str  "relative root for HTML output. Default '.'"
;;    j cljs-output-dir PATH     str  "relative root for cljs output. Default '.'"]

;;   (println "TASK: boot-miraj/extract")
;;   (let [root-dir (if root-output-dir root-output-dir "miraj")
;;         tmp-dir (io/file (boot/tmp-dir!) root-dir)
;;         tmp-output-path  (.getPath tmp-dir)
;;         ;; _ (println "tmp-dir: " tmp-dir)
;;         html-output-dir   (if html-output-dir (io/file tmp-dir html-output-dir) tmp-dir)
;;         html-output-path  (.getPath html-output-dir)
;;         cljs-output-dir   (if cljs-output-dir (io/file tmp-dir cljs-output-dir) tmp-dir)
;;         cljs-output-path  (.getPath cljs-output-dir)
;;         last-fileset (atom nil)
;;         pod          (-> (boot/get-env)
;;                          (update-in [:dependencies] into '[[miraj/co-dom "0.1.0-SNAPSHOT"]])
;;                          pod/make-pod  ;; use pod-pool?
;;                          future)]
;;     (boot/with-pre-wrap fileset
;;       (pod/with-eval-in @pod
;;         (require '[miraj.co-dom :as miraj])
;;         (do
;;           (if ~ns-str
;;             (let [ns-sym (symbol ~ns-str)]
;;               (require ns-sym)
;;               (let [interns (ns-interns ns-sym)
;;                     bld (resolve 'miraj.co-dom/build-component)]
;;                 (doseq [[isym ivar] interns]
;;                   (if (:component (meta ivar))
;;                     (miraj.co-dom/build-component [~html-output-path ~cljs-output-path]
;;                                                   [isym ivar]))))))
;;           (if ~component
;;             (do
;;               (boot.util/info "processing var: " ~component "\n")
;;               (let [widget (symbol ~component)
;;                     ns-sym (symbol (namespace widget))
;;                     nm (symbol (name widget))]
;;                 (require ns-sym)
;;                 (let [ivar (resolve widget)]
;;                   (if (:component (meta ivar))
;;                     (miraj/build-component [~html-output-path ~cljs-output-path]
;;                                            [nm ivar]))))))))
;;       (boot/sync! root-dir tmp-dir)
;;       fileset)))

;;     (if miraj-file
;;       (do (println "processing file: " miraj-file)
;;           (boot/with-pre-wrap fileset
;;             (let [last-fileset-val @last-fileset
;;                   all-files        (->> fileset
;;                                         (boot/fileset-diff last-fileset-val)
;;                                         boot/input-files
;;                                         (boot/by-re [#".*"]))
;;                   files             (->> fileset
;;                                          (boot/fileset-diff last-fileset-val)
;;                                          boot/input-files
;;                                          (boot/by-ext [".clj"]))
;;                   nss (boot/fileset-namespaces fileset)]
;;               (reset! last-fileset fileset)
;;               (when (seq files)
;;                 (util/info "Building components ... %d changed files.\n" (count files))
;;                 (doseq [ns-sym nss]
;;                   (println "Processing ns: " ns-sym)
;;                   (pod/with-eval-in @pod
;;                     (require '[miraj.co-dom :as miraj]
;;                              '~ns-sym)
;;                     (let [interns (ns-interns '~ns-sym)]
;;                       (doseq [[isym ivar] interns]
;;                         #_(println "\t" isym ivar)
;;                         (if (:component (meta ivar))
;;                           (miraj/build-component [~html-output-path ~cljs-output-path]
;;                                                  [isym ivar])))))))
;;               (boot/sync! root-dir tmp-dir)
;;               fileset))))

;;     (if (and (not miraj-file) (not ns-str) (not component))
;;       (do (println "DEFAULT processing")
;;           (boot/with-pre-wrap fileset
;;             (let [last-fileset-val @last-fileset
;;                   all-files        (->> fileset
;;                                         (boot/fileset-diff last-fileset-val)
;;                                         boot/input-files
;;                                         (boot/by-re [#".*"]))
;;                   files             (->> fileset
;;                                          (boot/fileset-diff last-fileset-val)
;;                                          boot/input-files
;;                                          (boot/by-ext [".clj"]))
;;                   nss (boot/fileset-namespaces fileset)]
;;               (reset! last-fileset fileset)
;;               (when (seq files)
;;                 (util/info "Building components ... %d changed files.\n" (count files))
;;                 (doseq [ns-sym nss]
;;                   (println "Processing ns: " ns-sym)
;;                   (pod/with-eval-in @pod
;;                     (require '[miraj.co-dom :as miraj]
;;                              '~ns-sym)
;;                     (let [interns (ns-interns '~ns-sym)]
;;                       (doseq [[isym ivar] interns]
;;                         #_(println "\t" isym ivar)
;;                         (if (:component (meta ivar))
;;                           (miraj/build-component [~html-output-path ~cljs-output-path]
;;                                                  [isym ivar])))))))
;;               (boot/sync! root-dir tmp-dir)
;;               fileset))))))
;; ;; (-> (boot/new-fileset)
;;         ;;     (boot/add-resource tmp-dir)
;;         ;;     boot/commit!)))))

(boot/deftask dummy
  "pom, jar, install"
   []
  identity)
