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

(defonce messages (atom []))
(defonce player (atom ""))
(defonce game (atom {:role nil  ; :guesser or :worder
                     :other nil ; "other player name"
                     }))
(defonce players (atom []))

(defn send-ws-message
  [type payload]
  (ws/send-transit-msg!
    {:type type
     :payload payload}))

(defn message-list []
  [:ul
   (for [[i message] (map-indexed vector @messages)]
     ^{:key i}
     [:li message])])

(defn message-input []
  (let [value (atom nil)]
    (fn []
      [:input.form-control
       {:type :text
        :placeholder "type in the name to want to use and press enter"
        :value @value
        :on-change #(reset! value (-> % .-target .-value))
        :on-key-down #(when (= (.-keyCode %) 13)
                       (send-ws-message :new-message
                                        {:message @value})
                       (reset! value nil))}])))

(defn home-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     [:h2 "Who are you?"]]]
   [:div.row
    [:div.col-sm-6
     [message-list]]]
   [:div.row
    [:div.col-sm-6
     [message-input]]]])


;;; ws messages ;;;

;;
;; :new-message
;;
(defn update-messages! [{:keys [message]}]
  (swap! messages #(vec (drop (- (count @messages) 10) (conj % message)))))

;;
;; :new-player
;;
(defn update-player-list! [{:keys [player]}]
  )

;;; mount components and set up ws connection ;;;

(defn receive-ws-message
  [{:keys [type payload]}]
  (do
    (println "got ws message type:" type "payload:" payload)
    (case type
      :new-message (update-messages! payload))))

(defn mount-components []
  (reagent/render-component [#'home-page] (.getElementById js/document "app")))

(defn init! []
  (ws/make-websocket! (str "ws://" (.-host js/location) "/ws") receive-ws-message)
  (mount-components))
