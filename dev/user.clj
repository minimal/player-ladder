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
            expectations
            [figwheel-sidecar.auto-builder :as fig-auto]
            [figwheel-sidecar.core :as fig]
            [react-tutorial-om.core :as core]
            [react-tutorial-om.system :as system]
            [reloaded.repl :refer [system init start stop go reset]]
            [weasel.repl.websocket :as weasel]))

(reloaded.repl/set-init! #(system/make-system {:is-dev? true :db-file "results.edn"}))

(defn browser-repl []
  (let [repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)]
    (piggieback/cljs-repl repl-env)))

(defonce fig-server (atom nil))
(defonce fig-builder (atom nil))
(defonce fig-config (atom nil))

(comment (defn file-exists? [path]
           (.exists (io/file path))))

(def base-fig-config
  {:builds [{:source-paths ["dev/cljs" "src/cljs" "src/cljc"]
             :compiler {:output-to "resources/public/js/app.js"
                        :output-dir "resources/public/js/out"
                        :source-map "resources/public/js/out.js.map"
                        :source-map-timestamp true
                        :main "react-tutorial-om.main"
                        :asset-path "js/out"
                        :preamble ["react/react.min.js"]}}]
   :figwheel-server nil})

(defn start-figwheel []
  (let [server (fig/start-server {:css-dirs ["resources/public/css"]})
        config (assoc base-fig-config :figwheel-server server)
        builder (fig-auto/autobuild* config)]
    (reset! fig-config config)
    (reset! fig-server server)
    (reset! fig-builder builder)))

(defn stop-auto-build! []
  (auto/stop-autobuild! @fig-builder))

(defn- mark-tests-as-unrun []
  (let [all (->> (all-ns)
                 (mapcat (comp vals ns-interns)))
        previously-ran-tests (filter (comp :expectations/run meta) all)]
    (doseq [test previously-ran-tests]
      (alter-meta! test dissoc :expectations/run :status))))

(defn reset-run-tests []
  (reset)
  (mark-tests-as-unrun)
  (expectations/run-all-tests))
