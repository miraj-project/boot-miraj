(def +project+ 'miraj/boot-miraj)
(def +version+ "0.1.0-SNAPSHOT")

(refer 'clojure.core :exclude [compile])

(set-env!
 :resource-paths #{"src/clj"}
 ;;:source-paths #{"src/clj"}

 ;; needed for developing core and co-com?
 ;; :checkouts '[[miraj/core "0.1.0-SNAPSHOT"]
 ;;             [miraj/co-dom "0.1.0-SNAPSHOT"]]

 :dependencies   '[[org.clojure/clojure RELEASE :scope "provided"]
                   [org.clojure/tools.namespace "0.2.11"]
                   ;; [miraj/core "0.1.0-SNAPSHOT"]
                   ;; [miraj/co-dom "0.1.0-SNAPSHOT" :scope "provided"]
                   [stencil "0.5.0"]

                   [boot/core "RELEASE" :scope "provided"]
;;                   [boot/util "RELEASE" :scope "provided"]
                   [adzerk/boot-test "1.0.7" :scope "test"]
                   ;; Webjars-locator uses logging
                   ;; [org.slf4j/slf4j-nop "1.7.12" :scope "test"]
                   ;; [org.webjars/webjars-locator "0.29"]
                   ;; For testing the webjars asset locator implementation
                   #_[org.webjars/bootstrap "3.3.6" :scope "test"]
                   ])

(require ;; '[miraj.boot-miraj :refer :all]
         '[adzerk.boot-test :refer [test]])

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

#_(deftask run-tests []
  (comp
   (test :namespaces #{'less4clj.core-test 'less4clj.webjars-test})))

(deftask monitor
  "watch etc."
  []
  (comp (watch)
        (notify :audible true)
        (pom)
        (jar)
        (install)))
