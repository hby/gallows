(ns gallows.config
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[gallows started successfully]=-"))
   :middleware identity})
