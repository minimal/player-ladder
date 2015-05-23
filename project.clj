(defproject react-tutorial-om "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :source-paths ["src/clj" "src/cljs" "src/cljc" "target/classes"]
  :dependencies [[clj-time "0.9.0"]
                 [clj-http "1.1.2"]
                 [cljs-http "0.1.30"]
                 [com.stuartsierra/component "0.2.3"]
                 [compojure "1.3.4"]
                 [buddy/buddy-auth "0.5.3"]
                 [environ "1.0.0"]
                 [metosin/compojure-api "0.20.4"]
                 [org.omcljs/om "0.8.8"]
                 [org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/clojurescript "0.0-3291" :classifier "aot" :exclusions
                  [org.clojure/tools.reader org.clojure/data.json]]
                 [org.clojure/tools.reader "0.9.2" :classifier "aot"]
                 [org.clojure/data.json "0.2.6" :classifier "aot"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [prismatic/om-tools "0.3.11"]
                 [prismatic/schema "0.4.3"]
                 [prone "0.8.2"]
                 [ring "1.3.2"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-defaults "0.1.5"]
                 [sablono "0.3.4"]
                 [secretary "1.2.3"]
                 [slingshot "0.12.2"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]

                 [org.clojure/core.match "0.3.0-alpha4"]]

  :aliases {"test" ["expectations"]}
  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-cljfmt "0.1.10"]
            [lein-environ "1.0.0"]
            [lein-expectations "0.0.8"]
            [lein-autoexpect "1.4.2"]
            #_[lein-figwheel "0.2.5" :exclusions [org.clojure/tools.nrepl]]]

  :cljfmt {:indents {do-template [[:block 1]]
                     context [[:block 1]]
                     swaggered [[:block 1]]
                     div [[:block 1]]}}
  :clean-targets ^{:protect false} ["resources/public/js/app.js"
                                    "resources/public/js/out"
                                    "out"
                                    :target-path]
  ;; :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]

  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [expectations "2.0.16"]
                                  [weasel "0.6.0" :exclusions [org.clojure/clojurescript]]
                                  [figwheel-sidecar "0.3.3"
                                   :exclusions [org.clojure/clojurescript]]
                                  [figwheel "0.3.3"
                                   :exclusions [org.clojure/clojurescript]]
                                  [com.cemerick/piggieback "0.2.1"
                                   :exclusions [org.clojure/clojurescript]]
                                  ;; [omdev "0.1.3-SNAPSHOT"]
                                  [spellhouse/clairvoyant "0.0-48-gf5e59d3"]
                                  [org.clojure/tools.namespace "0.2.10"]
                                  [org.clojure/tools.nrepl "0.2.10"]
                                  [reloaded.repl "0.1.0"]
                                  [ring-mock "0.1.5"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :figwheel {:http-server-root "public" ;; resources/public
                              :port 3449
                              :css-dirs ["resources/public/css"]}}

             :uberjar {:aot [react-tutorial-om.system]
                       :main react-tutorial-om.system
                       :hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :cljsbuild {:builds {:app
                                            {:source-paths ["src/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}}

  :cljsbuild {:test-commands {"node" ["node" :node-runner "resources/public/js/app.js"]}
              :builds {:app {:source-paths ["src/cljs" "src/cljc"]
                             :compiler {:output-to "resources/public/js/app.js"
                                        :output-dir "resources/public/js/out"
                                        :source-map "resources/public/js/out.js.map"
                                        :externs ["react/externs/react.js"]
                                        :optimizations :none
                                        :main "react-tutorial-om.main"
                                        :cache-analysis true
                                        :pretty-print  true}}}}

  )
