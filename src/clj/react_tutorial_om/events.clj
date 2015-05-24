(ns react-tutorial-om.events
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.core.async :refer [<! chan go-loop pub sub]]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [try+]]))


(defn format-league-bound-players [{:keys [best1 best2 worst1 worst2 league]}]
  (format (str/join "\n" ["\t\tLeague %s:"
                          "\t\tTop players:"
                          "\t\t\t\t1.- %s"
                          "\t\t\t\t2.- %s"
                          "\t\tBottom players:"
                          "\t\t\t\t1.- %s"
                          "\t\t\t\t2.- %s"])
          (name league) best1 best2 worst1 worst2))

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

(defn post-league-result-to-slack [{:keys [winner loser winner-score loser-score league]}
                                   slack-url]
  (let [params {:text (format "%s wins against %s in league %s: %s - %s"
                              winner loser (name league) winner-score loser-score)}]
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
