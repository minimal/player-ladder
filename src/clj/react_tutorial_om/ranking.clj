(ns react-tutorial-om.ranking
  (:require [react-tutorial-om.schemas :as sch]
            [schema.core :as s]))

(def DefaultRank
  {:loses 0
   :wins 0
   :draw 0
   :points 0
   :for 0
   :against 0
   :matches []})

(defn new-DefaultRank [team]
  (assoc DefaultRank :team team))

(defn match->winner-match [match]
  {:date (:date match)
   :for (:winner-score match)
   :against (:loser-score match)
   :opposition (:loser match)
   :round (:round match)})

(defn match->loser-match [match]
  {:date (:date match)
   :for (:loser-score match)
   :against (:winner-score match)
   :opposition (:winner match)
   :round (:round match)})

(defn sort-ranks [ranks]
  (reverse (sort-by (fn [x] [(:points x) (- (:for x) (:against x))]) ranks)))

(s/defn ^:always-validate process-match :- {s/Str sch/LeagueRanking}
  "Update rankings based on match. Create a rank for a team if it
  doesn't exist"
  [rankings :- {s/Str sch/LeagueRanking}
   match :- sch/Result]
  (let [winner (:winner match)
        loser (:loser match)
        rankings (if (contains? rankings winner)
                   rankings
                   (assoc rankings winner (new-DefaultRank winner)))
        rankings (if (contains? rankings loser)
                   rankings
                   (assoc rankings loser (new-DefaultRank loser)))]
    (-> rankings
        (update-in [winner :wins] inc)
        (update-in [winner :points] inc)
        (update-in [winner :for] + (:winner-score match))
        (update-in [winner :against] + (:loser-score match))
        (update-in [winner :matches] conj (match->winner-match match))
        (update-in [loser :loses] inc)
        (update-in [loser :against] + (:winner-score match))
        (update-in [loser :for] + (:loser-score match))
        (update-in [loser :matches] conj (match->loser-match match)))))

(s/defn ^:always-validate matches->league-ranks :- [sch/LeagueRanking]
  "Turn raw matches into per player rankings"
  [matches :- [sch/Result]]
  (sort-ranks (vals (reduce process-match {} matches))))
