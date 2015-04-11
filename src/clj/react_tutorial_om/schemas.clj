(ns react-tutorial-om.schemas
  (:require [schema.core :as s])
  (:import (java.util Date)))


(s/defschema Nat
  (s/both s/Int
          (s/pred #(not (neg? %)) "Zero or more")))

(s/defschema Result
  "Result is a map of winner/loser names and scores"
  (s/both {:winner s/Str
           :loser s/Str
           :winner-score Nat
           :loser-score Nat
           (s/optional-key :id) s/Int
           (s/optional-key :round) Nat
           (s/optional-key :competition) s/Keyword
           (s/optional-key :date) Date}
          (s/pred (fn [{:keys [winner-score loser-score]}]
                    (> winner-score loser-score))
                  "Winner scores more than loser")))

(s/defschema LeagueResult
  "Result is a map of winner/loser names and scores"
  (s/both {:winner s/Str
           :loser s/Str
           :winner-score Nat
           :loser-score Nat
           :id s/Int
           :round Nat
           (s/optional-key :date) Date}
          (s/pred (fn [{:keys [winner-score loser-score]}]
                    (> winner-score loser-score))
                  "Winner scores more than loser")))

(s/defschema Match
  {:opposition s/Str
   :for Nat
   :against Nat
   :round (s/maybe s/Int)
   :date Date})


(s/defschema Ranking
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

(s/defschema RankingsResponse
  {:message s/Str
   :players (s/either [] #{s/Str})
   :rankings [Ranking]})

(s/defschema LeagueRanking
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

(s/defschema LeagueScheduleMatch
  {:id s/Int
   :round Nat
   :home s/Str
   :away s/Str})

(s/defschema LeaguesResponse
  {:leagues {s/Keyword {:rankings [LeagueRanking]
                        :schedule [LeagueScheduleMatch]
                        :name s/Str}}})

(s/defschema LeagueStorage
  {s/Keyword {:matches [Result]
              :schedule [LeagueScheduleMatch]
              :name s/Str}})

(s/defschema AllResults
  "Results as stored in edn file"
  {:singles-ladder [Result]
   (s/optional-key :leagues) LeagueStorage})
