(ns protohackers.challenge1
  (:require
   [aleph.tcp :as tcp]
   [clojure.core.match :refer [match]]
   [clojure.data.json :as json]
   [clojure.math :as math]
   [clj-commons.byte-streams :refer [to-line-seq]]
   [manifold.stream :as s]))

(defn prime? [n]
  (cond
    (float? n) false
    (<= n 1) false
    (< n 4) true
    :else (every? #(not= 0 (rem  n %))
                  (range 2 (inc (math/sqrt n))))))

(defn close-malformed [s]
  (s/put! s "malformed\n")
  (s/close! s))

(defn ->json [s]
  (str (json/write-str s) "\n"))

(defn handler [s _info]
  (letfn [(json->map [data] (try (json/read-str data)
                                 (catch Exception _ (close-malformed s))))
          (handle-req [req] (match (json->map req)
                              {"method" "isPrime"
                               "number" (n :guard number?)}
                              (->json {:method "isPrime"
                                       :prime (prime? n)})
                              :else (close-malformed s)))]
    (s/connect (s/map handle-req (to-line-seq s)) s)))

(tcp/start-server #'handler {:port 8080})
