(ns protohackers.challenge2
  (:require
   [aleph.tcp :as tcp]
   [gloss.core :as gloss]
   [gloss.io :as io]
   [manifold.stream :as s]))

(def msg-type (gloss/enum :byte {:insert 0x49, :query 0x51}))

(gloss/defcodec insert (gloss/ordered-map :type :insert
                                          :timestamp :int32
                                          :price :int32))

(gloss/defcodec query (gloss/ordered-map :type :query
                                         :mintime :int32
                                         :maxtime :int32))

(def protocol
  (gloss/header
   msg-type
   {:insert insert, :query query}
   :type))

(gloss/defcodec response :int32)

(defn mean
  [coll]
  (if (empty? coll)
    0
    (/ (reduce + coll)
       (count coll))))

(defn avg-price
  [prices mintime maxtime]
  (->> prices
       (filter #(<= mintime (key %) maxtime))
       vals
       mean))

(defn handler [s _info]
  (letfn [(process-request [state req]
            (case (:type req)
              :insert (assoc state (:timestamp req) (:price req))
              :query (do
                       (s/put! s
                               (io/encode response
                                          (avg-price state (:mintime req) (:maxtime req))))
                       state)))]
    (s/reduce process-request
              {}
              (io/decode-stream s protocol))))

(tcp/start-server #'handler {:port 8080})
