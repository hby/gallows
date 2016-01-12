(ns gallows.routes.transit
  (:require [cognitect.transit :as t])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

;;; Transit helpers

(defn transit-read
  "return Cloure data given transit data"
  [tmsg]
  (let [in (ByteArrayInputStream. (.getBytes tmsg))
        reader (t/reader in :json)
        msg (t/read reader)]
    msg))

(defn transit-write
  "return transit data given Clojure data"
  [msg]
  (let [out (ByteArrayOutputStream. 4096)
        writer (t/writer out :json)
        _ (t/write writer msg)]
    (.toString out)))

