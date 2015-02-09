(ns user
  (:require [cemerick.piggieback :as piggieback]
            [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojurescript-build.auto :as auto]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [figwheel-sidecar.auto-builder :as fig-auto]
            [figwheel-sidecar.core :as fig]
            [react-tutorial-om.core :as core]
            [react-tutorial-om.system :as system]
            [reloaded.repl :refer [system init start stop go reset]]
            [weasel.repl.websocket :as weasel]))

(reloaded.repl/set-init! #(system/make-system true))

(defn browser-repl []
  (let [repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)]
    (piggieback/cljs-repl :repl-env repl-env)
    (piggieback/cljs-eval repl-env '(in-ns 'react-tutorial-om.core) {})))

(defonce fig-server (atom nil))
(defonce fig-builder (atom nil))

(defn start-figwheel []
  (let [server (fig/start-server { :css-dirs ["resources/public/css"] })
        config {:builds [{:source-paths ["src/cljs"]
                          :compiler {:output-to     "resources/public/js/app.js"
                                     :output-dir    "resources/public/js/out"
                                     :source-map    "resources/public/js/out.js.map"
                                     :preamble      ["react/react.min.js"]}}]
                :figwheel-server server}
        builder (fig-auto/autobuild* config)]
    (reset! fig-server server)
    (reset! fig-builder builder)))

