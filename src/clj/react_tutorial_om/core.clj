(ns react-tutorial-om.core
  (:require [clj-time.coerce :refer [from-date from-string to-timestamp]]
            [clj-time.core :as time]
            [clojure.core.async :refer [<! >! chan go]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [compojure.api.routes :refer [api-root]]
            [compojure.api.sweet
             :refer
             [GET* POST* context* swagger-docs swagger-ui]]
            [compojure.core :refer [GET]]
            [compojure.route :as route]
            [com.stuartsierra.component :as component]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [prone.debug :refer [debug]]
            [prone.middleware :as prone]
            [react-tutorial-om.ranking :as ranking]
            [react-tutorial-om.schemas :as sch]
            ring.adapter.jetty
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :refer [redirect]]
            [ring.util.http-response :as http-resp :refer [ok resource-response]]
            [schema.core :as s]
            [slingshot.slingshot :refer [throw+ try+]]))

(def archived-teams #{"jons" "cliff" "sina" "jamie" "michael" "michal"})

(defn recent? [date & [now weeks]]
  (boolean (some-> date
                   (#(or (from-string %) (from-date %)))
                   (time/after? (time/minus (or now (time/now))
                                            (time/weeks (or weeks 20)))))))

(defn load-edn-file [file]
  (-> (slurp file)
      (edn/read-string)
      ((partial s/validate sch/AllResults))))

(defn spit-edn-file [file data]
  (spit file (with-out-str (pprint data))))

(defn init!
  [db db-file]
  (reset! db (load-edn-file db-file)))


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

(s/defn ^:always-validate  save-match! ;; TODO: write to a db
  [match :- sch/Result, db]
  (swap! db (partial update-ladder-results match))
  {:message "Saved Match!"})

(defn translate-keys [{:keys [winner winner-score loser loser-score date competition]}]
  {:home winner
   :home-score winner-score
   :away loser
   :away-score loser-score
   :competition competition
   :date date})

(defn calc-ranking-data [matches]
  (map-indexed
   (partial ranking/format-for-printing matches)
   (ranking/top-teams 30 matches)))

(defn attach-player-matches [results rankings]
  (for [rank rankings]
    (assoc rank :matches (ranking/show-matches (:team rank) results))))

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
    (if (< 4 (count ranks))
      (let [offset 2
            idx-rank (dec rank)
            opps1 (range rank (+ rank offset))
            opps2 (range (- idx-rank offset) idx-rank)
            allopps (->> (concat opps1 opps2)
                         (normalise-indexes (count ranks) offset))
            oppnames-set (set (map ranks allopps))
            matchfreqs (frequencies (sequence (comp (map :opposition)
                                                    (filter #(contains? oppnames-set %)))
                                              matches))
            near-totals (reduce (fn [acc [k v]] (assoc acc k v))
                                (zipmap oppnames-set (repeat 0)) ;; Start at 0
                                matchfreqs)
            sorted-totals (sort-by second near-totals)]
        (ffirst (remove #(archived-teams (first %)) sorted-totals)))
      "")
    (catch IndexOutOfBoundsException e
      (println "Error in suggest opponent" (class e))
      "")))

(defn attach-suggested-opponents
  [rankings]
  (let [vec-ranks (mapv :team rankings)]
    (for [rank rankings]
      (assoc rank :suggest (suggest-opponent rank vec-ranks)))))

(defn attach-uniques [rankings]
  (for [rank rankings]
    (->> (into #{}
               (comp
                (filter (fn [x] (> (:for x) (:against x))))
                (map :opposition))
               (:matches rank))
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

(defn handle-rankings
  [results & [{:keys [filtered?] :or {filtered? true}}]]
  (let [results (map translate-keys results)]
    {:message "Some rankings"
     :players (unique-players results)
     :rankings (let [res (->> results
                              calc-ranking-data
                              (attach-player-matches results)
                              attach-suggested-opponents
                              attach-uniques)]
                 (cond->> res
                   filtered? (filter (fn [{matches :matches}]
                                       (recent? (:date (last matches)))))
                   filtered? ((fn [col] (if (> (count col) 5)
                                         (drop-last 2 col)
                                         col)))
                   true (map-indexed (fn [i m] (assoc m :rank (inc i))))))}))

(s/defn ^:always-validate update-league-result
  "Update the state map with the result of the league match. Remove
  match from schedule and also update the ladder with the result"
  [result :- sch/LeagueResult league doc]
  (-> doc
      (update-in [:leagues league :schedule]
                 (fn [sch] (remove #(= (:id %) (:id result)) sch)))
      (update-in [:leagues league :matches]
                 conj (assoc result :date (to-timestamp (time/now))))
      ((partial update-ladder-results (-> result
                                          (dissoc :id :round)
                                          (assoc :competition league))))))

(defn handle-league-result
  "Given an db atom, a league name and a result update the schedule and
  matches for the league and write out to file. Return the resulting
  state. Also post to slack if possible."
  [db league {:keys [winner loser winner-score loser-score] :as result} event-ch]
  (let [db-snapshot (swap! db (partial update-league-result result league))]
    (if event-ch
      (go (>! event-ch [:league-match (assoc result
                                             :league league
                                             :db-snapshot db-snapshot)]))))
  {:message "ok"})

(defn- handle-get-leagues
  [db]
  (into {} (for [[l {:keys [matches schedule name players img
                            sets-per-match bands]}] (:leagues @db)]
             [l {:rankings (ranking/matches->league-ranks matches)
                 :schedule (sort-by :round schedule)
                 :img img
                 :sets-per-match (or sets-per-match 3)
                 :bands bands
                 :players players
                 :name name}])))

(defn handle-post-league-schedule
  [db league {:keys [home away round] :as fixture}]
  (let [id (hash (str round home away (rand-int 1000)))
        fixture' (-> fixture
                     (assoc :id id)
                     (dissoc :name))]
    (s/validate sch/LeagueScheduleMatch fixture')
    (swap! db (fn [a] (update-in a [:leagues (keyword league) :schedule]
                                 #(conj % fixture'))))
    (ok id))
  )

(defn make-routes [is-dev? db event-ch]
  (api-root
   (GET "/" [] (resource-response "index.html" {:root "public"}))
   (route/resources "/")
   (route/resources "/react" {:root "react"})
   (swagger-ui :swagger-docs "/api/docs")
   (swagger-docs "/api/docs")
   (GET "/app" [] (resource-response "index.html" {:root "public"}))
   ;;(GET "/init" [] (init! db) "inited")
   (context*
    "/matches" []
    :tags ["matches"]
    (GET* "/" []
          :return {:message s/Str
                   :matches [{s/Keyword s/Any}]}
          :summary "all the matches"
          (ok
           {:message "Here's the results!"
            :matches (take-last 20 (:singles-ladder @db))}))
    (POST* "/" req
           :body [result sch/Result]
           (ok (save-match! result db))))
   (context*
    "/rankings" []
    :tags ["rankings"]
    (GET* "/" []
          :return sch/RankingsResponse
          (ok
           (handle-rankings (:singles-ladder @db)))))
   (context*
    "/leagues" []
    :tags ["leagues"]
    (GET* "/" []
          :return sch/LeaguesResponse
          (ok
           {:leagues (handle-get-leagues db)}))
    (POST* "/:league/result" [league] ;TODO: take id in post url?
           :body [result sch/LeagueResult]
           (ok (handle-league-result db (keyword league) result event-ch)))
    (GET* "/editable" req
          (if-not (authenticated? req)
            (do (prn "no auth")
                (throw-unauthorized))
            (ok)))
    (POST* "/schedule/:league" [league]
           :body [fixture s/Any]
           (ok (handle-post-league-schedule db league fixture))))
   (route/not-found "Page not found")))

(defn wrap-schema-errors [handler]
  (fn [req]
    (try+
     (handler req)
     (catch [:type :ring.swagger.schema/validation] {:keys [error] :as all}
       (println all)
       (http-resp/bad-request {:error error})))))

(defn log-request-middleware [handler]
  (fn [request]
                                        ;(puget.printer/pprint request)
    (let [res (handler request)]
      #_(println res)
      res)))

(defn unauthorized-handler [req metadata]
  (prn "in unauth" req)
  (if-let [user (get-in req [:query-params "user"])]
    (let [next-url (get-in req [:query-params :next] "/app")
          updated-session (assoc (:session req) :identity (keyword user))]
      (println updated-session)
      (let [resp
            (-> (redirect next-url)
                (assoc :session updated-session))]
        (prn resp)
        resp))
    {:status 401 :body {:error "Not authorized"}}))

(def auth-backend
  (session-backend {:unauthorized-handler unauthorized-handler}))


(defn make-handler [is-dev? db event-ch]
  (-> (make-routes is-dev? db event-ch)
      ;; log-request-middleware
      (wrap-authorization auth-backend)
      (wrap-authentication auth-backend)
      wrap-params
      wrap-session
      (cond-> is-dev? (prone/wrap-exceptions
                       {:app-namespaces ["react-tutorial-om"]
                        :skip-prone? (fn [{:keys [headers]}]
                                       (contains? headers "postman-token"))}))
      ;; compojure.api.middleware/api-middleware
      (wrap-restful-format {:formats [:json :transit-json]})

      ;; wrap-schema-errors
      ;; ring.swagger.middleware/catch-validation-errors
      ;; ring.middleware.http-response/catch-response
      ))

(defrecord WebServer [ring is-dev? event-handler db-file]
  component/Lifecycle
  (start [component]
    (let [db (atom {})
          file-agent (agent nil :error-handler println)
          _ (init! db db-file)
          app (make-handler is-dev? db (:pub-ch event-handler))]
      (add-watch db :writer (fn [_ _ _ new]
                              (send-off file-agent (fn [_] (spit-edn-file db-file new)))))
      #_(when is-dev?
          (inspect/start))
      (assoc component
             :server
             (ring.adapter.jetty/run-jetty app ring)
             :file-agent file-agent
             :db db)))
  (stop [component]
    #_(when is-dev?
        (inspect/stop))
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
