(ns react-tutorial-om.database
  (:require [clojure
             [edn :as edn]
             [pprint :refer [pprint]]]
            [com.stuartsierra.component :as component]
            [react-tutorial-om.schemas :as sch]
            [schema.core :as s]))

(defn load-edn-file [file]
  (-> (slurp file)
      (edn/read-string)
      ((partial s/validate sch/AllResults))))

(defn spit-edn-file [file data]
  (spit file (with-out-str (pprint data))))

(defn init!
  [db db-file]
  (reset! db (load-edn-file db-file)))

(defrecord AtomDatabase [db-file]
  component/Lifecycle
  (start [component]
    (let [db (atom {})
          file-agent (agent nil :error-handler println)]
      (init! db db-file)
      (add-watch db :writer (fn [_ _ _ new]
                              (send-off file-agent (fn [_] (spit-edn-file db-file new)))))
      (assoc component
             :db db)))
  (stop [component]
    ;; TODO: remove-watch
    (assoc component :db nil)))
