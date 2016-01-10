(ns gallows.routes.websockets
  (:require [compojure.core :refer [GET defroutes]]
            [org.httpkit.server
             :refer [send! with-channel on-close on-receive]]
            [cognitect.transit :as t]
            [clojure.tools.logging :as log]
            [clojure.set :as set])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))

;; channel -> {:name  ""
;;             :id    ""
;;             :words []}
(defonce channels (atom {}))

(defonce id (atom 0N))
(defn next-id []
  (str (swap! id inc)))

(defn id->channel
  "returns the channel key in the channels atom which
  has the value of qid for the :id value of the map under that key."
  [qid]
  {:pre [(not (nil? qid))]}
  (let [channels @channels]
    (some (fn [[ck {:keys [id]}]] (if (= qid id) ck)) channels)))

(defn channel-data
  [channel]
  (@channels channel))

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

(defn notify-clients
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
    (apply notify-clients msg (keys @channels))))

(defn ->message
  "convenince web socket message creator"
  [type payload]
  {:type type
   :payload payload})

(defn update-all-client-players
  []
  (dorun
    (let [channels @channels]
      ;(log/info channels)
      (for [channel (keys channels)
            :let [players (mapv (fn [m] (set/rename-keys (update-in m [:words] #(last %)) {:words :word})) (vals (dissoc channels channel)))]]
        (do
          (log/info "updating players:" players "to" channel)
          (notify-clients (->message :set-players {:players players}) channel))))))


;;; ws messages ;;;
;;
;; :update-name
;;
(defn update-name
  [channel {:keys [name]}]
  (do
    (log/info "updating channel:" channel "name to:" name)
    (swap! channels assoc-in [channel :name] name)
    (update-all-client-players)))

;;
;; :add-word
;;
(defn add-word
  [channel {:keys [word]}]
  (do
    (swap! channels update-in [channel :words] conj word)
    (update-all-client-players)))

;;
;; :guess-letter
;;
(defn guess-letter [{:keys [letter]}]
  (log/info "guessed: " letter)
  )


;;; Web Socket connect, disconnect, and raw handler

(defn handle-ws-message
  "pass payload to function determined by (msg :type)"
  [channel {:keys [type payload] :as msg}]
  (do
    (log/info "handling ws message:" msg "channel:" channel)
    (case type
      :update-name (update-name channel payload)
      :add-word (add-word channel payload)
      :guess-letter (guess-letter payload)
      )))

(defn connect! [channel]
  (log/info "channel open:" channel)
  (swap! channels assoc channel {:name "Anonymous"
                                 :id (next-id)
                                 :words []})
  (notify-clients (->message :set-message {:message "Welcome!"}) channel)
  (update-all-client-players))

(defn disconnect! [channel status]
  (log/info "channel disconnected:" channel "status:" status)
  (swap! channels dissoc channel)
  (update-all-client-players))

(defn ws-handler [request]
  (with-channel request channel
                (connect! channel)
                (on-close channel (partial disconnect! channel))
                (on-receive channel #(do
                                      (log/info "channel:" channel)
                                      (handle-ws-message channel (transit-read %))))))

(defroutes websocket-routes
           (GET "/ws" request (ws-handler request)))

