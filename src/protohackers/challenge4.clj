(ns protohackers.challenge4
  (:require
   [aleph.udp :as udp]
   [clj-commons.byte-streams :as bs]
   [clojure.string :as str]
   [manifold.stream :as s]))

(defn handle-request
  [socket db {:keys [sender message]}]
  (let [message     (bs/to-string message)
        [key value] (str/split message #"=" 2)]
    (if value
      ;; insert
      (if-not (= key "version")
        (assoc db key value)
        db)
      ;; retreive
      (do
        (s/put! socket {:host    (.getHostString sender)
                        :port    (.getPort sender)
                        :message (str key "=" (get db key))})
        db))))

(let [socket @(udp/socket {:port 8080})]
  (s/reduce
   (partial handle-request socket)
   {"version" "Ken's Key-Value Store 1.0"}
   socket))
