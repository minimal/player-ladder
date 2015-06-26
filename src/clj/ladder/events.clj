(ns ladder.events
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.core.async :refer [<! chan go-loop pub sub]]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [try+]]))


(defn format-league-bound-players [[best1 best2] [worst1 worst2] league]
  (format (str/join "\n" ["\t\tLeague %s:"
                          "\t\tTop players:"
                          "\t\t\t\t%s.- %s"
                          "\t\t\t\t%s.- %s"
                          "\t\tBottom players:"
                          "\t\t\t\t%s.- %s"
                          "\t\t\t\t%s.- %s"])
          (name league)
          (:rank best1) (:team best1)
          (:rank best2) (:team best2)
          (:rank worst1) (:team worst1)
          (:rank worst2) (:team worst2)))

(defn post-to-slack
  "Post a params map to slack. Returns a channel or nil if noop"
  [slack-url params]
  (when-not (str/blank? slack-url)
    (async/thread
      (try+
       (client/post slack-url
                    {:form-params params
                     :content-type :json})
       (catch [:status 403] {:keys [request-time headers body]}
         (println "Slack 403 " request-time headers))
       (catch Object _
         (println (:throwable &throw-context) "unexpected error"))))))

(defn post-league-result-to-slack [{:keys [winner loser winner-score loser-score league
                                           rankings]}
                                   slack-url]
  (let [match-text (format "%s wins against %s in league %s: %s - %s"
                           winner loser (name league) winner-score loser-score)
        bound-text (format-league-bound-players (map #(select-keys % [:team :rank]) rankings)
                                                (map #(select-keys % [:team :rank]) (take-last 2 rankings))
                                                league)
        league-url (str "http://loris:3000/app#/leagues/" (name league))
        params {:text (str/join "\n\n" [match-text bound-text league-url])}]
    (post-to-slack slack-url params)))

(defn setup-slack-loop [channel prefix slack-url]
  (go-loop []
    (when-let [[topic msg] (<! channel)]
      (case topic
        :league-match (post-league-result-to-slack msg slack-url)
        ;; :bound-players (post-league-bound-players-to-slack msg slack-url)
        (println "Unknown topic " topic))
      (recur))))


(defrecord EventHandler [slack-url]
  component/Lifecycle
  (start [component]
    (let [publisher-ch (chan)
          publication (pub publisher-ch first)
          league-match-ch (chan)]
      (sub publication :league-match league-match-ch)
      (setup-slack-loop league-match-ch "from component" slack-url)
      (assoc component
             :pub-ch publisher-ch
             :publication publication
             :league-match-ch league-match-ch)))
  (stop [component]
    (when-let [pub-ch (:pub-ch component)]
      (async/close! pub-ch))
    (assoc component :pub-ch nil)))
