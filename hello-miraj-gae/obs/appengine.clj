(ns gae.appengine)

(def config
  {:appengine {:thread-safe true
               ;; :public-root "/static"
               :system-properties {:props [{:name "myapp.maximum-message-length" :value "140"}
                                           {:name "myapp.notify-every-n-signups" :value "1000"}
                                           {:name"myapp.notify-url"
                                            :value "http://www.example.com/supnotfy"}]}
               ;; :env-vars [{:name "FOO" :value "BAR"}]
               :logging {:jul {:name "java.util.logging.config.file"
                               :value "WEB-INF/logging.properties"}}
               ;; #_{:log4j {:name "java.util.logging.config.file"
               ;;          :value "WEB-INF/classes/log4j.properties"}}}
               :sessions true
               :ssl true
               :async-session-persistence {:enabled "true" :queue-name "myqueue"}
               :inbound-services [{:service :mail} {:service :warmup}]
               :precompilation true
               :scaling {:basic {:max-instances 11 :idle-timeout "10m"
                                 :instance-class "B2"}
                         :manual {:instances 5
                                  :instance-class "B2"}
                         :automatic {:instance-class "F2"
                                     :idle-instances {:min 5
                                                      ;; ‘automatic’ is the default value.
                                                      :max "automatic"}
                                     :pending-latency {:min "30ms" :max "automatic"}
                                     :concurrent-requests {:max 50}}}
               ;; :resource-files {:include [{:path "**.xml"
               ;;                            :expiration "4d h5"
               ;;                            :http-header {:name "Access-Control-Allow-Origin"
               ;;                                          :value "http://example.org"}}]
               ;;                  :exclude [{:path "feed/**.xml"}]}
               ;; :static-files {:include {:path "foo/**.png"
               ;;                          :expiration "4d h5"
               ;;                          :http-header {:name "Access-Control-Allow-Origin"
               ;;                                        :value "http://example.org"}}
               ;;                :exclude {:path "bar/**.zip"}}
               }})