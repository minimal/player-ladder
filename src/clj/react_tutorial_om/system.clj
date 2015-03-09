(ns react-tutorial-om.system
  (:require [react-tutorial-om.core :as core]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn make-system [{:keys [is-dev? db-file slack-url port]}]
  (component/system-map
   :webserver (core/new-webserver {:ring {:port (or port 3000) :join? false}
                                   :slack-url slack-url
                                   :db-file db-file
                                   :is-dev? is-dev?})))

(defn -main
  [file & [port slack-url]]
  (component/start (make-system {:is-dev? false
                                 :db-file file
                                 :port (Integer/parseInt port)
                                 :slack-url slack-url})))
