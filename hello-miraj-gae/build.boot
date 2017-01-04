(def +project+ 'miraj/gae-miraj-boot)
(def +version+ "0.1.0-SNAPSHOT")

(set-env!
 :gae {:app-id +project+
       :version +version+}
 :asset-paths #{"resources/public"}
 :resource-paths #{"src/clj" "filters"} ;; "src/main/clojure/" "config"}
 :source-paths #{"config"}
 :repositories {"clojars" "https://clojars.org/repo"
                "maven-central" "http://mvnrepository.com"
                "central" "http://repo1.maven.org/maven2/"}
 :dependencies   '[[org.clojure/clojure "1.8.0" :scope "runtime"]
                   [boot/core "2.5.2" :scope "provided"]
                   [mobileink/boot-bowdlerize "0.1.0-SNAPSHOT" :scope "test"]
                   [migae/boot-gae "0.1.0-SNAPSHOT" :scope "test"]
                   [miraj/boot-miraj "0.1.0-SNAPSHOT" :scope "test"]
                   [miraj/html "5.1.0-SNAPSHOT"]
                   [miraj/markup "0.1.0-SNAPSHOT"]
                   [polymer/paper "1.2.3-SNAPSHOT"]
                   [polymer/iron "1.2.3-SNAPSHOT"]
                   ;; [components/greetings "0.1.0-SNAPSHOT"]
                   ;; [boot/core "2.5.2" :scope "provided"]
                   ;; [adzerk/boot-test "1.0.7" :scope "test"]
                   [com.google.appengine/appengine-java-sdk RELEASE :scope "provided" :extension "zip"]
                   ;; we need this so we can import KickStart:
                   [com.google.appengine/appengine-tools-sdk "1.9.32"]
                   [javax.servlet/servlet-api "2.5"]
                   [org.clojure/math.numeric-tower "0.0.4"]
                   [compojure/compojure "1.4.0"]
                   [ring/ring-core "1.4.0"]
                   [ring/ring-devel "1.4.0"]
                   [ring/ring-servlet "1.4.0"]
                   [ring/ring-defaults "0.1.5"]
                   [ns-tracker/ns-tracker "0.3.0"]
                   ])

#_(def gae
  ;; web.xml doco: http://docs.oracle.com/cd/E13222_01/wls/docs81/webapp/web_xml.html
  {;; :build-dir ; default: "build";  gradle compatibility: "build/exploded-app"
   ;; :sdk-root ; default: ~/.appengine-sdk; gradle compatibility: "~/.gradle/appengine-sdk"
   :list-tasks true
   ;; :verbose true
   :module "foo"})

(require '[migae.boot-gae :as gae]
         '[boot-bowdlerize :as b]
         '[boot-miraj :as mrj]
         '[boot.task.built-in])

(def configs #{'resources/styles 'bower/config-map 'polymer/config-map})

;;(def build #{"build"})

(task-options!
 ;; gae/config-appengine {:config-syms #{'gae.appengine/config
 ;;                                      'gae.appstats/config
 ;;                                      'gae.version/config}}
 ;; gae/config-webapp {:config-syms #{'gae.appstats/config
 ;;                                   ;; 'gae.filters/config
 ;;                                   'gae.security/config
 ;;                                   'gae.servlets/config
 ;;                                   'gae.webapp/config
 ;;                                   'gae.version/config}
 ;;                    :reloader true}
 ;; gae/servlets {:config-sym 'gae.servlets/config}
 b/config {:nss configs}
 b/install {:nss configs}
 ;; mrj/config {:root "build/exploded-app"
 ;;             :configs {'components.greetings
 ;;                       {:ns 'miraj.greetings :resources "miraj_components"}}}
 pom  {:project     +project+
       :version     +version+
       :description "Example code, boot, miraj, GAE"
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

;; (deftask prep
;;   "run all the boot-gae prep tasks"
;;   [a save bool "save aot source?"]
;;   (comp (gae/logging)
;;         (gae/config)
;;         ;; (target :dir #{"build"} :no-clean true)
;;         (gae/servlets :save save)
;;         ;; (sift :include #{#"class$"} ;; retain transient clj files
;;         ;;               :move {#"(.*class$)" "WEB-INF/classes/$1"})
;;         (gae/assets :type :clj))) ;; :odir "WEB-INF/classes")
;;         ;; (target)
;;         ;; #_(gae/run)))
