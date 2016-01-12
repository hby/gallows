(ns gallows.core
  (:require [reagent.core :as reagent :refer [atom]]
            [clojure.string :as s]
            [goog.string :as gstring]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [ajax.core :refer [GET POST]]
            [gallows.websockets :as ws]
            [gallows.game :as g]
            [gallows.view :as gv]
            )
  (:import goog.History))


(defn mount-components
  "Mounts all of the view components."
  []
  (reagent/render-component [#'gv/home-page] (.getElementById js/document "app")))

(defn init!
  "Will be called when page loads."
  []
  (ws/make-websocket! (str "ws://" (.-host js/location) "/ws")
                      ; game logic will handle ws messages from server
                      g/handle-message)
  (mount-components))
