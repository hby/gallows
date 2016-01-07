(ns gallows.routes.websockets
  (:require [compojure.core :refer [GET defroutes]]
            [org.httpkit.server
             :refer [send! with-channel on-close on-receive]]
            [cognitect.transit :as t]
            [clojure.tools.logging :as log])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))

(defonce channels (atom #{}))

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

(defn send-ws-message
  "msg is encoded as transit data and sent to channels
   channels - web socket channels
   msg     - Clojure data to send"
  [msg & channels]
  (let [tmsg (transit-write msg)]
    (doseq [channel channels]
      (send! channel tmsg))))


(defn notify-all-clients [msg]
  (do
    (log/info "notifying all clients:" msg)
    (apply send-ws-message msg @channels)))

(defn ->message
  "convenince web socket message creator"
  [type payload]
  {:type type
   :payload payload})


;;; ws messages ;;;

;;
;; :new-message
;;
(defn new-message
  [payload]
  (notify-all-clients (->message :new-message payload)))

;;
;; :new-player
;;
(defn new-player [{:keys [player]}]
  )



;;; Web Socket connect, disconnect, and raw handler

(defn handle-ws-message
  "pass payload to function determined by (msg :type)"
  [{:keys [type payload] :as msg}]
  (do
    (log/info "handling ws message:" msg)
    (case type
      :new-message (new-message payload)
      :new-player (new-player payload)
      )))

(defn connect! [channel]
  (log/info "channel open:" channel)
  (swap! channels conj channel))

(defn disconnect! [channel status]
  (log/info "channel closed:" status channel)
  (swap! channels #(remove #{channel} %)))

(defn ws-handler [request]
  (with-channel request channel
                (connect! channel)
                (on-close channel (partial disconnect! channel))
                (on-receive channel #(handle-ws-message (transit-read %)))))

(defroutes websocket-routes
           (GET "/ws" request (ws-handler request)))

