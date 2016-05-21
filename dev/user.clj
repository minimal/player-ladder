(ns user
  (:require [cemerick.piggieback :as piggieback]
            [clojure.java.io :as io]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojurescript-build.auto :as auto]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            expectations
            [ladder.core :as core]
            [ladder.system :as system]
            [reloaded.repl :refer [system init start stop go reset]]))

(reloaded.repl/set-init! #(system/make-system {:is-dev? true :db-file "results.edn"}))

(comment (defn file-exists? [path]
           (.exists (io/file path))))

(defn- mark-tests-as-unrun []
  (let [all (->> (all-ns)
                 (mapcat (comp vals ns-interns)))
        previously-ran-tests (filter (comp :expectations/run meta) all)]
    (doseq [test previously-ran-tests]
      (alter-meta! test dissoc :expectations/run :status))))

(defn reset-run-tests []
  (reset)
  (mark-tests-as-unrun)
  (expectations/run-all-tests))
