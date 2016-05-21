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

(reloaded.repl/set-init! #(system/make-system {:is-dev? true :db-file #_"example.edn" "results.loris.edn"}))

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

(comment
  (in-ns 'clojure.core)

                                        ; from 1242c48
  (defn- print-object [o, ^Writer w]
    (when (instance? clojure.lang.IMeta o)
      (print-meta o w))
    (.write w "#<")
    (let [name (.getSimpleName (class o))]
      (when (seq name) ;; anonymous classes have a simple name of ""
        (.write w name)
        (.write w " ")))
    (.write w (str o))
    (.write w ">"))

  (defmethod print-method Throwable [^Throwable o ^Writer w]
    (print-object o w))
  (in-ns 'user))
