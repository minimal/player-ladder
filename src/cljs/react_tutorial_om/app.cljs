(ns ^:figwheel-load
  react-tutorial-om.app
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]])
  (:require [goog.events :as events]
            [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [cljs.core.match :refer-macros [match]]
            [om.core :as om]
            [om.dom :as dom]
            [om-tools.dom :as tdom]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.schema :as schema]
            [schema.core :as s :refer-macros [defschema]]
            [secretary.core :as sec]
            [sablono.core :as html :refer-macros [html]]
            [goog.history.EventType :as EventType]
            [cljs-http.client :as http]
            ;; [clairvoyant.core :as trace :include-macros true]
            [clojure.string :as str]
            ;; [omdev.core :as omdev]
            [react-tutorial-om.schemas :as sch :refer [check Nat]]
            [react-tutorial-om.utils :refer [guid] :refer-macros [logm inspect breakpoint]])
  (:import [goog History]))

(enable-console-print!)
(sec/set-config! :prefix "#")

(let [history (History.)
      navigation EventType/NAVIGATE]
  (goog.events/listen history
                      navigation
                      #(-> % .-token sec/dispatch!))
  (doto history (.setEnabled true))
  history)


(defonce conn-ch (chan (async/dropping-buffer 1))) ;;TODO: no global
(defonce nav-ch (chan))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util

(defn- with-id
  [m]
  (assoc m :id (guid)))

(defn- fetch-matches
  "The comments need to be a vector, not a list. Not sure why."
  [app opts]
  (go (let [{{matches :matches} :body} (<! (http/get (:url opts) {:accept "application/transit+json"}))]
        (if matches
          (om/transact!
           app #(assoc % :matches matches))
          (>! conn-ch :error)))))

(defn- fetch-rankings
  "The comments need to be a vector, not a list. Not sure why."
  [app opts]
  (go (let [{{:keys [rankings players]}
             :body status :status} (<! (http/get (:url opts) {:accept "application/transit+json"}))]
        (if rankings
          (om/transact!
           app #(-> %
                    (assoc :rankings rankings)
                    (assoc :players players)
                    ))
          (>! conn-ch :error)))))

(defn fetch-leagues
  "The comments need to be a vector, not a list. Not sure why."
  [app opts]
  ;; (logm "gonna fetch leagues")
  (go (let [{:keys [success status body] :as resp} (<! (http/get
                                                        "/leagues"
                                                        {:accept "application/transit+json"}))]
        (if success
          (om/update! app (:leagues body))
          (>! conn-ch :error)))))


(defn- value-from-node
  [owner field]
  (let [n (om/get-node owner field)
        v (-> n .-value clojure.string/trim)]
    (when-not (empty? v)
      [v n])))

(defn- clear-nodes!
  [& nodes]
  (doall (map #(set! (.-value %) "") nodes)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components


(defonce app-state
  (atom {:matches [] :rankings [] :players [] :conn? true
         :player-view {:display false :player nil}
         :leagues {}}))

(defn league-items []
  (om/ref-cursor (:leagues (om/root-cursor app-state))))


(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defcomponent ladder-match-row
  [{:keys [winner winner-score loser loser-score date]} owner opts]
  (render
   [_]
   (tdom/tr {:class "comment"}
            (map tdom/td [winner winner-score loser-score loser]))))

(defcomponent ladder-result-list [{:keys [matches]}]
  (render
   [_]
   (html [:table.commentList
          [:thead [:tr (map #(tdom/th %) ["winner" "" "" "loser"])]]
          [:tbody (om/build-all ladder-match-row (take 20 (reverse matches)))]])))

(s/defn ^:always-validate save-match!
  "Save Ladder match"
  [match :- sch/Result app opts]
  (do (om/transact! app [:matches]
                    (fn [matches] (conj matches match)))
      (go (let [res (<! (http/post (:url opts) {:transit-params match}))]
            (prn (:message res))))))

(defn validate-scores
  "Coerce scores to ints and return if onse is greater than the other
  else return [false false] "
  [score score2]
  (let [score-int (js/parseInt score)
        score2-int (js/parseInt score2)]
    (if (or (> score-int score2-int)
            #_(< score-int score2-int))
      [score-int score2-int]
      [false false])))

(defn save-league-match!
  "Post league result"
  [match app opts]
  {:pre [(s/validate sch/LeagueResult match)]}
  (go (let [res (<! (http/post (:url opts) {:transit-params match}))]
        (when (:success res)
          (inspect "saved league:" res)
          (fetch-leagues app opts))
        res)))

(defn handle-league-result-submit
  [e app owner opts {:keys [home-score away-score home away name id round
                            sets-per-match]
                     :as result}]
  (let [[home-score away-score] (map js/parseInt [home-score away-score])
        [winner winner-score] (if (> home-score away-score)
                                [home home-score]
                                [away away-score])
        [loser loser-score] (if (= winner home)
                              [away away-score]
                              [home home-score])]
    (if (and (> winner-score loser-score)
             (= sets-per-match winner-score))
      (do (save-league-match! {:winner winner :winner-score winner-score
                               :loser loser :loser-score loser-score
                               :id id :round round}
                              app {:url (str "/leagues/" name "/result")})
          (om/transact! app [(keyword name) :schedule]
                        (fn [s] (remove #(= (:id %) id) s))))
      (throw (js/Error. "Invalid score")))
    (inspect "onsubmit" result "winner" winner)))

(s/defn ^:always-validate handle-league-schedule-submit
  "Adds a new game to the schedule"
  [app owner name {:keys [home away round] :as data} :- sch/LeagueScheduleMatch]
  {:pre [(not= home away)]}
  (inspect data)
  (go (let [res (<! (http/post (str "/leagues" "/schedule/" name)
                               {:transit-params (update data :round js/parseInt)}))]
        (when (:success res)
          (inspect "saved league schedule:" res)
          (om/update-state! owner #(assoc % :home "" :away ""))
          #_(fetch-leagues app {}))
        res))
  )


(defn handle-submit
  [e app owner opts {:keys [winner winner-score loser loser-score]}]
  (let [winner (clojure.string/trim winner)
        winner-score (clojure.string/trim winner-score)
        loser (clojure.string/trim loser)
        loser-score (clojure.string/trim loser-score)
        [winner-score-int loser-score-int] (validate-scores winner-score loser-score)]
    (when (and winner winner-score-int loser loser-score-int)
      (save-match! {:winner winner :winner-score winner-score-int
                    :loser loser :loser-score loser-score-int}
                   app opts)
      (doseq [key [:winner :winner-score :loser :loser-score]]
        (om/set-state! owner key "")))
    (.preventDefault e)))

(defn handle-change
  "Get the value of the event and set the state to the key.
  Applies f to the value if supplied"
  [e owner key & [f]]
  (let [v (.. e -target -value)]
    (om/set-state! owner key (if f (f v) v))))

(defcomponent ladder-form
  [app owner opts]
  (init-state
   [_]
   {:winner "" :winner-score ""
    :loser "" :loser-score ""})
  (render-state
   [this state]
   (tdom/form
    {:class "commentForm" :onSubmit #(handle-submit % app owner opts state)}
    (tdom/input {:type "text" :placeholder "Winner" :ref "winner"
                 :value (:winner state) :list "players"
                 :onChange #(handle-change % owner :winner)})
    (tdom/input {:type "number"  :placeholder "Score" :ref "winner-score"
                 :value (:winner-score state)
                 :onChange #(handle-change % owner :winner-score)})
    (tdom/input {:type "text" :placeholder "Loser" :ref "loser"
                 :value (:loser state) :list "players"
                 :onChange #(handle-change % owner :loser)})
    (tdom/input {:type "number" :placeholder "Score" :ref "loser-score"
                 :value (:loser-score state)
                 :onChange #(handle-change % owner :loser-score)})
    (tdom/input {:class "button" :type "submit" :value "Post"})
    (apply dom/datalist #js {:id "players"}
           (map #(dom/option #js {:value %})
                (:players app))))))

(defcomponent ladder-box [app owner opts]
  (init-state [_] {:mounted true})
  (will-mount [_]
              (go (while (om/get-state owner :mounted)
                    (fetch-matches app opts)
                    (<! (timeout (:poll-interval opts))))))
  (will-unmount [_]
                (om/set-state! owner :mounted false))
  (render
   [_]
   (tdom/div {:class "commentBox"}
     (tdom/h3 "Results (most recent first)")
     (om/build ladder-form app {:opts opts})
     (om/build ladder-result-list app))))

(defcomponent last-10-games [results owner]
  (render
   [_]
   (html [:ul.last-10-games {:style {:width "130px"}}
          (for [game (take-last 10 results)]
            (let [win? (> (:for game) (:against game))]
              [:li {:class (str (if win? "win" "loss") " hover")}
               [:span ""]
               [:.atooltip (str (if win? "Win " "Loss ")
                                (:for game) " - " (:against game)
                                " against " (:opposition game)
                                " @ " (:date game))]]))])))


(defcomponent player-summary
  [{{:keys [team ranking rd wins loses suggest matches] :as fields} :data
    show :display} owner opts]
  (render
   [_]
   (html [:div {:style (display show)}
          [:h4 "Player Stats"]
          [:ul.pricing-table
           [:li.title team]
           [:li.price ranking]
           [:li.bullet-item (str  wins " - " loses)]
           [:li.bullet-item
            [:ul (for [[name matches*] (reverse (sort-by (fn [[_ games]] (count games))
                                                         (group-by :opposition matches)))]
                   (let [wins (reduce (fn [tot {:keys [for against]}]
                                        (if (> for against)
                                          (inc tot)
                                          tot)) 0 matches*)
                         losses (- (count matches*) wins)]
                     [:li
                      [:.row
                       [:.small-6.columns
                        (str name ": " wins "-" losses)
                        [:.progress.success
                         [:span.meter {:style {:width (str (Math/round
                                                            (* 100 (/ wins (+ wins losses)))) "%")}}]]]
                       [:.small-6.columns
                        (om/build last-10-games (take-last 5 matches*))]]]))]]]])))

(defcomponent ranking
  [{:keys [team rank ranking rd wins loses suggest] :as fields} owner opts]
  (render-state [this {:keys [select-player-ch]}]
                (tdom/tr
                 (map tdom/td
                      [rank
                       (tdom/span {:onClick (fn [e]
                                              (put! select-player-ch team)
                                              (.stopPropagation e))
                                   :style #js {:cursor "pointer"}}
                         team)
                       ranking (.toFixed (/ wins loses) 2) suggest
                       (om/build last-10-games (:matches fields))]))))

(defcomponent ranking-list [rankings owner opts]
  (render
   [_]
   (tdom/table {:class "rankingTable"}
               (tdom/thead
                (tdom/tr
                 (map tdom/th
                      ["" "team" "ranking" "w/l"
                       "suggested opponent" "last 10 games"])))
               (tdom/tbody
                (om/build-all
                 ranking
                 rankings
                 {:init-state {:select-player-ch (:select-player-ch opts)}})))))


(defcomponent rankings-box [app owner opts]
  (init-state [_]
              {:mounted true})
  (will-mount [_]
              (prn "will mount")
              (go (while (om/get-state owner :mounted)
                    (fetch-rankings app opts)
                    (<! (timeout (:poll-interval opts))))))
  (will-unmount [_]
                (logm "unmounting!!!!!")
                (om/set-state! owner :mounted false))
  (render [_]
          (tdom/div {:class "rankingsBox"}
            (tdom/h3 "Rankings")
            (om/build ranking-list (:rankings app) {:opts opts}))))

(defn position-class [bands rank]
  (if bands
    (cond
      (<= (:relegation bands) rank)
      "relegation"

      (>= (:playoff bands) rank (inc (:promotion bands)))
      "playoff"

      (>= (:promotion bands) rank)
      "promotion"

      :else nil)))

(defcomponent league-row [{:keys [team wins loses points matches for against diff
                                  rank change league-name bands] :as data} ;;:- (schema/cursor LeagueRanking)
                          owner opts]
  (render
   [_]
   (html [:tr {:class (position-class bands rank)}
          [:td rank]
          [:td {:style {:color (case change
                                 :+ "#2c7e00"
                                 :- "#a8160c"
                                 "")}}
           (case change
             :+ "▲"
             :- "▼"
             "")]
          [:td [:a {:href (str "#/leagues/" league-name "/team/" team)
                    :on-click #(some-> js/document
                                       (.getElementById "league-summary")
                                       .scrollIntoView )}
                team]]
          [:td (+ wins loses)]
          [:td wins]
          [:td loses]
          [:td for]
          [:td against]
          [:td diff]
          [:td {:style {:color "darkgreen"}} points]
          [:td (om/build last-10-games matches)]])))

(defcomponent league-schedule-row [{:keys [round home id away name] :as app} owner opts]
  (init-state [_] {:home-score 0 :away-score 0 :error nil})
  (render-state
   [_ state]
   (let [leagues (om/observe owner (league-items))
         league (get leagues (keyword name))]
     (html
      [:form
       {:class "league-form"
        :on-submit #(do (.preventDefault %)
                        (try (handle-league-result-submit
                              % leagues owner opts
                              (merge state
                                     {:round round :name name
                                      :sets-per-match (:sets-per-match league)
                                      :id id :home home :away away}))
                             (catch :default e
                               (inspect e)
                               (om/set-state! owner [:error] e)))
                        )}
       [:.row
        [:.large-10.colums
         [:.large-1.columns round]
         [:.large-4.columns
          [:.row.collapse.prefix-radius
           [:.small-8.columns
            [:span.prefix home]]
           [:.small-4.columns
            [:select {:id "moo" :value (:home-score state)
                      :on-change #(handle-change % owner :home-score)}
             (for [n (range (inc (:sets-per-match league)))]
               [:option {:value (str n)} n])]]]]
         [:.large-1.columns "vs"]
         [:.large-4.columns
          [:.row.collapse.prefix-radius
           [:.small-8.columns
            [:span.prefix away]]
           [:.small-4.columns
            [:select {:id "foo" :value (:away-score state)
                      :on-change #(handle-change % owner :away-score)}
             (for [n (range (inc (:sets-per-match league)))]
               [:option {:value (str n)} n])]]]]
         [:input {:class "button tiny" :type "submit" :value "Post"}]]]
       (when-let [err (:error state)]
         (let [msg (condp = (:type (.-data err))
                     :schema.core/error "Invalid input"
                     (.-message err))]
           (go (<! (timeout 5000))
               (om/set-state! owner :error nil))
           [:small.error (str "Error: " msg)]))
       ]))))

(defn check-leagues-editable
  "Calls editable endpoint, sets state

  If success set to editable, if unauth set not-auth true"
  [owner]
  (go (let [{:keys [success status]} (<! (http/get "/leagues/editable"))]
        (if success
          (om/set-state! owner :editable true)
          (when (= 401 status)
            (om/update-state! owner (fn [s]
                                      (assoc s
                                        :editable false
                                        :not-auth true))))))))

(defcomponent league-schedule-edit
  "Allows adding matches to schedule of authorised"
  [data owner opts]
  (init-state [_]
    {:editable false
     :not-auth false                                        ;; true if verified not authorised
     :round 1
     :home ""
     :away ""
     :error nil})

  (render-state [this state]
    (html [:div [:a {:on-click #(check-leagues-editable owner)}
                 "Edit"]
           (if (:editable state)
             [:form {:on-submit #(do (.preventDefault %)
                                     (try (handle-league-schedule-submit
                                            data owner (:name data)
                                            (select-keys state [:round :home :away]))
                                          (catch :default e
                                            (inspect "error")
                                            (om/set-state! owner [:error] e))))}
              [:.row
               [:.large-12.colums
                [:.small-2.columns
                 [:.row.collapse.prefix-radius
                  [:.small-5.columns
                   [:span.prefix "R"]]
                  [:.small-7.columns
                   [:select {:id "round-select" :value (:round state)
                             :on-change #(handle-change % owner :round js/parseInt)}
                    (for [n (range 1 (inc (count (:players data))))]
                      [:option {:value (str n)} n])]]]]
                [:.large-3.columns
                 [:.row.collapse.prefix-radius
                  [:.small-2.columns
                   [:span.prefix "H"]]
                  [:.small-10.columns
                   [:select {:id "add-home" :value (:home state)
                             :on-change #(handle-change % owner :home)}
                    (for [p (cons nil (:players data))]
                      [:option {:value p} p])]]]]
                [:.small-3.columns
                 [:.row.collapse.prefix-radius
                  [:.small-2.columns
                   [:span.prefix "A"]]
                  [:.small-10.columns
                   [:select {:id "add-away" :value (:away state)
                             :on-change #(handle-change % owner :away)}
                    (for [p (cons nil (:players data))]
                      [:option {:value p} p])]]
                  ]
                 ]
                [:.small-2.columns
                 [:input {:class "button tiny" :type "submit" :value "Add"}]]]]

              (when-let [err (:error state)]
                (let [msg (condp = (:type (.-data err))
                            :schema.core/error "Invalid input"
                            (.-message err))]
                  (go (<! (timeout 5000))
                      (om/set-state! owner :error nil))
                  [:small.error (str "error: " msg)]))]
             ;; else
             (when (:not-auth state)
               (go (<! (timeout 10000))
                   (om/set-state! owner :not-auth false))
               [:.alert-box.warning.radius "Not authorised to edit"]))])
    ))

(defcomponent league-schedule [{:keys [name schedule] :as data} owner opts]
  (render
   [_]
   (html
    [:div
     [:h4.subheader "Schedule"]
     (for [row schedule]
       (om/build league-schedule-row (assoc row :name name) {:react-key (guid)}))
     (om/build league-schedule-edit data)])))

(defcomponent league-list [league owner opts]
  (init-state
   [_]
   {:mounted false})
  (render
   [_]
   ;; (inspect league)
   (html
    [:div
     [:h3 (:name league)]
     (if-let [src (:img league)]
       [:img {:src src :style {:height "150px"}}])
     [:table.rankingTable
      [:thead
       (for [header ["" "" "" "P" "W" "L" "F" "A" "Diff" "Pts" "Last 10 Games"]]
         [:th header])]
      [:tbody
       (om/build-all league-row (mapv #(assoc %
                                              :league-name (:name league)
                                              :bands (:bands league))
                                      (:rankings league)
                                      )
                     {:key :team})]]
     (om/build league-schedule (select-keys league [:schedule :name :players]))])))

(defcomponent status-box [conn? owner]
  (render [_]
          (html [:.alert-box.warning.radius {:style (display (not conn?))}
                 "Connection problem!"])))

(defcomponent navigation-view [app _]
  (render
   [_]
   (let [style {:style {:margin "10px;"}}]
     (tdom/div style
       (om/build status-box (:conn? app))
       (tdom/a (assoc style :href "#/")
               "Ladder")
       (tdom/a (assoc style :href "#/leagues")
               "Leagues")
       (tdom/a (assoc style :href "#/about")
               "About")))))


(defcomponent ladder-app [app owner]
  (init-state [_] {:select-player-ch (chan)})
  (will-mount
   [_]
   (let [select-player-ch (om/get-state owner :select-player-ch)]
     (go (loop []
           (when-let [player (<! select-player-ch)]
             (om/transact!
              app :player-view
              #(-> %
                   ((fn [x]
                      (if (= (:player x)
                             player)  ;; toggle same player
                        (assoc x :display (not (:display x)))
                        (assoc x :display true))))
                   (assoc :player player)))
             (recur))))))
  (will-unmount
   [_]
   (async/close! (om/get-state owner :select-player-ch)))
  (render-state
   [this {:keys [select-player-ch]}]
   (tdom/div {:class "row"}
     (tdom/div {:class "large-2 columns"
                :dangerouslySetInnerHTML {:__html "&nbsp;"}})
     (tdom/div {:class "large-7 columns"}

       (om/build rankings-box app
                 {:opts {:poll-interval 2000
                         :url "/rankings"
                         :select-player-ch select-player-ch}})
       (om/build ladder-box app
                 {:opts {:poll-interval 2000
                         :url "/matches"}}))
     (tdom/div {:class "large-3 columns"}
       (om/build
        player-summary
        {:data  (first
                 (filter #(= (:team %)
                             (get-in app [:player-view :player]))
                         (:rankings app)))
         :display (get-in app [:player-view :display])})))))

(defn filter-schedule [team]
  (filter #(or (= (:home %) team)
               (= (:away %) team))))

(defcomponent league-team-summary [{:keys [rank against points matches
                                           for team change loses wins diff
                                           schedule] :as data}
                                   owner opts]
  (did-mount [_] (.scrollIntoView (om/get-node owner)))
  (render
   [_]
   (html [:div#league-summary
          [:h4 "Player Stats"]
          [:ul.pricing-table
           [:li.title team]
           [:li.price rank]
           [:li.bullet-item (str  wins " - " loses)]
           [:li.bullet-item (str "Last match: " (let [game (last matches)]
                                                  (str (:for game) " - " (:against game)
                                                       " against " (:opposition game)
                                                       " @ " (:date game))))]
           [:li.bullet-item (str "Average sets per match: "
                                 (.toFixed (/ (transduce (map :for) + matches)
                                              (count matches))
                                           2))]
           [:li.bullet-item
            [:div
             [:p "Next games: "]
             (for [game (sequence (comp (filter-schedule team) (take 2)) schedule)
                   :let [opp (some #(if (not= team %) %)
                                   ((juxt :home :away) game))]]
               [:p (str  opp ". Rd: " (:round game))])]]]]
         )))

(defcomponent leagues-page-view [{:keys [leagues path] :as data} owner opts]
  (render
   [_]
   (tdom/div {:class "row results-row"}
     (tdom/div {:class "large-2 columns"
                :dangerouslySetInnerHTML {:__html "&nbsp;"}})
     (tdom/div {:class "large-7 columns"}
       (tdom/h3 "Leagues")
       (tdom/ul (for [[league _] leagues]
                  (tdom/li {:key (name league)}
                           (tdom/a {:href (str "#/leagues/" (name league))}
                                   (name league)))))
       (if (seq path)
         (tdom/div {:style (display (seq path))}
           (om/build league-list (leagues (keyword (first path)))))))
     (tdom/div {:class "large-3 columns"}
       (match (om/value path)
         [league [:team team]]
         (if-let [team-row (->> (get-in leagues [(keyword league) :rankings])
                                (filter #(= team (:team %)))
                                first )]
           (om/build league-team-summary
                     (assoc team-row
                            :schedule (get-in leagues [(keyword league) :schedule]))))
         :else nil)
       )))

  (init-state
   [_]
   {:mounted true})
  (will-mount
   [_]
   (prn "will mount leagues")
   (inspect opts)
   (go (while (om/get-state owner :mounted)
         ;; (logm :polling)
         (fetch-leagues leagues opts)
         (<! (timeout (or  (:poll-interval opts) 5000))))))
  (will-unmount
   [_]
   (om/set-state! owner :mounted false)))

(defcomponent about-page-view [_ owner]
  (render
   [_]
   (tdom/div {:class "row results-row"}
     (tdom/div {:class "large-2 columns"
                :dangerouslySetInnerHTML #js {:__html "&nbsp;"}})
     (tdom/div {:class "large-7 columns"}
       (tdom/div (tdom/a {:href "https://github.com/minimal/player-ladder"}
                         "Fork me on Github")))
     (tdom/div {:class "large-3 columns" :dangerouslySetInnerHTML {:__html "&nbsp;"}}))))

(defcomponent top-level [{:keys [view path] :as app} owner]
  (render
   [this]
   (tdom/div
       (tdom/div {:class "row results-row"} ;; top bar
         (tdom/div {:class "large-2 columns"
                    :dangerouslySetInnerHTML {:__html "&nbsp;"}})
         (tdom/div {:class "large-7 columns"}
           (om/build navigation-view (select-keys app [:conn?])))
         (tdom/div {:class "large-3 columns"
                    :dangerouslySetInnerHTML {:__html "&nbsp;"}}))
     (case view
       :leagues (om/build leagues-page-view app)
       :ladder (om/build ladder-app app)
       :about (om/build about-page-view app)
       (do (inspect "unknown path " view)
           (om/build ladder-app app)))))

  (init-state
   [_]
   {:mounted true})
  (will-unmount
   [_]
   (om/set-state! owner :mounted false))
  (will-mount
   [_]
   (logm "starting top-level")
   (go-loop []
     (when-let [msg (and (om/get-state owner :mounted) (<! conn-ch))]
       (when (= :error msg)
         (om/transact! app #(assoc % :conn? false))
         (<! (timeout 5000))
         (om/transact! app #(assoc % :conn? true)))
       (recur)))
   (go (loop []
         (when-let [[view' path'] (and (om/get-state owner :mounted)
                                       (<! nav-ch))]
           (inspect view')
           (inspect path')
           (om/transact! app #(assoc %
                                     :view view'
                                     :path path'))
           (recur))))))

(defn run-top-level []
  (om/root top-level
           app-state
           {:target (.getElementById js/document "content")}))

(sec/defroute something-page "/leagues" []
  (logm "in leauges")
  (put! nav-ch [:leagues []]))

(sec/defroute league "/leagues/:id" [id]
  (inspect id)
  (put! nav-ch [:leagues [id]]))

(sec/defroute league-team "/leagues/:id/team/:team" [id team]
  (put! nav-ch [:leagues [id [:team team]]]))

(sec/defroute root-page "/" []
  (put! nav-ch [:ladder []]))

(sec/defroute about-page "/about" []
  (logm "in about")
  (put! nav-ch [:about []]))


(defonce set-location
  (when (clojure.string/blank? (-> js/document
                                   .-location
                                   .-hash))
    (-> js/document
        .-location
        (set! "#/"))))

(defonce last-hash (atom ""))

(defn get-hash []
  (-> js/document
      .-location
      .-hash))

(sec/dispatch! (get-hash))
