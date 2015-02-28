(ns react-tutorial-om.core-test
  (:require [react-tutorial-om.core :refer :all]
            [react-tutorial-om.schemas :as sch]
            [react-tutorial-om.ranking :as ranking]
            [clojure.test :refer :all]
            [ring.mock.request :refer :all]
            [schema.core :as s]
            [clojure.data.json :as json]
            [clj-time.coerce :refer [from-date]]
            [cognitect.transit :as transit]))

(deftest suggesting
  (testing "Normalise indexes"
    (is (= '(3 4 0 1) (normalise-indexes 5 2 [3 4 0 1])))
    (is (= '(4 3 1 2) (normalise-indexes 10 2 [-2 -1 1 2])))
    (is (= '(7 8 5 6) (normalise-indexes 10 2 [7 8 10 11])))))

(deftest date-funcs
  (testing "recent"
    (let [now (from-date #inst "2014-03-01")]
      (are [expected input] (= expected (recent? input now))
           true "2014-02-20T17:27:07Z"
           false "2014-01-20T17:27:07Z"
           true "2014-02-20"
           true #inst "2014-02-20"
           false #inst "2014-01-20T17:27:07Z"
           false nil))))

(deftest ranks
  (testing "calc-ranking-data"
    (let [matches [{:home "winners", :home_score 10, :away "losers", :away_score 0, :date nil}
                   {:home "arsenal", :home_score 3, :away "chelsea", :away_score 0, :date nil}
                   {:home "arsenal", :home_score 2, :away "chelsea", :away_score 0, :date nil}]]
      (is (= [{:loses 0, :draw 0, :wins 2, :rank 1, :team "arsenal", :ranking 1230.53, :rd nil, :round nil}
              {:loses 0, :draw 0, :wins 1, :rank 2, :team "winners", :ranking 1216.0, :rd nil, :round nil}
              {:loses 1, :draw 0, :wins 0, :rank 3, :team "losers", :ranking 1184.0, :rd nil, :round nil}
              {:loses 2, :draw 0, :wins 0, :rank 4, :team "chelsea", :ranking 1169.47, :rd nil, :round nil}]
             (calc-ranking-data matches))))))

(defn transit-header [req]
  (header req "Accept" "application/transit+json"))

(defn slurp-transit-body [response]
  (-> response :body (transit/reader :json) transit/read))

(deftest test-app
  (let [app (make-handler false (atom {}) "")
        response (-> (request :get "/rankings")
                   transit-header
                   app)
        body (slurp-transit-body response)]
    (is (= (:status response) 200))
    (is (nil? (s/check sch/RankingsResponse body)))))

(deftest league-ranks
  (is (= [{:team "chris",
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
           {:date #inst "2015" :winner "rob" :loser "chris" :winner-score 3 :loser-score 2}]))))
