(ns protohackers.challenge3
  (:require
   [aleph.tcp :as tcp]
   [clj-commons.byte-streams :refer [to-line-seq]]
   [clojure.string :as str]
   [manifold.deferred :as d]
   [manifold.stream :as s]))

(defn valid-name?
  [name]
  (re-matches #"[a-zA-Z0-9]+" name))

(def users (agent {}))

(defn disconnect-client
  [stream]
  (s/put! stream "invalid name!\n")
  (s/close! stream))

(defn handle-client
  [name in out]
  (letfn [(send-all [msg]
            (doseq [[user stream] @users]
              (when-not (= name user)
                (s/put! stream msg))))

          (leave-room []
            (send users dissoc name)
            (send-all (str "* " name " has left the room\n")))

          (send-msg [msg]
            (send-all (format "[%s] %s\n" name msg)))]

    (s/on-drained in leave-room)
    (s/put! out
            (str "* The room contains: "
                 (str/join ", " (keys @users))
                 "\n"))
    (send-all (str "* " name " has entered the room\n"))
    (send users assoc name out)
    (s/consume
     send-msg
     in)))

(defn handler [stream _info]
  (s/put! stream "Welcome to budgetchat! What shall I call you?\n")
  (let [input (s/->source (to-line-seq stream))]
    (d/let-flow [name (s/take! input)]
                (if (valid-name? name)
                  (handle-client name input stream)
                  (disconnect-client stream)))))

(tcp/start-server #'handler {:port 8080})
