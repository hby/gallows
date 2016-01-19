(ns gallows.game
  (:require [clojure.string :as s]
            [goog.string :as gstring]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [ajax.core :refer [GET POST]]
            [gallows.state :as gs]
            [gallows.websockets :as ws]
            )
  (:import goog.History))


;;; Game logic

(defn send-to-server
  "Sends a message over ws to server.

   type    - message type (typically a keyword)
   payload - the message payload"
  [type payload]
  (reset! gs/message "")
  (ws/send-transit-msg! {:type type
                         :payload payload}))

(defn update-name
  "Sends :update-name message to server and updates name game state.

   n - name to send in payload. If name is empty
       'Anonymous' is sent"
  [n]
  (let [name (if (empty? n) "Anonymous" n)]
    (reset! gs/player name)
    (send-to-server :update-name {:name name})))

(defn add-word
  "Sends :add-word message to server and updates word game state.

   w - word to send in payload."
  [w]
  (when (not (empty? w))
    (reset! gs/word w)
    (send-to-server :add-word {:word w})))

(defn start-game
  "Starts a game with a player by updating the game state.

   player - immutable player from the players state."
  [{:keys [name id word] :as player}]
  (reset! gs/game {:player player
                   :hangman (mapv identity (player :word))
                   :letters (mapv identity "abcdefghijklmnopqrstuvwxyz")
                   :guessed #{}
                   :correct #{}}))

(defn win?
  "A game is won when all of hangman are in guessed
   (expect for spaces in hangman).

   game - immutable game"
  [game]
  (let [hletters (remove #(= % " ") (set (game :hangman)))
        guessed (game :guessed)]
    (every? guessed hletters)))

(defn lose?
  "A game is lost on the 6th incorrect guess.

   game - immutable game"
  [game]
  (>= ( -
        (-> game :guessed count)
        (-> game :correct count))
      6))

(defn game-report-payload
  "Return a payload for a :report-game message.

   game - immutable game"
  [game]
  {:outcome (if (win? game) :won :lost)
   :player (game :player)})

(defn report-game
  "Sends :report-game message to server.

   game - immutable game"
  [game]
  (if (or (win? game) (lose? game))
    (send-to-server :report-game (game-report-payload game))))

(defn guess-letter
  "Update game state according to letter guessed.
   A game report is sent on a win or lose.

   l - the guessed letter"
  [l]
  (letfn [(updater [g l]
            (-> g
                (update-in [:guessed] #(conj % l))
                (update-in [:correct] #(if (some (set (g :hangman)) l)
                                        (conj % l)
                                        %))))]
    (report-game (swap! gs/game updater l))))


;;; WS Messages
;;; These function names correspond to server message types

;
; :set-players
;
(defn set-players
  "Sets the players game state to the players
   in the payload. They are sorted by name."
  [{new-players :players}]
  (reset! gs/players (vec (sort-by :name new-players))))

;
; :set-message
;
(defn set-message
  "Sets the message game state to the message
   in the payload."
  [{new-message :message}]
  (reset! gs/message new-message))

;
; :game-report
;
(defn game-report
  "Adds a game report message in the payload
   to the reports state."
  [{new-report :message}]
  (swap! gs/reports conj new-report))

;
; :ping
;
(defn ws-ping
  "Does nothing"
  [_]
  ; do nothing
  )


;;; Websocket layer messsage delegation

(defn handle-message
  "Perform the game logic for server message.
   Dispatch payload according to (msg :type).

   channel - websocket channel
   msg     - message type and payload from client"
  [{:keys [type payload]}]
  (case type
    :ping (ws-ping payload)
    :game-report (game-report payload)
    :set-players (set-players payload)
    :set-message (set-message payload)
    ))

(defn handle-close
  "Handle ws close

   channel - websocket channel"
  []
  (reset! gs/overlay "The connection to the server has disconnected. Please reload your browser."))
