(ns gallows.core
  (:require [reagent.core :as reagent :refer [atom]]
            [gallows.websockets :as ws]
            [clojure.string :as s]
            [goog.string :as gstring]
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

;; If there is a game then this has game data,
;; otherwise nil.
(defonce game (atom nil))


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

(defn start-game
  "starting a game with a player"
  [{:keys [name id word] :as player}]
  (reset! game {:player player
                :hangman (mapv identity (player :word))
                :letters (mapv identity "abcdefghijklmnopqrstuvwxyz")
                :guessed #{}
                :correct #{}}))

(defn win?
  "you win when all of hangman are in guessed
   (expect for spaces in hangman)"
  [game]
  (let [hletters (remove #(= % " ") (set (game :hangman)))
        guessed (game :guessed)]
    (every? guessed hletters)))

(defn lose?
  "you lose on the 6th incorrect guess"
  [game]
  (>= ( -
        (-> game :guessed count)
        (-> game :correct count))
      6))

(defn guess-letter
  [l]
  (letfn [(updater [g l]
            (-> g
                (update-in [:guessed] #(conj % l))
                (update-in [:correct] #(if (some (set (g :hangman)) l)
                                        (conj % l)
                                        %))))]
    (swap! game updater l)))

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
     {:href "#"
      :onClick #(start-game player)}
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
        :placeholder "what's your word?"
        :value @value
        :on-change #(let [tv (-> % .-target .-value)]
                     (if (< (count tv) 16)
                       (reset! value (s/lower-case tv))))
        :on-key-down #(when (= (.-keyCode %) 13)
                       (add-word-ws (s/lower-case @value))
                       (reset! value nil))}])))

(defn nbsp
  ([] (nbsp 1))
  ([n] (repeat n (gstring/unescapeEntities "&nbsp;"))))

(defn hangman [game]
  [:div {:style {:font-size "30pt"
                 :border-color "ccc"
                 :border-style :none
                 :border-bottom-style :double
                 :padding-bottom "15px"}}
   (doall
     (map-indexed (fn [i l]
                    (if (some (game :correct) l)
                      ^{:key i} [:span (apply str l (nbsp))]
                      ^{:key (+ i 100)} [:span (apply str "_" (nbsp))]))
                  (-> game :hangman)))])

(defn game-letters [game]
  [:div {:style {:font-size "20pt"
                 :padding-top "15px"
                 :padding-bottom "15px"}}
   [:span {:style {:font-size "12pt"}} "Click a letter to guess:"]
   [:br]
   (doall
     (map-indexed (fn [i l]
                    (if (some (game :guessed) l)
                      ^{:key i} [:span (apply str "_" (nbsp))]
                      ^{:key i} [:span
                                 (if (or (win? game) (lose? game))
                                   [:span l]
                                   [:a
                                    {:href "#"
                                     :onClick #(guess-letter l)}
                                    l])
                                 [:span (nbsp)]]))
                  (-> game :letters)))])

(defn guessed-letters [game]
  )

(defn game-view []
  (if @game
    (let [g @game]
      [:div.game
       [:h4 "Playing a word from "
        [:span {:style {:color "blue"}} (-> g :player :name)]]
       [hangman g]
       [game-letters g]
       [guessed-letters g]
       (cond
         (win? g) [:div.win
                   {:style {:color "green"
                            :font-size "20pt"
                            :font-weight "bold"}}
                   "You guessed it!"]
         (lose? g) [:div.lose
                    {:style {:font-size "20pt"
                             :font-weight "bold"}}
                    "You lost. The word was "
                    [:br]
                    [:span
                     {:style {:color "red"}}
                     (-> g :player :word)]])])
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
     [:div [:strong "They are "]
      [player-list]]]

    [:div.col-sm-9
     [game-view]]]
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
(defn guess-letter-msg [{:keys [letter]}]
  )

;;
;; :ping
;;
(defn ws-ping [_]
  )

;;; mount components and set up ws connection ;;;

(defn receive-ws-message
  [{:keys [type payload]}]
  (do
    (println "got ws message type:" type "payload:" payload)
    (case type
      :ping (ws-ping payload)
      :set-players (set-players! payload)
      :set-message (set-message! payload)
      :guess-letter (guess-letter-msg payload)
      )))

(defn mount-components []
  (reagent/render-component [#'home-page] (.getElementById js/document "app")))

(defn init! []
  (ws/make-websocket! (str "ws://" (.-host js/location) "/ws") receive-ws-message)
  (mount-components))
