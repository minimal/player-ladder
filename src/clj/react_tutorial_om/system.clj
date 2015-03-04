(ns react-tutorial-om.system
  (:require [react-tutorial-om.core :as core]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn make-system [is-dev? & [slack-url]]
  (component/system-map
   :webserver (core/new-webserver {:ring {:port 3000 :join? false}
                                   :slack-url slack-url
                                   :is-dev? is-dev?})))

(defn -main
  [& args]
  (component/start (make-system false)))
