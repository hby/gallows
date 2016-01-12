(ns gallows.view
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as s]
            [goog.string :as gstring]
            [gallows.state :as gs]
            [gallows.game :as g]
            )
  (:import goog.History))

;;; Views

(defn player-li
  "Renders a single player name. If the player has a word
   the name will be selectable and will start a game when
   selected.
   Sub-views:

   player - immutable player from the players game state."
  [[i player]]
  (if (player :word)
    [:a
     {:href "#"
      :onClick #(g/start-game player)}
     (player :name)]
    (player :name)))

(defn player-list
  "Renders a list of the players names.
   Sub-views: player-li"
  []
  [:ul {:style {:list-style-type "none"
                :padding-left "3"
                :margin "0"}}
   (for [[i _ :as ip] (map-indexed vector @gs/players)]
     ^{:key i}
     [:li (player-li ip)])])

(defn message-view
  "Renders the message game state"
  []
  [:span
   @gs/message])

(defn player-name-field
  "Renders the player name input field if there is no
   player name in the game state. Otherwise renders nothing.
   The players name is set in the game state when either tab
   or return is pushed in the input field."
  []
  (let [value (atom nil)
        done-fn #(do
                  (g/update-name @value)
                  (reset! value nil))]
    (fn []
      [:input.form-control
       {:type :text
        :placeholder "who are you?"
        :value @value
        :on-change #(let [tv (-> % .-target .-value)]
                     (if (< (count tv) 11)
                       (reset! value tv)))
        :on-key-down #(when (or (= (.-keyCode %) 13)
                                (= (.-keyCode %) 9))
                       (done-fn))}])))

(defn player-name
  "Renders the player name as uneditable if there is
   a player name in the game state. Otherwise renders nothing."
  []
  (if (empty? @gs/player)
    [player-name-field]
    [:div
     [:span "Hello, "]
     [:span @gs/player]]))

(defn word-field
  "Renders the word input field.
   The players name is set in the game state when
   the return key is pushed."
  []
  (let [value (atom nil)]
    (fn []
      [:input.form-control
       {:type        :text
        :placeholder "what's your word?"
        :value       @value
        :on-change   #(let [tv (-> % .-target .-value)]
                       (if (< (count tv) 16)
                         (reset! value (s/lower-case tv))))
        :on-key-down #(when (and (= (.-keyCode %) 13)
                                 (not (empty? @value)))
                       (g/add-word (s/lower-case @value))
                       (reset! value nil))}])))

(defn current-word
  "Renders the current word in the game state."
  []
  [:div [:em "current word: "]
   [:span @gs/word]])

(defn nbsp
  "Helper to render as many nbsp's given in the argument (defaults to 1)."
  ([] (nbsp 1))
  ([n] (repeat n (gstring/unescapeEntities "&nbsp;"))))

(defn hangman
  "Renders the word being guessed. Letters are shown as an _
   until they are guessed and the the letter shows.
   Sub-views:

   game - immutable game state"
  [game]
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

(defn game-letters
  "Renders a line of letter to guess. Letters are shown if
   they have not yet been guessed. An _ shows for a guessed letter.
   Sub-views:

   game - immutable game state"
  [game]
  [:div {:style {:font-size "20pt"
                 :padding-top "15px"
                 :padding-bottom "15px"}}
   [:span {:style {:font-size "12pt"
                   :color "grey"}}
    "Click a letter to guess:"]
   [:br]
   (doall
     (map-indexed (fn [i l]
                    (if (some (game :guessed) l)
                      ^{:key i} [:span (apply str "_" (nbsp))]
                      ^{:key i} [:span
                                 (if (or (g/win? game) (g/lose? game))
                                   [:span l]
                                   [:a
                                    {:href "#"
                                     :onClick #(g/guess-letter l)}
                                    l])
                                 [:span (nbsp)]]))
                  (-> game :letters)))])

(defn guessed-letters
  "Empty"
  [game]
  )

(defn game-view
  "Renders a game when there is one.
   Sub-views: hangman, game-letters, guessed-letters"
  []
  (if @gs/game
    (let [g @gs/game]
      [:div.game
       [:h4 {:style {:color "grey"}} "Playing a word from "
        [:span {:style {:color "black"}} (-> g :player :name)]]
       [hangman g]
       [game-letters g]
       [guessed-letters g]
       (cond
         (g/win? g) [:div.win
                     {:style {:color "green"
                              :font-size "20pt"
                              :font-weight "bold"}}
                     "You guessed it!"]
         (g/lose? g) [:div.lose
                      {:style {:font-size "20pt"
                               :font-weight "bold"}}
                      "You lost. The word was "
                      [:br]
                      [:span
                       {:style {:color "red"}}
                       (-> g :player :word)]])])
    [:span "Select a player name to play their word"]))

(defn reports-list
  "Renders the list of game reports used by the reports-view.
   Sub-views:

   reports - vector of report messages (strings)"
  [reports]
  [:ul {:style {:list-style-type "none"
                :padding-left "3"
                :margin "0"}}
   (for [[i r] (map-indexed vector reports)]
     ^{:key i} [:li r])])

(defn reports-view
  "Renders a scrollable, running list of win/lose reports for
   players that have played your words.
   Sub-views: reports-list"
  []
  (if (not (empty? @gs/reports))
    [:div.results {:style {:height "250px"
                           :overflow-y "scroll"
                           :border-style "solid"
                           :border-width "1px"
                           :border-color "ddd"}}
     [reports-list @gs/reports]]))

(defn home-page
  "The main view for the application. This hold all other
   views as subviews.
   Sub-views: message-view, player-name, word-field, current-word,
              player-list, game-view, reports-view"
  []
  [:div.container
   [:div.row

    [:div.col-sm-3
     [message-view]
     [:h4 "Enter The Gallows"]
     [:div
      [player-name]
      [:br]
      [word-field]
      [current-word]]
     [:br]
     [:div
      [:span {:style {:font-weight "bold"}} "Other players"]
      [:div {:style {:height "300px"
                     :overflow-y "scroll"
                     :border-style "solid"
                     :border-width "1px"
                     :border-color "ddd"}}
       [player-list]]]]

    [:div.col-sm-9
     [game-view]
     [reports-view]]]
   ]
  )
