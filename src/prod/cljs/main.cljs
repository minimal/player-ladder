(ns ladder.main
  (:require [ladder.app :as app]
            [ladder.utils :refer [guid] :refer-macros [logm]]))

(app/run-top-level)
