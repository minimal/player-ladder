(defproject react-tutorial-om "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :source-paths ["src/clj" "src/cljs" "target/classes"]
  :dependencies [[clj-time "0.9.0"]
                 [clj-http "1.0.1"]
                 [cljs-http "0.1.27"]
                 [com.matthiasnehlsen/inspect "0.1.11"]
                 [com.stuartsierra/component "0.2.3"]
                 [compojure "1.3.2"]
                 [enlive "1.1.5"]
                 [environ "1.0.0"]
                 [metosin/compojure-api "0.18.0" :exclude [ring-middleware-format prismatic/plumbing]]
                 [org.omcljs/om "0.8.8"]
                 [org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/clojurescript "0.0-3058"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.reader "0.8.16"]
                 [prismatic/om-tools "0.3.10"]
                 [prismatic/schema "0.3.7"]
                 [prone "0.8.1"]
                 [figwheel "0.2.2-SNAPSHOT"]
                 [ring "1.3.2"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-defaults "0.1.4"]
                 [sablono "0.3.4"]
                 [secretary "1.2.1"]
                 [slingshot "0.12.2"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [weasel "0.6.0"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-cljfmt "0.1.10"]
            [lein-environ "1.0.0"]
            [lein-expectations "0.0.8"]
            [lein-figwheel "0.2.2-SNAPSHOT"]]

  :clean-targets ^{:protect false} ["resources/public/js/app.js"
                                    "resources/public/js/out"]
  ;; :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]

  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [expectations "2.0.16"]
                                  [figwheel "0.2.2-SNAPSHOT"]
                                  [figwheel-sidecar "0.2.2-SNAPSHOT"]
                                  [com.cemerick/piggieback "0.1.5"]
                                  ;; [omdev "0.1.3-SNAPSHOT"]
                                  [spellhouse/clairvoyant "0.0-48-gf5e59d3"]
                                  [org.clojure/tools.namespace "0.2.10"]
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
                                            {;;:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}}

  :cljsbuild {:test-commands {"node" ["node" :node-runner "resources/public/js/app.js"]}
              :builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to "resources/public/js/app.js"
                                        :output-dir "resources/public/js/out"
                                        :source-map "resources/public/js/out.js.map"
                                        :preamble ["react/react.min.js"]
                                        :externs ["react/externs/react.js"]
                                        :optimizations :none
                                        :cache-analysis true
                                        :pretty-print  true}}}}

  )
