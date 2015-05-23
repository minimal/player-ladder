(ns react-tutorial-om.schemas
  (:require [clojure.string])
  #?(:cljs (:require [schema.core :as s :refer-macros [defschema]])
     :clj (:require [schema.core :as s :refer [defschema]])))


(defschema Nat
  (s/both s/Int
          (s/pred #(not (neg? %)) "Zero or more")))


(defschema NEmptyStr
  (s/pred #(not (clojure.string/blank? %)) "Non blank string"))

(defschema Result
  "Result is a map of winner/loser names and scores"
  (s/both {:winner s/Str
           :loser s/Str
           :winner-score Nat
           :loser-score Nat
           (s/optional-key :id) s/Int
           (s/optional-key :round) Nat
           (s/optional-key :competition) s/Keyword
           (s/optional-key :date) s/Inst}
          (s/pred (fn [{:keys [winner-score loser-score]}]
                    (> winner-score loser-score))
                  "Winner scores more than loser")))

(defschema LeagueResult
  "Result is a map of winner/loser names and scores"
  (s/both {:winner s/Str
           :loser s/Str
           :winner-score Nat
           :loser-score Nat
           :id s/Int
           :round Nat
           (s/optional-key :date) s/Inst}
          (s/pred (fn [{:keys [winner-score loser-score]}]
                    (> winner-score loser-score))
                  "Winner scores more than loser")))

(defschema Match
  {:opposition s/Str
   :for Nat
   :against Nat
   :round (s/maybe s/Int)
   :date s/Inst})


(defschema Ranking
  {(s/optional-key :rd) (s/maybe s/Int)
   :rank Nat,
   :matches [Match]
   (s/optional-key :round) (s/maybe s/Int)
   :team s/Str
   :suggest s/Str
   :u-wins Nat
   :ranking s/Num
   :draw Nat
   :loses Nat
   :wins Nat})

(defschema RankingsResponse
  {:message s/Str
   :players (s/either [] #{s/Str})
   :rankings [Ranking]})

(defschema LeagueRanking
  {(s/optional-key :rd) (s/maybe s/Int)
   ;; :rank Nat,
   :matches [Match]
   (s/optional-key :round) (s/maybe s/Int)
   (s/optional-key :rank) Nat
   (s/optional-key :change) (s/maybe (s/enum :+ :-))
   :team s/Str
   :draw Nat
   :loses Nat
   :wins Nat
   :for Nat
   :against Nat
   :diff s/Int
   :points Nat})

(defschema LeagueScheduleMatch
  {(s/optional-key :id) s/Int
   (s/optional-key :inactive?) s/Bool
   :round Nat
   :home NEmptyStr
   :away NEmptyStr})

(defschema LeaguesBase
  {:schedule [LeagueScheduleMatch]
   :players [s/Str]
   (s/optional-key :sets-per-match) s/Int
   (s/optional-key :img) (s/maybe s/Str)
   (s/optional-key :bands) (s/maybe {:promotion s/Int
                                     :playoff s/Int
                                     :relegation s/Int})
   :name s/Str})

(defschema LeaguesResponse
  {:leagues {s/Keyword (assoc LeaguesBase
                              :rankings [LeagueRanking])}})

(defschema LeagueStorage
  {s/Keyword (assoc LeaguesBase
                    :matches [Result])})

#?(:clj
   (defschema AllResults
     "Results as stored in edn file"
     {:singles-ladder           [Result]
      (s/optional-key :leagues) LeagueStorage}))

(defn check []
 #?(:clj :clojure
    :cljs :clojurescript))
