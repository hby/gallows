(ns gallows.core
  (:require [gallows.handler :refer [app init destroy]]
            [org.httpkit.server :as http-kit]
            [gallows.db.migrations :as migrations]
            [clojure.tools.nrepl.server :as nrepl]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]])
  (:gen-class))

(defonce nrepl-server (atom nil))

(defn parse-port [port]
  (when port
    (cond
      (string? port) (Integer/parseInt port)
      (number? port) port
      :else          (throw (Exception. (str "invalid port value: " port))))))

(defn stop-nrepl []
  (when-let [server @nrepl-server]
    (nrepl/stop-server server)))

(defn start-nrepl
  "Start a network repl for debugging when the :nrepl-port is set in the environment."
  []
  (if @nrepl-server
    (log/error "nREPL is already running!")
    (when-let [port (env :nrepl-port)]
      (try
        (->> port
             (parse-port)
             (nrepl/start-server :port)
             (reset! nrepl-server))
        (log/info "nREPL server started on port" port)
        (catch Throwable t
          (log/error t "failed to start nREPL"))))))

(defn http-port [port]
  (parse-port (or port (env :port) 3000)))

(defonce http-server (atom nil))

(defn start-http-server [port]
  (init)
  (reset! http-server
          (http-kit/run-server
            app
            {:port port})))

(defn stop-http-server []
  (when @http-server
    (destroy)
    (@http-server :timeout 100)
    (reset! http-server nil)))

(defn stop-app []
  (stop-nrepl)
  (stop-http-server)
  (shutdown-agents))

(defn start-app [[port]]
  (let [port (http-port port)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
    (start-nrepl)
    (log/info "server is starting on port " port)
    (start-http-server port)))

(defn -main [& args]
  (cond
    (some #{"migrate" "rollback"} args)
    (do (migrations/migrate args) (System/exit 0))
    :else
    (start-app args)))
  
