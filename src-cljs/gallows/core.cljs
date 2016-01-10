(ns gallows.core
  (:require [reagent.core :as reagent :refer [atom]]
            [gallows.websockets :as ws]
    ;[reagent.session :as session]
    ;[secretary.core :as secretary :include-macros true]
    ;[goog.events :as events]
    ;[goog.history.EventType :as HistoryEventType]
    ;[markdown.core :refer [md->html]]
    ;[ajax.core :refer [GET POST]]
            )
  ;(:import goog.History)
  )

(defonce message (atom ""))

(defonce player (atom ""))

;; [{ :name ""   ; player name
;;    :id   ""   ; player id
;;    :word "" } ; word to guess
;;  ...]
(defonce players (atom []))

(defonce word (atom ""))

;; { :role nil    ; :guesser or :worder
;;   :other nil } ; other player name
(defonce game (atom nil))


(defn start-game
  [{:keys [name id word]}]
  (println "game with" name ":" id ":" word))

(defn send-ws-message
  [type payload]
  (reset! message "")
  (ws/send-transit-msg! {:type type
                         :payload payload}))

(defn update-name-ws
  [n]
  (reset! player n)
  (send-ws-message :update-name {:name n}))

(defn add-word-ws
  [w]
  (reset! word w)
  (send-ws-message :add-word {:word w}))

(defn player-name
  []
  [:div [:strong "You are "]
   [:span {:style {:color "green"}} @player]])

(defn current-word
  []
  [:div [:em "current word: "]
   [:span @word]])

(defn player-li
  [[i player]]
  (if (player :word)
    [:a
     {:onClick #(start-game (@players i))}
     (player :name)]
    (player :name)))

(defn player-list []
  [:ul
   (for [[i _ :as ip] (map-indexed vector @players)]
     ^{:key i}
     [:li (player-li ip)])])

(defn message-view []
  [:span
   @message])

(defn player-name-field []
  (let [value (atom nil)]
    (fn []
      [:input.form-control
       {:type :text
        :placeholder "who are you?"
        :value @value
        :on-change #(let [tv (-> % .-target .-value)]
                     (if (< (count tv) 11)
                       (reset! value tv)))
        :on-key-down #(when (= (.-keyCode %) 13)
                       (update-name-ws @value)
                       (reset! value nil))}])))

(defn word-field []
  (let [value (atom nil)]
    (fn []
      [:input.form-control
       {:type :text
        :placeholder "what's the word?"
        :value @value
        :on-change #(let [tv (-> % .-target .-value)]
                     (if (< (count tv) 16)
                       (reset! value tv)))
        :on-key-down #(when (= (.-keyCode %) 13)
                       (add-word-ws @value)
                       (reset! value nil))}])))

(defn letter-input []
  (let [value (atom nil)]
    (fn []
      [:input.form-control
       {:type :text
        :placeholder "guess letters"
        :value @value
        :on-change #(reset! value (-> % .-target .-value))
        :on-key-down #(when (= (.-keyCode %) 13)
                       (send-ws-message :guess-letter
                                        {:letter @value})
                       (reset! value nil))}])))

(defn game-view []
  (if @game
    [letter-input]
    [:span "Pick a name to play their word"]))

(defn home-page []
  [:div.container
   [:div.row

    [:div.col-sm-3
     [message-view]
     [:h4 "Enter The Gallows"]
     [:div
      [player-name-field]
      [player-name]
      [:br]
      [word-field]
      [current-word]]
     [:br]
     [:div [:strong "Them: "]
      [player-list]]]

    [:div.col-sm-9
     [:div.game
      [game-view]]]]
   ]
  )


;;; ws messages ;;;

;;
;; :set-players
;;
(defn set-players! [{new-players :players}]
  (reset! players (vec (sort-by :name new-players))))

;;
;; :set-message
;;
(defn set-message! [{new-message :message}]
  (reset! message new-message))

;;
;; :guess-letter
;;
(defn guess-letter [{:keys [letter]}]
  )

;;; mount components and set up ws connection ;;;

(defn receive-ws-message
  [{:keys [type payload]}]
  (do
    (println "got ws message type:" type "payload:" payload)
    (case type
      :set-players (set-players! payload)
      :set-message (set-message! payload)
      :guess-letter (guess-letter payload)
      )))

(defn mount-components []
  (reagent/render-component [#'home-page] (.getElementById js/document "app")))

(defn init! []
  (ws/make-websocket! (str "ws://" (.-host js/location) "/ws") receive-ws-message)
  (mount-components))
