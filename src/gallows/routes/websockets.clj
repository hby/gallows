(ns gallows.routes.websockets
  (:require [clojure.tools.logging :as log]
            [compojure.core :refer [GET defroutes]]
            [org.httpkit.server :refer [with-channel on-close on-receive]]
            [gallows.routes.transit :refer :all]
            [gallows.game :as g]))


;;; Low level websocket connect, disconnect, and message handling

; Holds the future that wraps the periodic pinger
(defonce pinger (atom nil))


(defn- periodically
  "Repeatedly invokes f in another thread every ms milliseconds
   Uses a future, that is returned, for the thread behavior and
   allow for cancellation.

   f  - funciton to invoke
   ms - period in milliseconds"
  [ms f]
  (future (while true (do (Thread/sleep ms) (f)))))


(defn- setup-pinger
  "Periodically sends an empty message on all channels to keep
   them open on deployments that require traffic to stay alive.

   secs - seconds between pings"
  [secs]
  (swap! pinger #(if (nil? %)
                  (periodically (* 1000 secs)
                                (fn [] (g/send-to-all-channels (g/->message :ping {}))))
                  %)))


(defn- ws-connect
  "The ws connect handler. This will be called with a new
   channel when a client first connects.
   Tells game to add the player channel.

   channel - websocket channel"
  [channel]
  (log/info "channel connect:" channel)
  ; set up pinger on first connect, this is idempotent
  (setup-pinger 30)

  ; game logic
  (g/add-player channel))


(defn- ws-disconnect
  "The ws disconnect handler. This will be called when a
   channel disconnects.
   Tells game to remove player channel.

   channel   - websocket channel
   status    - status of the channel"
  [channel status]
  (log/info "channel disconnect:" channel "status:" status)

  ; game logic
  (g/remove-player channel))


(defn- ws-receive
  "The ws receive handler. This will be called when a
   client message comes in on the channel.
   Decodes to clojure data and forwards to game message handler.

   channel - websocket channel
   tmsg    - transit message received"
  [channel tmsg]
  (log/debug "channel recv:" channel)

  ; game logic
  (g/handle-message channel (transit-read tmsg)))


(defn- ws-handler
  "Creates a channel set up for messaging with
   lifecycle functions attached.

   request - the websocket request"
  [request]
  (with-channel request channel
                (ws-connect channel)
                (on-close channel (partial ws-disconnect channel))
                (on-receive channel (partial ws-receive channel))))


(defroutes websocket-routes
           (GET "/ws" request (ws-handler request)))
