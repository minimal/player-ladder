(ns react-tutorial-om.exp-test
  (:require [clj-http.client :as client]
            [clj-time.coerce :refer [from-date from-string to-timestamp]]
            [clj-time.core :as time]
            [clojure.data.json :as json]
            [clojure.template :refer [do-template]]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [expectations :refer :all]
            [react-tutorial-om.core :refer :all]
            [react-tutorial-om.ranking :as ranking]
            [react-tutorial-om.schemas :as sch]
            [ring.mock.request :refer :all]
            [schema.core :as s]
            [react-tutorial-om.events :refer [->EventHandler]]))

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
                 :name "a"}}})

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
        (get-api (atom fresh-state) "/rankings"))


;; players
(expect #{"a" "b"} (-> fresh-state
                       (update :singles-ladder conj match-result)
                       atom
                       (get-api "/rankings")
                       slurp-transit-body
                       :players))

(expect nil? (-> fresh-state
                 (update :singles-ladder conj match-result)
                 atom
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

        (let [app-state (atom fresh-state)]
          (freeze-time (time/date-time 1)
                       (post-api app-state "/leagues/a/result"
                                 {:winner "foo"
                                  :loser "moo"
                                  :winner-score 3
                                  :loser-score 0
                                  :id 1
                                  :round 1}))
          (get-in @app-state [:leagues :a :matches 0])))

;; posting to league adds to ladder with competition
(expect {:winner "foo"
         :loser "moo"
         :winner-score 3
         :loser-score 0
         :competition :a
         :date (to-timestamp (time/date-time 1))}

        (let [app-state (atom fresh-state)]
          (freeze-time (time/date-time 1)
                       (post-api app-state "/leagues/a/result"
                                 {:winner "foo"
                                  :loser "moo"
                                  :winner-score 3
                                  :loser-score 0
                                  :id 1
                                  :round 1}))
          (get-in @app-state [:singles-ladder 0])))

;; posting to a known league gives a higher ranking for winning
(expect (more-> "foo" :team
                1264.0 :ranking)

        (let [app-state (-> fresh-state
                            (assoc :leagues {:first-division {:matches []
                                                              :schedule []
                                                              :name "first-division"}})
                            atom)]
          (post-api app-state "/leagues/first-division/result"
                    {:winner "foo"
                     :loser "moo"
                     :winner-score 3
                     :loser-score 0
                     :id 1
                     :round 1})
          (-> @app-state
              :singles-ladder
              (handle-rankings {:filtered? true})
              :rankings
              first)))

;; posts to slack on league result
(expect ['("localhost" {:form-params {:text "foo wins against moo in league a: 3 - 0"}
                        :content-type :json})]
        (let [app-state (atom fresh-state)
              event-handler (component/start (->EventHandler "localhost"))]
          (side-effects [client/post]
                        (post-api app-state "/leagues/a/result"
                                  {:winner "foo"
                                   :loser "moo"
                                   :winner-score 3
                                   :loser-score 0
                                   :id 1
                                   :round 1}
                                  (:pub-ch event-handler))
                        (Thread/sleep 50)
                        (component/stop event-handler))))
