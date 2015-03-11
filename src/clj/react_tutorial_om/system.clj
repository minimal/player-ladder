(ns react-tutorial-om.system
  (:require [react-tutorial-om.core :as core]
            [com.stuartsierra.component :as component]
            [react-tutorial-om.events :as events])
  (:gen-class))

(defn make-system [{:keys [is-dev? db-file slack-url port]}]
  (component/system-map
   :event-handler (events/->EventHandler slack-url)
   :webserver (component/using
               (core/new-webserver {:ring {:port (or port 3000) :join? false}
                                    :slack-url slack-url
                                    :db-file db-file
                                    :is-dev? is-dev?})
               [:event-handler])))

(defn -main
  [file & [port slack-url]]
  (component/start (make-system {:is-dev? false
                                 :db-file file
                                 :port (Integer/parseInt port)
                                 :slack-url slack-url})))
