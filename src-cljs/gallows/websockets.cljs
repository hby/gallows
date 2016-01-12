(ns gallows.websockets
  (:require [cognitect.transit :as t]))

; The websocket
(defonce ws-chan (atom nil))

; Transit reader/write funcitons
(def json-reader (t/reader :json))
(def json-writer (t/writer :json))

(defn receive-transit-msg!
  "Helper function that returns a function that takes
   a transit message and decodes it before calling update-fn.

   update-fn - function of one arg"
  [update-fn]
  (fn [tmsg]
    (update-fn
      (->> tmsg .-data (t/read json-reader)))))

(defn send-transit-msg!
  "Sends msg on websocket (if there is one)
   after transit encoding.

   msg - message to send"
  [msg]
  (if @ws-chan
    (.send @ws-chan (t/write json-writer msg))
    (throw (js/Error. "Websocket is not available!"))))

(defn make-websocket!
  "Sets up a websocket.

   url             - server ws url
   receive-handler - function called when messages are received
                     after transit decoding"
  [url receive-handler]
  (println "attempting to connect websocket")
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) (receive-transit-msg! receive-handler))
      (reset! ws-chan chan)
      (println "Websocket connection established with: " url))
    (throw (js/Error. "Websocket connection failed!"))))

