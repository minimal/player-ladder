(ns ladder.main
  (:require [ladder.app :as app]
            [figwheel.client :as fw :include-macros true]
            [ladder.utils :refer [guid] :refer-macros [logm]]
            ;; [devtools.core :as devtools]
            ))

;; (devtools/install!)

(defn run-refresh []
  (let [root (app/get-hash)]
    (logm root)
    (logm app/last-hash)
    ;; (s/with-fn-validation)
    (app/run-top-level)))

(defn before-load-hook [files]
  (reset! app/last-hash (app/get-hash))
  (logm app/last-hash)
  (fw/default-before-load files))

(enable-console-print!)

(app/run-top-level)
