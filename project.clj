(defproject player-ladder "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :source-paths ["src/clj"]
  :dependencies [[clj-time "0.11.0"]
                 [clj-http "2.0.0"]
                 [cljs-http "0.1.40"]
                 [potemkin "0.4.3"]
                 [com.stuartsierra/component "0.3.1"]
                 [compojure "1.5.0"]
                 [buddy/buddy-auth "0.5.3"]
                 [environ "1.0.3"]
                 [metosin/compojure-api "0.21.0"]
                 [org.omcljs/om "0.8.8"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [org.clojure/tools.reader "0.10.0" :classifier "aot"]
                 [org.clojure/data.json "0.2.6" :classifier "aot"]
                 [org.clojure/core.async "0.2.374"]
                 [prismatic/om-tools "0.4.0"]
                 [prismatic/schema "1.1.1"]
                 [prone "1.1.1"]
                 [ring "1.4.0"]
                 [metosin/ring-middleware-format "0.6.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [sablono "0.3.5"]
                 [secretary "1.2.3"]
                 [slingshot "0.12.2"]
                 [log4j "1.2.17" :exclusions [javax.mail/mail
                                              javax.jms/jms
                                              com.sun.jdmk/jmxtools
                                              com.sun.jmx/jmxri]]

                 [org.clojure/core.match "0.3.0-alpha4"]]

  #_:repositories #_[["sonatype-oss-public"
                      "https://oss.sonatype.org/content/groups/public/"]]
  :aliases {"test" ["expectations"]}
  :plugins [[lein-cljsbuild "1.1.3"]
            [lein-cljfmt "0.5.3"]
            [lein-environ "1.0.3"]
            [venantius/yagni "0.1.4"]
            [lein-expectations "0.0.8"]
            [lein-autoexpect "1.9.0"]
            [lein-figwheel "0.5.3-2"]]
  :cljfmt {:indents {do-template [[:block 1]]
                     context [[:block 1]]
                     swaggered [[:block 1]]
                     div [[:block 1]]}}
  :clean-targets ^{:protect false} ["resources/public/js/app.js"
                                    "resources/public/js/out"
                                    "out"
                                    :target-path]
  ;;  ≠ ≡ ≢ ≣ ≤ ≥ ≦ ≧ ≨ ≩ ≪ ≫ ≬ ≭ ≮ ≯
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [expectations "2.1.8"]
                                  [figwheel-sidecar "0.5.3-2"]
                                  [figwheel "0.5.3-2"]
                                  [com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                                  ;; [omdev "0.1.3-SNAPSHOT"]
                                  ;;[spellhouse/clairvoyant "0.1.0-SNAPSHOT"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [reloaded.repl "0.1.0"]
                                  [clojurescript-build "0.1.9"]
                                  [ring-mock "0.1.5"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :cljsbuild {:builds {:app
                                        {:source-paths ["dev/cljs" "src/clj"]}}}
                   :figwheel {:http-server-root "public"
                              :nrepl-port 7888
                              :css-dirs ["resources/public/css"]}}

             :uberjar {:aot [ladder.system]
                       :main ladder.system
                       :hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :cljsbuild {:builds {:app
                                            {:source-paths ["src/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}}

  :cljsbuild {:test-commands {"node" ["node" :node-runner "resources/public/js/app.js"]}
              :builds {:app {:source-paths ["src/clj"]
                             :figwheel {:on-jsload "ladder.main/run-refresh"
                                        :before-jsload "ladder.main/before-load-hook"}
                             :compiler {:output-to "resources/public/js/app.js"
                                        :output-dir "resources/public/js/out"
                                        :source-map true
                                        :externs ["react/externs/react.js"]
                                        :optimizations :none
                                        :main "ladder.main"
                                        :asset-path "js/out"
                                        :cache-analysis true
                                        :pretty-print  true}}}})
