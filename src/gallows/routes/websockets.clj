(ns gallows.routes.websockets
  (:require [compojure.core :refer [GET defroutes]]
            [org.httpkit.server
             :refer [send! with-channel on-close on-receive]]
            [cognitect.transit :as t]
            [clojure.tools.logging :as log]))

(defonce channels (atom #{}))

(defn connect! [channel]
  (log/info "channel open:" channel)
  (swap! channels conj channel))

(defn disconnect! [channel status]
  (log/info "channel closed:" status channel)
  (swap! channels #(remove #{channel} %)))

(defn notify-clients [msg]
  (doseq [channel @channels]
    (log/info "notify-clients:" msg)
    (send! channel msg)))

(defn ws-handler [request]
  (with-channel request channel
                (connect! channel)
                (on-close channel (partial disconnect! channel))
                (on-receive channel #(notify-clients %))))

(defroutes websocket-routes
           (GET "/ws" request (ws-handler request)))

