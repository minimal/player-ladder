(defproject react-tutorial-om "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[clj-time "0.8.0"]
                 [cljs-http "0.1.23"]
                 [com.matthiasnehlsen/inspect "0.1.5"]
                 [com.stuartsierra/component "0.2.2"]
                 [compojure "1.2.2"]
                 [enlive "1.1.5"]
                 [environ "1.0.0"]
                 [metosin/compojure-api "0.16.6" :exclude [ring-middleware-format]]
                 [org.omcljs/om "0.8.8"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2760"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.reader "0.8.14"]
                 [prismatic/om-tools "0.3.10"]
                 [prismatic/schema "0.3.7"]
                 [prone "0.8.1"]
                 [ranking-algorithms "0.1.0-SNAPSHOT"]
                 [ring "1.3.2"]
                 [metosin/ring-middleware-format "0.5.0"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-defaults "0.1.4"]
                 [sablono "0.3.4"]
                 [secretary "1.2.1"]
                 [slingshot "0.12.2"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]
                 [weasel "0.5.0"]] ;; 0.6.0 Requires ClojureScript 0.0-2814 or newer

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-environ "1.0.0"]
            [lein-figwheel "0.2.2-SNAPSHOT"]]

  :ring {:handler react-tutorial-om.core/app
         :init    react-tutorial-om.core/init}



  :source-paths ["src/clj" "src/cljs" "target/classes"]
  ;; :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]

  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [expectations "2.0.16"]
                                  [figwheel "0.2.2-SNAPSHOT"]
                                  [figwheel-sidecar "0.2.2-SNAPSHOT"]
                                  [com.cemerick/piggieback "0.1.5"]
                                  ;; [omdev "0.1.3-SNAPSHOT"]
                                  [spellhouse/clairvoyant "0.0-48-gf5e59d3"]
                                  [org.clojure/tools.namespace "0.2.9"]
                                  [reloaded.repl "0.1.0"]
                                  [ring-mock "0.1.5"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]

                   :figwheel {:http-server-root "public" ;; resources/public
                              :port 3449
                              :css-dirs ["resources/public/css"]}}

             :uberjar {:aot [react-tutorial-om.system]
                       :main react-tutorial-om.system}}

  :cljsbuild {:test-commands {"node" ["node" :node-runner "resources/public/js/app.js"]}
              :builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/js/app.js"
                                   :output-dir "resources/public/js/out"
                                   :optimizations :none
                                   :source-map true
                                   :preamble ["react/react.min.js"]
                                   :externs ["react/externs/react.js"]
                                   }}
                       {:id "release"
                        :source-paths ["src/cljs"]
                        :compiler {
                                   :output-to "resources/public/js/app.js"
                                   :source-map "resources/public/js/app.js.map"
                                   :optimizations :advanced
                                   :pretty-print false
                                   :output-wrapper false
                                   :preamble ["om/react.min.js"]
                                   :externs ["om/externs/react.js"]
                                   :closure-warnings
                                   {:non-standard-jsdoc :off}}}]})
