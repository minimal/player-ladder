(ns ^:figwheel-load
  react-tutorial-om.app
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :as async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-tools.dom :as tdom :include-macros true]
            [om-tools.core :refer-macros [defcomponent defcomponentk]]
            [om-tools.schema :as schema]
            [schema.core :as s :refer-macros [defschema]]
            [secretary.core :as sec :include-macros true]
            [sablono.core :as html :refer-macros [html]]
            [goog.history.EventType :as EventType]
            [cljs-http.client :as http]
            ;; [clairvoyant.core :as trace :include-macros true]
            [clojure.string :as str]
            ;; [omdev.core :as omdev]
            [react-tutorial-om.utils :refer [guid] :refer-macros [logm]])
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


;; Schemas

(s/defschema Nat
  (s/both s/Int
          (s/pred #(not (neg? %)) "Zero or more")))


(s/defschema Match
  {:opposition s/Str
   :for Nat
   :against Nat
   (s/optional-key :round) (s/maybe s/Int)
   :date s/Inst})

(s/defschema LeagueRanking
  {(s/optional-key :rd) (s/maybe s/Int)
   (s/optional-key :rank) Nat,
   :matches [Match]
   (s/optional-key :round) (s/maybe s/Int)
   :team s/Str
   :draw Nat
   :loses Nat
   :for Nat
   :against Nat
   :diff s/Int
   :wins Nat
   :points Nat})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Util

(defn- with-id
  [m]
  (assoc m :id (guid)))

(defn- fetch-matches
  "The comments need to be a vector, not a list. Not sure why."
  [app opts]
  (go (let [{{matches :matches} :body} (<! (http/get (:url opts) {:accept "application/transit+json"}))]
        (when matches
          (om/transact!
           app #(assoc % :matches matches))))))

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
                    (assoc :conn? true)))
          (om/transact!
           app #(assoc % :conn? false))))))

(defn fetch-leagues
  "The comments need to be a vector, not a list. Not sure why."
  [app opts]
  ;; (logm "gonna fetch leagues")
  (go (let [{:keys [success status body] :as resp} (<! (http/get
                                                        "/leagues"
                                                        {:accept "application/transit+json"}))]
        (when success
          (om/update! app (:leagues body))))))


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

(defonce nav-ch (chan))

(defn display [show]
  (if show
    #js {}
    #js {:display "none"}))

(defn ladder-match-row
  [{:keys [winner winner-score loser loser-score date]} owner opts]
  (om/component
   (apply dom/tr #js {:className "comment"}
          (map #(dom/td nil %) [winner winner-score loser-score loser]))))

(defn comment-list [{:keys [matches]}]
  (om/component
   (dom/table #js {:className "commentList"}
              (dom/thead nil (apply dom/tr nil
                                    (map #(dom/th nil %) ["winner" "" "" "loser"])))
              (apply
               dom/tbody nil
               (om/build-all ladder-match-row (take 20 (reverse matches)))))))

(defn save-match!
  [match app opts]
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
  (go (let [res (<! (http/post (:url opts) {:transit-params match}))]
        (when (:success res)
          (logm "saved league:" res)
          (fetch-leagues app opts))
        res)))

(defn handle-league-result-submit
  [e app owner opts {:keys [home-score away-score home away name id round]
                     :as result}]
  (let [[home-score away-score] (map js/parseInt [home-score away-score])
        [winner winner-score] (if (> home-score away-score)
                                [home home-score]
                                [away away-score])
        [loser loser-score] (if (= winner home)
                              [away away-score]
                              [home home-score])]
    (if (and (> winner-score loser-score)
             (if (= "second-division" name)
               (= 2 winner-score)
               (= 3 winner-score)))
      (do (save-league-match! {:winner winner :winner-score winner-score
                               :loser loser :loser-score loser-score
                               :id id :round round}
                              app {:url (str "/leagues/" name "/result")})
          (om/transact! app [(keyword name) :schedule]
                        (fn [s] (remove #(= (:id %) id) s))))
      (logm "Warning: invalid score"))
    (logm "onsubmit" result "winner" winner)))

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

(defn handle-change [e owner key]
  (om/set-state! owner key (.. e -target -value)))

(defn comment-form
  [app owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:winner "" :winner-score ""
       :loser "" :loser-score ""})
    om/IRenderState
    (render-state [this state]
      (dom/form
       #js {:className "commentForm" :onSubmit #(handle-submit % app owner opts state)}
       (dom/input #js {:type "text" :placeholder "Winner" :ref "winner"
                       :value (:winner state) :list "players"
                       :onChange #(handle-change % owner :winner)})
       (dom/input #js {:type "number"  :placeholder "Score" :ref "winner-score"
                       :value (:winner-score state)
                       :onChange #(handle-change % owner :winner-score)})
       (dom/input #js {:type "text" :placeholder "Loser" :ref "loser"
                       :value (:loser state) :list "players"
                       :onChange #(handle-change % owner :loser)})
       (dom/input #js {:type "number" :placeholder "Score" :ref "loser-score"
                       :value (:loser-score state)
                       :onChange #(handle-change % owner :loser-score)})
       (dom/input #js {:className "button" :type "submit" :value "Post"})
       (apply dom/datalist #js {:id "players"}
              (map #(dom/option #js {:value %})
                   (:players app)))))))

(defn comment-box [app owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:mounted true})
    om/IWillMount
    (will-mount [_]
      (go (while (om/get-state owner :mounted)
            (fetch-matches app opts)
            (<! (timeout (:poll-interval opts))))))
    om/IWillUnmount
    (will-unmount [_]
      (om/set-state! owner :mounted false))
    om/IRender
    (render [_]
      (dom/div
       #js {:className "commentBox"}
       (dom/h3 nil "Results (most recent first)")
       (om/build comment-form app {:opts opts})
       (om/build comment-list app)))))

(defn last-10-games [results owner]
  (om/component
   (html [:ul.last-10-games {:style {:width "130px"}}
          (for [game (take-last 10 results)]
            (let [win? (> (:for game) (:against game))]
              [:li {:class (str (if win? "win" "loss") " hover")}
               [:span ""]
               [:.atooltip (str (if win? "Win " "Loss ")
                                (:for game) " - " (:against game)
                                " against " (:opposition game)
                                " @ " (:date game))]]))])))


(defn player-summary
  [{{:keys [team ranking rd wins loses suggest matches] :as fields} :data
    show :display} owner opts]
  (om/component
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

(defn ranking
  [{:keys [team rank ranking rd wins loses suggest] :as fields} owner opts]
  (reify
    om/IRenderState
    (render-state [this {:keys [select-player-ch]}]
      (apply dom/tr nil
             (map #(dom/td nil %)
                  [rank
                   (dom/span #js {:onClick (fn [e]
                                             (put! select-player-ch team)
                                             (.stopPropagation e))
                                  :style #js {:cursor "pointer"}}
                             team)
                   ranking (.toFixed (/ wins loses) 2) suggest
                   (om/build last-10-games (:matches fields))])))))

(defn ranking-list [rankings owner opts]
  (om/component
   (dom/table #js {:className "rankingTable"}
              (dom/thead nil
                         (apply dom/tr nil
                                (map #(dom/th nil %)
                                     ["" "team" "ranking" "w/l"
                                      "suggested opponent" "last 10 games"])))
              (apply
               dom/tbody nil
               (om/build-all
                ranking
                rankings
                {:init-state {:select-player-ch (:select-player-ch opts)}})))))


(defn rankings-box [app owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:mounted true})
    om/IWillMount
    (will-mount [_]
      (prn "will mount")
      (go (while (om/get-state owner :mounted)
            (fetch-rankings app opts)
            (<! (timeout (:poll-interval opts))))))
    om/IWillUnmount
    (will-unmount [_]
      (logm "unmounting!!!!!")
      (om/set-state! owner :mounted false))
    om/IRender
    (render [_]
      (dom/div
       #js {:className "rankingsBox"}
       (dom/h3 nil "Rankings")
       (om/build ranking-list (:rankings app) {:opts opts})))))

(defcomponent league-row [{:keys [team wins loses points matches for against diff
                                  rank change]} ;;:- (schema/cursor LeagueRanking)
                          owner opts]
  (render
   [_]
   (html [:tr
          [:td rank]
          [:td {:style {:color (case change
                                 :+ "#2c7e00"
                                 :- "#a8160c"
                                 "")}}
           (case change
             :+ "▲"
             :- "▼"
             "")]
          [:td team]
          [:td (+ wins loses)]
          [:td wins]
          [:td loses]
          [:td for]
          [:td against]
          [:td diff]
          [:td points]
          [:td (om/build last-10-games matches)]])))

(defcomponent league-schedule-row [{:keys [round home id away name] :as app} owner opts]
  (init-state [_] {:home-score 0 :away-score 0})
  (render-state
   [_ state]
   (let [leagues (om/observe owner (league-items))]
     (html
      [:.row
       [:form
        {:class "league-form"
         :on-submit #(do (handle-league-result-submit
                          % leagues owner opts
                          (merge state
                                 {:round round :name name
                                  :id id :home home :away away}))
                         (.preventDefault %))}
        [:.large-10.colums
         [:.large-1.columns round]
         [:.large-4.columns
          [:label {:for "moo"} home]
          [:select {:id "moo" :value (:home-score state)
                    :on-change #(handle-change % owner :home-score)}
           (for [n (range 4)]
             [:option {:value (str n)} n])]]
         [:.large-1.columns "vs"]
         [:.large-4.columns
          [:label {:for "foo"} away]
          [:select {:id "foo" :value (:away-score state)
                    :on-change #(handle-change % owner :away-score)}
           (for [n (range 4)]
             [:option {:value (str n)} n])]]
         [:input {:class "button tiny" :type "submit" :value "Post"}]]]]))))

(defcomponent league-schedule [{:keys [name schedule]} owner opts]
  (render
   [_]
   ;; (logm schedule)
   (html
    [:div
     [:h4.subheader "Schedule"]
     (for [row schedule]
       (om/build league-schedule-row (assoc row :name name) {:react-key (guid)}))])))

(defcomponent league-list [league owner opts]
  (init-state
   [_]
   {:mounted false})
  (render
   [_]
   ;; (logm league)
   (html
    [:div
     [:h3 (:name league)]
     [:table.rankingTable
      [:thead
       (for [header ["" "" "" "P" "W" "L" "F" "A" "Diff" "Pts" "Last 10 Games"]]
         [:th header])]
      [:tbody
       (om/build-all league-row (:rankings league) {:key :team})]]
     (om/build league-schedule {:name (:name league) :schedule (:schedule league)})])))

(defcomponent status-box [conn? owner]
  (render [_]
          (html [:.alert-box.warning.radius {:style (display (not conn?))}
                 "Connection problem!"])))

(defcomponent navigation-view [_ _]
  (render
   [_]
   (let [style {:style {:margin "10px;"}}]
     (tdom/div style
               (tdom/a (assoc style :href "#/")
                       "Ladder")
               (tdom/a (assoc style :href "#/leagues")
                       "Leagues")
               (tdom/a (assoc style :href "#/about")
                       "About")))))


(defn ladder-app [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:select-player-ch (chan)})
    om/IWillMount
    (will-mount [_]
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
    om/IWillUnmount
    (will-unmount [_]
      (async/close! (om/get-state owner :select-player-ch)))
    om/IRenderState
    (render-state [this {:keys [select-player-ch]}]
      (dom/div #js {:className "row"}
               (dom/div #js {:className "large-2 columns"
                             :dangerouslySetInnerHTML #js {:__html "&nbsp;"}})
               (dom/div #js {:className "large-7 columns"}
                        (om/build status-box (:conn? app))
                        (om/build navigation-view {})
                        (om/build rankings-box app
                                  {:opts {:poll-interval 2000
                                          :url "/rankings"
                                          :select-player-ch select-player-ch}})
                        (om/build comment-box app
                                  {:opts {:poll-interval 2000
                                          :url "/matches"}}))
               (dom/div #js {:className "large-3 columns"}
                        (om/build
                         player-summary
                         {:data  (first
                                  (filter #(= (:team %)
                                              (get-in app [:player-view :player]))
                                          (:rankings app)))
                          :display (get-in app [:player-view :display])}))))))


(defcomponent leagues-page-view [{:keys [leagues path] :as data} owner opts]
  (render-state
   [this state]
   (tdom/div {:className "row results-row"}
     (tdom/div {:className "large-2 columns"
                :dangerouslySetInnerHTML {:__html "&nbsp;"}})
     (tdom/div {:className "large-7 columns"}
       #_(om/build status-box (:conn? data))
       (om/build navigation-view {})
       (tdom/h3 "Leagues")

       (tdom/ul (for [[league _] leagues]
                  (tdom/li {:key (name league)} (tdom/a {:href (str "#/leagues/" (name league))}
                                                        (name league)))))
       (if (seq path)
         (tdom/div {:style (display (seq path))}
           (om/build league-list (leagues (keyword (first path)))))))
     (tdom/div {:className "large-3 columns" :dangerouslySetInnerHTML {:__html "&nbsp;"}})))
  (init-state
   [_]
   {:mounted true})
  (will-mount
   [_]
   (prn "will mount leagues")
   (logm  opts)
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
   (dom/div #js {:className "row results-row"}
            (dom/div #js {:className "large-2 columns"
                          :dangerouslySetInnerHTML #js {:__html "&nbsp;"}})
            (dom/div #js {:className "large-7 columns"}
                     (om/build navigation-view {})
                     (tdom/div nil (tdom/a {:href "https://github.com/minimal/player-ladder"}
                                           "Fork me on Github")))
            (dom/div #js {:className "large-3 columns" :dangerouslySetInnerHTML #js {:__html "&nbsp;"}}))))

(defn leagues []
  (om/root leagues-page-view
           app-state ;(:leagues app-state)
           {:target (.getElementById js/document "content")}))

(defn main [& [root]]
  (om/root ladder-app
           app-state
           {:target (.getElementById js/document "content")
            :tx-listen
            (fn [tx-data root-cursor]
              #_(println "listener 1: " tx-data))}))

(defcomponent top-level [{:keys [view path] :as app} owner]
  (render
   [this]
   (case view
     :leagues (om/build leagues-page-view app)
     :ladder (om/build ladder-app app)
     :about (om/build about-page-view app)
     (do (logm "unknown path " view)
         (om/build ladder-app app))))

  (will-mount
   [_]
   (logm "starting top-level")
   (go (loop []
         (when-let [[view' path'] (<! nav-ch)]
           (logm view')
           (logm path')
           (om/transact! app #(assoc %
                                     :view view'
                                     :path path'))
           (recur))))))

(defn run-top-level []
  (om/root top-level
           app-state
           {:target (.getElementById js/document "content")}))

(defn about []
  (om/root about-page-view
           app-state
           {:target (.getElementById js/document "content")
            :shared {:route :about}}))

(sec/defroute something-page "/leagues" []
  (logm "in leauges")
  (put! nav-ch [:leagues []]))

(sec/defroute league "/leagues/:id" [id]
  (logm id)
  (put! nav-ch [:leagues [id]]))

(sec/defroute root-page "/" []
  (put! nav-ch [:ladder []]))

(sec/defroute about-page "/about" []
  (logm "in about")
  (put! nav-ch [:about []]))

#_(sec/defroute "*" []
    (main))



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
(run-top-level)
