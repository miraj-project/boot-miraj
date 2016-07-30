(def +project+ 'miraj/boot-miraj)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
  :resource-paths #{"src"}
  :source-paths #{"src"}
  :dependencies   '[[org.clojure/clojure "1.8.0" :scope "provided"]
                    [org.clojure/tools.namespace "0.2.11"]
                    [miraj/miraj "0.1.0-SNAPSHOT" :scope "provided"]
                    [miraj/markup "0.1.0-SNAPSHOT" :scope "provided"]
                    [boot/core "RELEASE" :scope "provided"]
                    [adzerk/boot-test "1.0.7" :scope "test"]
                    ;; Webjars-locator uses logging
                    ;; [org.slf4j/slf4j-nop "1.7.12" :scope "test"]
                    ;; [org.webjars/webjars-locator "0.29"]
                    ;; For testing the webjars asset locator implementation
                    [org.webjars/bootstrap "3.3.6" :scope "test"]])

(require '[adzerk.boot-test :refer [test]]
         '[boot-miraj :refer :all])

(task-options!
  pom  {:project     +project+
        :version     +version+
        :description "Boot for miraj"
        :url         "https://github.com/miraj-project/boot-miraj.git"
        :scm         {:url "https://github.com/miraj-project/boot-miraj.git"}
        :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})



(defn with-files
  "Runs middleware with filtered fileset and merges the result back into complete fileset."
  [p middleware]
  (fn [next-handler]
    (fn [fileset]
      (let [merge-fileset-handler (fn [fileset']
                                    (next-handler (commit! (assoc fileset :tree (merge (:tree fileset) (:tree fileset'))))))
            handler (middleware merge-fileset-handler)
            fileset (assoc fileset :tree (reduce-kv
                                          (fn [tree path x]
                                            (if (p x)
                                              (assoc tree path x)
                                              tree))
                                          (empty (:tree fileset))
                                          (:tree fileset)))]
        (handler fileset)))))

(deftask write-version-file
  [n namespace NAMESPACE sym "Namespace"]
  (let [d (tmp-dir!)]
    (fn [next-handler]
      (fn [fileset]
        (let [f (clojure.java.io/file d (-> (name namespace)
                                            (clojure.string/replace #"\." "/")
                                            (clojure.string/replace #"-" "_")
                                            (str ".clj")))]
          (clojure.java.io/make-parents f)
          (spit f (format "(ns %s)\n\n(def +version+ \"%s\")" (name namespace) +version+)))
        (next-handler (-> fileset (add-resource d) commit!))))))

#_(deftask build []
  (comp
   (pom
    :project 'miraj/boot-miraj
    :description "Boot task to cocompile miraj web components"
    :dependencies [])
   (write-version-file :namespace 'boot-miraj.version)
   (jar)
   (install)))

  ;; (comp
  ;;  #_(with-files
  ;;      (fn [x] (re-find #"less4clj" (tmp-path x)))
  ;;      (comp
  ;;       (pom
  ;;        :project 'deraen/less4clj
  ;;        :description "Clojure wrapper for Less4j.")
  ;;       (jar)
  ;;       (install)))

  ;;  (with-files
  ;;   (fn [x] (re-find #"src" (tmp-path x)))
  ;;   (comp
  ;;    (pom
  ;;     :project 'miraj/boot-miraj
  ;;     :description "Boot task to cocompile miraj web components"
  ;;     :dependencies [])
  ;;    (write-version-file :namespace 'boot-miraj.version)
  ;;    (jar)
  ;;    (install)))

  ;;  #_(with-files
  ;;   (fn [x] (re-find #"leiningen" (tmp-path x)))
  ;;   (comp
  ;;    (pom
  ;;     :project 'deraen/lein-less4j
  ;;     :description "Leinigen task to compile {less}"
  ;;     :dependencies [])
  ;;    (write-version-file :namespace 'leiningen.less4j.version)
  ;;    (jar)
  ;;    (install)))))

#_(deftask dev []
  (comp
   (watch)
   (repl :server true)
   (build)
   (target)))

#_(deftask deploy []
  (comp
   (build)
   (push :repo "clojars" :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))

(deftask run-tests []
  (comp
   (test :namespaces #{'less4clj.core-test 'less4clj.webjars-test})))
