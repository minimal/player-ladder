(defproject react-tutorial-om "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[clj-time "0.8.0"]
                 [cljs-http "0.1.23"]
                 [com.cemerick/piggieback "0.1.3"]
                 [com.matthiasnehlsen/inspect "0.1.1"]
                 [com.stuartsierra/component "0.2.2"]
                 [compojure "1.2.2"]
                 [enlive "1.1.5"]
                 [environ "1.0.0"]
                 [figwheel "0.1.7-SNAPSHOT"]
                 [metosin/compojure-api "0.16.6" :exclude [ring-middleware-format]]
                 [om "0.8.0-beta5"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2505"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/tools.reader "0.8.13"]
                 [prismatic/om-tools "0.3.6"]
                 [prismatic/schema "0.3.3"]
                 [prone "0.8.0"]
                 [ranking-algorithms "0.1.0-SNAPSHOT"]
                 [ring "1.3.2"]
                 [ring-middleware-format "0.4.1-SNAPSHOT"]
                 [ring/ring-core "1.3.2"]
                 [ring/ring-defaults "0.1.3"]
                 [secretary "1.2.1"]
                 [slingshot "0.12.1"]
                 [weasel "0.4.2"]]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [lein-environ "1.0.0"]
            [lein-figwheel "0.1.7-SNAPSHOT"]]

  :ring {:handler react-tutorial-om.core/app
         :init    react-tutorial-om.core/init}

  :repl-options {:init-ns user}
  :main react-tutorial-om.core
  ;; :aot [react-tutorial-om.core]
  :source-paths ["src/clj" "src/cljs"]
  ;; :jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]

  :profiles {:dev { :dependencies [[javax.servlet/servlet-api "2.5"]
                                   [leiningen-core "2.5.0"]
                                   [omdev "0.1.3-SNAPSHOT"]
                                   [spellhouse/clairvoyant "0.0-48-gf5e59d3"]
                                   [org.clojure/tools.namespace "0.2.8"]
                                   [reloaded.repl "0.1.0"]
                                   [ring-mock "0.1.5"]]
                   :source-paths ["dev"]}

             :figwheel {:http-server-root "public" ;; resources/public
                        :port 3449 }}

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
