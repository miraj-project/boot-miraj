(ns boot-miraj
  {:boot/export-tasks true}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :as repl :refer [refresh set-refresh-dirs]]
            [miraj.core :as miraj]
            [miraj.webc :as webc]
            [boot.pod :as pod]
            [boot.core :as boot]
            [boot.util :as util]
            [boot.task.built-in :as builtin]))

            ;; [deraen.boot-less.version :refer [+version+]]))

;; (def ^:private deps
;;   [['deraen/less4clj +version+]])

(defn- find-mainfiles [fs]
  (->> fs
       boot/input-files
       (boot/by-ext [".clj"])))

#_(defn do-build
  [f]
  (println "aot-compile task: " ~(boot/tmp-path f))

  ;; TODO: load the files and filter for components here, THEN call build-components

  (miraj.markup/build-component ~(.getPath (boot/tmp-file f))))

(def ^:private deps
  '[[miraj/markup "0.1.0-SNAPSHOT"]])

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
       (let [cfg-map (miraj.webc/webdeps bower-cache)
             cfg-path (str/join "/" [(.getPath bower-cache) "bowdlerize.edn"])]
         (spit cfg-path cfg-map)
         (println "CONFIG MAP: " cfg-path))))

(boot/deftask webc
  ""
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
       ;;   (let [e (repl/refresh)]
       ;;     (if e (println e)
       ;;       (clojure.repl/pst)))
       (let [cfg-map (miraj.webc/webc :html-out html-output-path
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
(boot/deftask clj2web
  "extract and store html and cljs from miraj Clojure component definitions"

  [f miraj-file      FILE     str  "input file. If not present, all .clj files will be processed."
   c component       VAR      str  "component to extract.  Must be namespace qualified."
   n ns-str          NS       str  "namespace from which to extract commponents"
   r root-output-dir PATH     str  "relative root of html and cljs output paths. Default: 'miraj'"
   m html-output-dir PATH     str  "relative root for HTML output. Default '.'"
   j cljs-output-dir PATH     str  "relative root for cljs output. Default '.'"]

  (println "TASK: boot-miraj/extract")
  (let [root-dir (if root-output-dir root-output-dir "miraj")
        tmp-dir (io/file (boot/tmp-dir!) root-dir)
        tmp-output-path  (.getPath tmp-dir)
        ;; _ (println "tmp-dir: " tmp-dir)
        html-output-dir   (if html-output-dir (io/file tmp-dir html-output-dir) tmp-dir)
        html-output-path  (.getPath html-output-dir)
        cljs-output-dir   (if cljs-output-dir (io/file tmp-dir cljs-output-dir) tmp-dir)
        cljs-output-path  (.getPath cljs-output-dir)
        last-fileset (atom nil)
        pod          (-> (boot/get-env)
                         (update-in [:dependencies] into '[[miraj/markup "0.1.0-SNAPSHOT"]])
                         pod/make-pod  ;; use pod-pool?
                         future)]
    (boot/with-pre-wrap fileset
      (pod/with-eval-in @pod
        (require '[miraj.markup :as miraj])
        (do
          (if ~ns-str
            (let [ns-sym (symbol ~ns-str)]
              (require ns-sym)
              (let [interns (ns-interns ns-sym)
                    bld (resolve 'miraj.markup/build-component)]
                (doseq [[isym ivar] interns]
                  (if (:component (meta ivar))
                    (miraj.markup/build-component [~html-output-path ~cljs-output-path]
                                                  [isym ivar]))))))
          (if ~component
            (do
              (boot.util/info "processing var: " ~component "\n")
              (let [widget (symbol ~component)
                    ns-sym (symbol (namespace widget))
                    nm (symbol (name widget))]
                (require ns-sym)
                (let [ivar (resolve widget)]
                  (if (:component (meta ivar))
                    (miraj/build-component [~html-output-path ~cljs-output-path]
                                           [nm ivar]))))))))
      (boot/sync! root-dir tmp-dir)
      fileset)))

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
;;                     (require '[miraj.markup :as miraj]
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
;;                     (require '[miraj.markup :as miraj]
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
