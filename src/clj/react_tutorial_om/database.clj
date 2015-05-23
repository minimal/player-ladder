(ns react-tutorial-om.database
  (:require [clj-time
             [coerce :refer [to-timestamp]]
             [core :as time]]
            [clojure
             [edn :as edn]
             [pprint :refer [pprint]]]
            [com.stuartsierra.component :as component]
            [react-tutorial-om.schemas :as sch]
            [schema.core :as s]))

(s/defn ^:always-validate update-ladder-match
  "Coerce the data into the format we want and add date"
  [match :- sch/Result]
  (-> match
      ;; TODO: coerce data earlier
      (update :winner clojure.string/lower-case)
      (update :loser clojure.string/lower-case)
      (assoc :date (to-timestamp (time/now)))))

(s/defn ^:always-validate update-ladder-results :- sch/AllResults
  [match :- sch/Result
   doc :- sch/AllResults]
  (update-in doc [:singles-ladder] conj (update-ladder-match match)))

(s/defn ^:always-validate  save-ladder-match!* ;; TODO: write to a db
  [db match :- sch/Result]
  (swap! db (partial update-ladder-results match)))

(s/defn ^:always-validate update-league-result
  "Update the state map with the result of the league match. Remove
  match from schedule and also update the ladder with the result"
  [result :- sch/LeagueResult league doc]
                                        ;TODO: validate not inactive
  (-> doc
      (update-in [:leagues league :schedule]
                 (fn [sch] (vec (remove #(= (:id %) (:id result)) sch))))
      (update-in [:leagues league :matches]
                 conj (assoc result :date (to-timestamp (time/now))))
      ((partial update-ladder-results (-> result
                                          (dissoc :id :round)
                                          (assoc :competition league))))))


(defn load-edn-file [file]
  (-> (slurp file)
      (edn/read-string)
      ((partial s/validate sch/AllResults))))

(defn spit-edn-file [file data]
  (spit file (with-out-str (pprint data))))

(defn init!
  [db db-file]
  (reset! db (load-edn-file db-file)))

(defprotocol Database
  (get-leagues [db])
  (save-league-result! [db result league])
  (save-league-schedule-match! [db match league])
  (get-ladder-matches [db])
  (save-ladder-match! [db result]))

(s/defrecord AtomDatabase
    [db-file :- (s/maybe s/Str)
     db :- (s/maybe clojure.lang.Atom)]
  component/Lifecycle
  (start [component]
         (if-not db
           (let [db (atom {})]
             (when db-file
               (let [file-agent (agent nil :error-handler println)]
                 (init! db db-file)
                 (add-watch db :writer (fn [_ _ _ new]
                                         (send-off file-agent (fn [_] (spit-edn-file db-file new)))))))
             (assoc component
                    :db db))
           component))
  (stop [component]
        ;; TODO: remove-watch
        (assoc component :db nil))
  Database
  (get-leagues [{:keys [db]}]
               (:leagues @db))
  (save-league-result! [{:keys [db]} result league]
                       (swap! db (partial update-league-result result league)))
  (save-league-schedule-match!
   [{:keys [db]} match league]
   (swap! db (fn [a] (update-in a [:leagues league :schedule]
                                #(conj % match)))))
  (get-ladder-matches [{:keys [db]}]
                      (:singles-ladder @db))
  (save-ladder-match! [{:keys [db]} result]
                      (save-ladder-match!* db result)))

(s/defschema AtomDatabaseSchema
  {(s/optional-key :db-file) (s/maybe s/Str)
   (s/optional-key :db) (s/maybe clojure.lang.Atom)})

(defn map->new-database [data]
  (map->AtomDatabase (s/validate AtomDatabaseSchema data)))
