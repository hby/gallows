(ns gallows.game
  (:require [clojure.tools.logging :as log]
            [org.httpkit.server :refer [send!]]
            [gallows.routes.transit :refer :all]
            [clojure.set :as set]))


;;; Game state

; channel -> {:name  ""
;             :id    ""
;             :words []}
;
; Game state is a map of ws channels to player data (name, id, and vector of words).
(defonce channels (atom {}))


; An atom for providing a sequence of increasing numbers.
; Used for the player's id.
(defonce id (atom 0N))


(defn next-id
  "Atomically returns the next id."
  []
  (str (swap! id inc)))


(defn id->channel
  "Returns the channel for which uid is a player's id.

   uid - a user id"
  [uid]
  {:pre [(not (nil? uid))]}
  (let [channels @channels]
    (some (fn [[ck {:keys [id]}]] (if (= uid id) ck)) channels)))


(defn channel-data
  "Returns the player data for the channel

   channel - a ws channel"
  [channel]
  (@channels channel))


;;; Message level logic

; Messages are structured as
; {:type    :message-type ; a keyword identifiying the message type
;  :payload {...}         ; a clojure data structure
(defn ->message
  "This is a convenince function for creating consistent messages.
   All messages will be a map with two keys, :type and :payload.
   The value of :payload is a general Clojure dataa structure.

   type    - message type, should be a keyword
   payload - message payload, clojure data structure"
  [type payload]
  {:type type
   :payload payload})


(defn send-to-channels
  "Sends message to all given channel arguments.

   msg      - message to send, clojure data structure
   channels - websocket channels"
  [msg & channels]
  (let [tmsg (transit-write msg)]
    (doseq [channel channels]
      (send! channel tmsg))))


(defn send-to-all-channels
  "Sends message to all connected channels.

   msg - message to send, clojure data structure"
  [msg]
  (do
    (log/debug "notifying all clients:" msg)
    (apply send-to-channels msg (keys @channels))))


;;; Game level logic

(defn set-players-to-all-channels
  "Send a :set-players message to all channels.
   The payload is {:players [players]} where players is all of the connected
   players except the one that is being sent the message."
  []
  (dorun
    (let [channels @channels]
      (for [channel (keys channels)
            :let [players (mapv (fn [m] (set/rename-keys (update-in m [:words] #(last %))
                                                         {:words :word}))
                                (vals (dissoc channels channel)))]]
        (do
          (log/debug "updating players:" players "to" channel)
          (send-to-channels (->message :set-players {:players players}) channel))))))


;;; WS Messages
;;; These function names correspond to client message types

;
; :update-name
;
(defn update-name
  "Updates the player's name associated with the channel.

   channel - websocket channel
   msg     - message payload from client, {:name name}"
  [channel {:keys [name]}]
  (do
    (log/info "updating channel:" channel "name to:" name)
    (swap! channels assoc-in [channel :name] name)
    (set-players-to-all-channels)))

;;
;; :add-word
;;
(defn add-word
  "Adds a new word for the player associated with the channel.

   channel - websocket channel
   msg     - message payload from client, {:word word}"
  [channel {:keys [word] :as msg}]
  (do
    (swap! channels update-in [channel :words] conj word)
    (set-players-to-all-channels)))

;;
;; :report-game
;;
(defn report-game
  "Send a message to a player that the player associated with the channel
   won or lost a game with their word.

   channel - websocket channel
   msg     - message payload from client, {:outcome outcome :player player}
             where outcome - :win or :lose
                   player  - client game player data to send the message"
  [channel {:keys [outcome player] :as msg}]
  (let [worler (-> (channel-data channel) :name) ; player name that won/lost
        word (-> player :word)
        report-channel (id->channel (-> player :id)) ; channel of player that has the word
        msg (str worler " " (name outcome) " on your word '" word "'")]
    (do
      (log/info msg)
      (when report-channel
        (send-to-channels (->message :game-report {:message msg}) report-channel)))))


;;; Websocket layer messsage delegation and lifecycle

(defn handle-message
  "Perform the game logic for client message.
   Dispatch payload according to (msg :type).

   channel - websocket channel
   msg     - message type and payload from client"
  [channel {:keys [type payload] :as msg}]
  (do
    (log/debug "handling ws message:" msg)
    (case type
      :update-name (update-name channel payload)
      :add-word (add-word channel payload)
      :report-game (report-game channel payload)
      )))


(defn add-player
  "Adds the player channel to the game.

   channel - websocket channel"
  [channel]
  (swap! channels assoc channel {:name "Anonymous"
                                 :id (next-id)
                                 :words []})
  (send-to-channels (->message :set-message {:message "Welcome!"}) channel)
  ; update each client's list of players
  (set-players-to-all-channels))


(defn remove-player
  "Removes the player channel from the game.

   channel - websocket channel"
  [channel]
  (swap! channels dissoc channel)
  ; update each client's list of players
  (set-players-to-all-channels))
