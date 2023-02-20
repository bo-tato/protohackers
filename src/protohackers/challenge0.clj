(ns protohackers.challenge0
  (:require
   [aleph.tcp :as tcp]
   [manifold.stream :as s]))

(defn echo-handler [s _info]
  (s/connect s s))

(tcp/start-server #'echo-handler {:port 8080})
