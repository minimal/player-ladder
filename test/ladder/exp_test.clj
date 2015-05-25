(ns ladder.exp-test
  (:require [clj-http.client :as client]
            [clj-time
             [coerce :refer [from-date to-timestamp]]
             [core :as time]]
            [clojure.template :refer [do-template]]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [expectations :refer :all]
            [ladder
             [core :refer :all]
             [database :as database]
             [events :refer [->EventHandler]]
             [ranking :as ranking]
             [schemas :as sch]]
            [ring.mock.request :refer :all]
            [schema.core :as s]))

;; recent
(do-template [expected input]
  (expect expected
          (recent? input (from-date #inst "2014-03-01") 4))
  true "2014-02-20T17:27:07Z"
  false "2014-01-20T17:27:07Z"
  true "2014-02-20"
  true #inst "2014-02-20"
  false #inst "2014-01-20T17:27:07Z"
  false nil)

;; suggesting
(expect '(3 4 0 1) (normalise-indexes 5 2 [3 4 0 1]))
(expect '(4 3 1 2) (normalise-indexes 10 2 [-2 -1 1 2]))
(expect '(7 8 5 6) (normalise-indexes 10 2 [7 8 10 11]))

(expect "" (suggest-opponent {:rank 1 :matches []} ["a" "b"]))
(expect "e"
        (suggest-opponent {:rank    1
                           :matches [{:opposition "b"} {:opposition "c"} {:opposition "d"} {:opposition "b"}]}
                         ["a" "b" "c" "d" "e" "f"]))

(expect [{:loses 0, :draw 0, :wins 2, :rank 1, :team "arsenal", :ranking 1230.53, :rd nil, :round nil}
         {:loses 0, :draw 0, :wins 1, :rank 2, :team "winners", :ranking 1216.0, :rd nil, :round nil}
         {:loses 1, :draw 0, :wins 0, :rank 3, :team "losers", :ranking 1184.0, :rd nil, :round nil}
         {:loses 2, :draw 0, :wins 0, :rank 4, :team "chelsea", :ranking 1169.47, :rd nil, :round nil}]

        (calc-ranking-data [{:home "winners", :home-score 10, :away "losers", :away-score 0, :date nil}
                            {:home "arsenal", :home-score 3, :away "chelsea", :away-score 0, :date nil}
                            {:home "arsenal", :home-score 2, :away "chelsea", :away-score 0, :date nil}]))


;; league ranks
(expect [{:team "chris",
          :loses 1,
          :wins 1,
          :draw 0,
          :points 1,
          :for 5,
          :against 4,
          :change nil
          :rank 1
          :diff 1,
          :matches
          [{:date #inst "2015-01-01T00:00:00.000-00:00", :for 3, :against 1, :opposition "rob", :round nil}
           {:date #inst "2015-01-01T00:00:00.000-00:00", :for 2, :against 3, :opposition "rob", :round nil}]}
         {:team "rob",
          :loses 1,
          :wins 1,
          :draw 0,
          :points 1,
          :for 4,
          :against 5,
          :change nil
          :rank 2
          :diff -1,
          :matches
          [{:date #inst "2015-01-01T00:00:00.000-00:00", :for 1, :against 3, :opposition "chris", :round nil}
           {:date #inst "2015-01-01T00:00:00.000-00:00", :for 3, :against 2, :opposition "chris", :round nil}]}]
        (ranking/matches->league-ranks
         [{:date #inst "2015" :winner "chris" :loser "rob" :winner-score 3 :loser-score 1}
          {:date #inst "2015" :winner "rob" :loser "chris" :winner-score 3 :loser-score 2}]))


(def fresh-state
  {:singles-ladder []
   :leagues {:a {:matches []
                 :schedule []
                 :players []
                 :name "a"}}})

(defn new-db [data]
  (component/start (database/map->new-database {:db (atom data)})))

(def match-result {:date (java.util.Date.)
                   :winner "a",
                   :loser "b",
                   :winner-score 2,
                   :loser-score 0})

(defn transit-header [req]
  (header req "Accept" "application/transit+json"))

(defn transit-post-header [req]
  (header req "Content-Type" "application/transit+json"))

(defn slurp-transit-body [response]
  (-> response :body (transit/reader :json) transit/read))

(defn get-api [app-state path]
  (let [app (make-handler false app-state nil)]
    (-> (request :get path)
        transit-header
        app)))

(defn post-api [app-state path body & [slack-ch]]
  (let [app (make-handler false app-state slack-ch)
        bytes (java.io.ByteArrayOutputStream.)
        writer (transit/writer bytes :json)]
    (transit/write writer body)
    (-> (request :post path (.toString bytes))
        transit-post-header
        app)))

(expect (more-of resp
                 200 (:status resp)
                 nil? (s/check sch/RankingsResponse (slurp-transit-body resp)))
        (get-api (new-db fresh-state) "/rankings"))


;; players
(expect #{"a" "b"} (-> fresh-state
                       (update :singles-ladder conj match-result)
                       new-db
                       (get-api "/rankings")
                       slurp-transit-body
                       :players))

(expect nil? (-> fresh-state
                 (update :singles-ladder conj match-result)
                 new-db
                 (get-api "/rankings")
                 slurp-transit-body
                 (->>
                  (s/check sch/RankingsResponse))))

;; post league
(expect {:winner "foo"
         :loser "moo"
         :winner-score 3
         :loser-score 0
         :id 1
         :date (to-timestamp (time/date-time 1))
         :round 1}

        (let [db (new-db fresh-state)]
          (freeze-time (time/date-time 1)
                       (post-api db "/leagues/a/result"
                                 {:winner "foo"
                                  :loser "moo"
                                  :winner-score 3
                                  :loser-score 0
                                  :id 1
                                  :round 1}))
          (get-in @(:db db) [:leagues :a :matches 0])))

;; posting to league adds to ladder with competition
(expect {:winner "foo"
         :loser "moo"
         :winner-score 3
         :loser-score 0
         :competition :a
         :date (to-timestamp (time/date-time 1))}

        (let [db (new-db fresh-state)]
          (freeze-time (time/date-time 1)
                       (post-api db "/leagues/a/result"
                                 {:winner "foo"
                                  :loser "moo"
                                  :winner-score 3
                                  :loser-score 0
                                  :id 1
                                  :round 1}))
          (get-in @(:db db) [:singles-ladder 0])))

;; posting to a known league gives a higher ranking for winning
(expect (more-> "foo" :team
                1264.0 :ranking)

        (let [db (-> fresh-state
                     (assoc :leagues {:first-division {:matches []
                                                       :schedule []
                                                       :players []
                                                       :name "first-division"}})
                     new-db)]
          (post-api db "/leagues/first-division/result"
                    {:winner "foo"
                     :loser "moo"
                     :winner-score 3
                     :loser-score 0
                     :id 1
                     :round 1})
          (-> @(:db db)
              :singles-ladder
              (handle-rankings {:filtered? true})
              :rankings
              first)))

;; posts to slack on league result
(expect (more-of [[host {:keys [form-params] :as params}] :as all]
                 "localhost" host
                 map? params
                 string? (:text form-params)
                 #"foo" (:text form-params)
                 #"moo" (:text form-params))
        (let [db (new-db fresh-state)
              event-handler (component/start (->EventHandler "localhost"))]
          (side-effects [client/post]
                        (post-api db "/leagues/a/result"
                                  {:winner "foo"
                                   :loser "moo"
                                   :winner-score 3
                                   :loser-score 0
                                   :id 1
                                   :round 1}
                                  (:pub-ch event-handler))
                        (Thread/sleep 100)
                        (component/stop event-handler))))
