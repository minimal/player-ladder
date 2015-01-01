(ns user
  (:require [cemerick.piggieback :as piggieback]
            [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [leiningen.core.main :as lein]
            [react-tutorial-om.core :as core]
            [react-tutorial-om.system :as system]
            [reloaded.repl :refer [system init start stop go reset]]
            [weasel.repl.websocket :as weasel]))

(reloaded.repl/set-init! system/make-system)

(defn browser-repl []
  (let [repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)]
    (piggieback/cljs-repl :repl-env repl-env)
    (piggieback/cljs-eval repl-env '(in-ns 'react-tutorial-om.core) {})))

(def fig-future (atom nil))

(defn start-figwheel []
  (when-not @fig-future
    (reset! fig-future
            (future
              (print "Starting figwheel.\n")
              (lein/-main ["figwheel"])))))

(defn stop-figwheel []
  (when fig-future
    (swap! fig-future
           (fn [f] (future-cancel f) nil))))
