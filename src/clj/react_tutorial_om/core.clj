(ns react-tutorial-om.core
  (:require [clj-time.coerce :refer [from-date from-string]]
            [clj-time.core :as time]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [compojure.api.routes :refer [with-routes]]
            [compojure.api.sweet
             :refer
             [GET* POST* context swagger-docs swagger-ui swaggered]]
            [compojure.core :refer [GET]]
            [compojure.route :as route]
            [net.cgrand.enlive-html :refer [append deftemplate html prepend set-attr]]
            [prone.debug :refer [debug]]
            [prone.middleware :as prone]
            [ranking-algorithms.core :as rank]
            ring.adapter.jetty
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.util.http-response :as http-resp :refer [ok]]
            [schema.core :as s]
            [slingshot.slingshot :refer [throw+ try+]]))

(def inject-devmode-html
  (comp
     (set-attr :class "is-dev")
     (prepend (html [:script {:type "text/javascript" :src "/js/out/goog/base.js"}]))
     (prepend (html [:script {:type "text/javascript" :src "/react/react.js"}]))
     (append  (html [:script {:type "text/javascript"} "goog.require('react_tutorial_om.app')"]))))

(deftemplate page (io/resource "public/index.html")
  [is-dev?]
  [:body] (if is-dev? inject-devmode-html identity))

(defn recent? [date & [now]]
  (if (nil? date)
    false
    (let [joda-date (or (from-string date) (from-date date))
          offset (time/weeks 4)
          now (or now (time/now))]
      (if (nil? joda-date)
        false
        (time/after? joda-date
                     (time/minus now offset))))))

(defonce results (atom []))

(def db-file "results.edn")

(defn load-edn-file [file]
  (-> (slurp file)
      (edn/read-string)
      vec))

(defn init
  []
  (reset! results (load-edn-file db-file)))

(defn save-match! ;; TODO: write to a db
  [match]
  (let [comment (-> match
                    ;; TODO: coerce data earlier
                    (update-in [:winner] clojure.string/lower-case)
                    (update-in [:loser] clojure.string/lower-case)
                    (assoc :date (java.util.Date.)))]
    (swap! results conj comment)
    (spit db-file (with-out-str (pprint @results))) ;; put in channel?
    {:message "Saved comment!"}))

(defn translate-keys [{:keys [winner winner-score loser loser-score date]}]
  {:home winner
   :home_score winner-score
   :away loser
   :away_score loser-score
   :date date})

(defn calc-ranking-data [matches]
  (map-indexed
   (partial rank/format-for-printing matches)
   (rank/top-teams 30 matches)
   #_(do (prn (rank/top-glicko-teams 30 matches))
         (rank/top-glicko-teams 30 matches {}))))

(defn attach-player-matches [results rankings]
  (for [rank rankings]
    (assoc-in rank [:matches] (rank/show-matches (:team rank) results))))

(defn normalise-indexes
  "Move out of bounds indexes into the next free position

  10 2 [-2 -1 1 2] => (4 3 1 2)
  10 2 [7 8 10 11] => (7 8 5 6)"
  [total offset idxs]
  (for [idx idxs]
    (cond
     (neg? idx) (- offset idx)
     (> (inc idx) total) (- (dec idx) offset offset)
     :else idx)))

(defn suggest-opponent
  "Given a user match history and map of user ranks suggest the next
  oppenent a user should face. Ranks is a vector of people.

  TODO: tidy up"
  [{:keys [rank matches]} ranks]
  (try
    (let [offset 2
          idx-rank (dec rank)
          opps1 (range rank (+ rank offset))
          opps2 (range (- idx-rank offset) idx-rank)
          allopps  (->> (concat opps1 opps2)
                        (normalise-indexes (count ranks) offset))
          oppnames-set (set (map ranks allopps))
          matchfreqs (frequencies (filter #(contains? oppnames-set %)
                                          (map :opposition matches)))
          near-totals (reduce (fn [acc [k v]] (assoc acc k v))
                              (zipmap oppnames-set (repeat 0)) ;; Start at 0
                              matchfreqs)
          sorted-totals (sort-by second near-totals)
          ]
       (ffirst sorted-totals))))

(defn- vectorise-names [rankings]
  (vec (map :team rankings)))

(defn attach-suggested-opponents
  [rankings]
  (let [vec-ranks (vectorise-names rankings)]
    (for [rank rankings]
      (assoc-in rank [:suggest] (suggest-opponent rank vec-ranks)))))

(defn attach-uniques [rankings]
  (for [rank rankings]
    (->> rank
         :matches
         (filter (fn [x] (> (:for x) (:against x))))
         (map :opposition)
         (into #{})
         count
         (assoc-in rank [:u-wins]))))

(defn unique-players [results]
  (into (into #{} (map :home results))
        (map :away results)))

(defn pdbug [x]
  (println x)
  #_(doseq [t x]
      (println (:team t)))
  (println (filter #(= (:team %) "jons") x))
  x)

(s/defschema Nat
  (s/both s/Int
          (s/pred #(not (neg? %)) "Zero or more")))

(s/defschema Result
  "Result is a map of winner/loser names and scores"
  (s/both {:winner s/Str
           :loser s/Str
           :winner-score Nat
           :loser-score Nat}
          (s/pred (fn [{:keys [winner-score loser-score]}]
                    (> winner-score loser-score))
                  "Winner scores more than loser")))

(s/defschema Match
  {:opposition s/Str
   :for Nat
   :against Nat
   :round (s/maybe s/Int)
   :date java.util.Date})

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
   :players #{s/Str}
   :rankings [Ranking]})

(defn handle-rankings
  [results]
  {:message "Some rankings"
   :players (unique-players results)
   :rankings  (->> (calc-ranking-data results)
                   (attach-player-matches results)
                   attach-suggested-opponents
                   attach-uniques
                   (filter (fn [{matches :matches}]
                             (recent? (:date (last matches)))))
                   (filter (fn [{:keys [loses wins]}] (> (+ loses wins) 4)))
                   ((fn [col] (if (> (count col) 5)
                               (drop-last 2 col)
                               col)))
                   (map-indexed (fn [i m] (assoc m :rank (inc i)))))})

(defn make-routes [is-dev?]
  (with-routes
    (route/resources "/")
    (route/resources "/react" {:root "react"})
    (swagger-ui :swagger-docs "/api/docs")
    (swagger-docs "/api/docs")
    (GET "/app" [] (apply str (page true)))
    (GET "/init" [] (init) "inited")
    (swaggered
     "matches"
     :description "Matches"
     (context
      "/matches" []
      (GET* "/" []
            :return {:message s/Str
                     :matches [{s/Keyword s/Any}]}
            :summary "all the matches"
            (ok
             {:message "Here's the results!"
              :matches (take-last 20 @results)}))
      (POST* "/" req
             :body [result Result]
             (ok (save-match! result)))))
    (swaggered
     "rankings"
     :description "Rankings"
     (context
      "/rankings" []
      (GET* "/" []
            :return RankingsResponse
            (ok
             (handle-rankings (map translate-keys @results))))))
    (route/not-found "Page not found")))

(defn wrap-schema-errors [handler]
  (fn [req]
    (try+
     (handler req)
     (catch [:type :ring.swagger.schema/validation] {:keys [error] :as all}
       (println all)
       (http-resp/bad-request {:error error})))))

(defn make-handler [is-dev?]
  (-> (make-routes is-dev?)
      compojure.api.middleware/api-middleware
      (wrap-restful-format :formats  [:json :transit-json])
      ))

(defrecord WebServer [ring is-dev?]
  component/Lifecycle
  (start [component]
    (init)
    (let [app (cond-> (make-handler is-dev?)
                      is-dev? (prone/wrap-exceptions
                               {:app-namespaces ["react-tutorial-om"]}))]
      (assoc component
        :server
        (ring.adapter.jetty/run-jetty app ring))))
  (stop [component]
    (when-let [server (:server component)]
      (.stop server))
    (assoc component :server nil)))

(defn new-webserver [config]
  (map->WebServer config))


(comment
  "
 TODO:
* use a db => datomic?
* sort by col
* click on player -> match history, nemesis,
* proper glicko
* generic sortable table component
* unique wins, etc - some kind of distribution concept, who is most rounded
* can only play against people near you (+/- 3). how to handle top people?
* date in tooltip
* notification of results (chrome notification?), only if not entered here
 ")
