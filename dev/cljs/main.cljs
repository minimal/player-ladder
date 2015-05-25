(ns ladder.main
  (:require [ladder.app :as app]
            [figwheel.client :as fw :include-macros true]
            [ladder.utils :refer [guid] :refer-macros [logm]]
            ;; [devtools.core :as devtools]
            [weasel.repl :as weasel]))

;; (devtools/install!)

(defn run-refresh []
  (let [root (app/get-hash)]
    (logm root)
    (logm app/last-hash)
    ;; (s/with-fn-validation)
    (app/run-top-level)))

(enable-console-print!)
(fw/watch-and-reload
 :websocket-url   "ws://localhost:3449/figwheel-ws"
 :before-jsload (fn [files]
                  (reset! app/last-hash (app/get-hash))
                  (logm app/last-hash)
                  (fw/default-before-load files))
 :on-jsload (fn []
              (run-refresh)))
(weasel/connect "ws://localhost:9001" :verbose true :print #{:repl :console})

(app/run-top-level)
