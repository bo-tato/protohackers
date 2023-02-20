(ns protohackers.core
  (:require
   [aleph.tcp :as tcp]
   [manifold.stream :as s]
   [gloss.io :as io]))

;; `wrap-duplex-stream` from https://github.com/clj-commons/aleph/blob/master/examples/src/aleph/examples/tcp.clj
;;
;; This function takes a raw TCP **duplex stream** which represents bidirectional communication
;; via a single stream.  Messages from the remote endpoint can be consumed via `take!`, and
;; messages can be sent to the remote endpoint via `put!`.  It returns a duplex stream which
;; will take and emit arbitrary Clojure data, via the protocol we've just defined.
;;
;; First, we define a connection between `out` and the raw stream, which will take all the
;; messages from `out` and encode them before passing them onto the raw stream.
;;
;; Then, we `splice` together a separate sink and source, so that they can be presented as a
;; single duplex stream.  We've already defined our sink, which will encode all outgoing
;; messages.  We must combine that with a decoded view of the incoming stream, which is
;; accomplished via `gloss.io/decode-stream`.
(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
     (s/map #(io/encode protocol %) out)
     s)

    (s/splice
     out
     (io/decode-stream s protocol))))

(defn start-server
  [handler protocol]
  (tcp/start-server (fn [s _info]
                      (handler (wrap-duplex-stream protocol s)))
                    {:port 8080}))
