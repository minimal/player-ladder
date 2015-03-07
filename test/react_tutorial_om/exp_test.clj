(ns react-tutorial-om.exp-test
  (:require [clj-time.coerce :refer [from-date from-string to-timestamp]]
            [clj-time.core :as time]
            [clojure.data.json :as json]
            [cognitect.transit :as transit]
            [expectations :refer :all]
            [react-tutorial-om.core :refer :all]
            [react-tutorial-om.ranking :as ranking]
            [react-tutorial-om.schemas :as sch]
            [ring.mock.request :refer :all]
            [schema.core :as s]))

(expect '(3 4 0 1) (normalise-indexes 5 2 [3 4 0 1]))
(expect '(4 3 1 2) (normalise-indexes 10 2 [-2 -1 1 2]))
(expect '(7 8 5 6) (normalise-indexes 10 2 [7 8 10 11]))

(expect [{:team "chris",
          :loses 1,
          :wins 1,
          :draw 0,
          :points 1,
          :for 5,
          :against 4,
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
          :diff -1,
          :matches
          [{:date #inst "2015-01-01T00:00:00.000-00:00", :for 1, :against 3, :opposition "chris", :round nil}
           {:date #inst "2015-01-01T00:00:00.000-00:00", :for 3, :against 2, :opposition "chris", :round nil}]}]
        (ranking/matches->league-ranks
         [{:date #inst "2015" :winner "chris" :loser "rob" :winner-score 3 :loser-score 1}
          {:date #inst "2015" :winner "rob" :loser "chris" :winner-score 3 :loser-score 2}]))


(def fresh-state
  {:singles-ladder []
   :leagues {}})

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

(defn post-api [app-state path body]
  (let [app (make-handler false app-state nil)
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
                       (update-in [:singles-ladder] conj match-result)
                       atom
                       (get-api "/rankings")
                       slurp-transit-body
                       :players))

(expect nil? (-> fresh-state
                 (update-in [:singles-ladder] conj match-result)
                 atom
                 (get-api "/rankings")
                 slurp-transit-body
                 (->>
                  (s/check sch/RankingsResponse))))

;; post league
(expect-let [app-state (-> fresh-state
                           (assoc :leagues {:a {:matches []
                                                :schedule []
                                                :name "a"}})
                           atom)]
            {:winner "foo"
             :loser "moo"
             :winner-score 3
             :loser-score 0
             :id 1
             :date (to-timestamp (time/date-time 1))
             :round 1}
            (do
              (freeze-time (time/date-time 1)
                           (post-api app-state "/leagues/a/result"
                                     {:winner "foo"
                                      :loser "moo"
                                      :winner-score 3
                                      :loser-score 0
                                      :id 1
                                      :round 1}))
              (get-in @app-state [:leagues :a :matches 0])))
