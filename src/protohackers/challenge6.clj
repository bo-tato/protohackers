(ns protohackers.challenge6
  (:require
   [clojure.math :as math]
   [gloss.core :as gloss]
   [manifold.deferred :as d]
   [manifold.stream :as s]
   [manifold.time :as time]
   [protohackers.core :as protohackers]))

(def msg-str (gloss/finite-frame :ubyte
                                 (gloss/string :utf-8)))

(def msg-type (gloss/enum :ubyte
                          {:error         0x10
                           :ticket        0x21
                           :heartbeat     0x41
                           :plate         0x20
                           :WantHeartbeat 0x40
                           :IAmCamera     0x80
                           :IAmDispatcher 0x81}))

(gloss/defcodec plate (gloss/ordered-map
                       :type :plate
                       :plate msg-str
                       :timestamp :uint32))

(gloss/defcodec want-heartbeat (gloss/ordered-map
                                :type :WantHeartbeat
                                :interval :uint32))

(gloss/defcodec camera (gloss/ordered-map
                        :type :IAmCamera
                        :road :uint16
                        :mile :uint16
                        :limit :uint16))

(gloss/defcodec error (gloss/ordered-map
                       :type :error
                       :msg msg-str))

(gloss/defcodec ticket (gloss/ordered-map
                        :type :ticket
                        :plate msg-str
                        :road :uint16
                        :mile1 :uint16
                        :timestamp1 :uint32
                        :mile2 :uint16
                        :timestamp2 :uint32
                        :speed :uint16))

(gloss/defcodec dispatcher
  (gloss/ordered-map
   :type :IAmDispatcher
   :roads (gloss/repeated :uint16 :prefix :ubyte)))

(gloss/defcodec heartbeat {:type :heartbeat})

(def protocol
  (gloss/header msg-type {:error         error
                          :ticket        ticket
                          :plate         plate
                          :heartbeat     heartbeat
                          :WantHeartbeat want-heartbeat
                          :IAmCamera     camera
                          :IAmDispatcher dispatcher}
                :type))

;; global state
(def plates-seen (atom {}))
(def dispatchers (atom {}))
(def pending-tickets (atom {}))
(def ticketer (s/stream))

(defn disconnect-client
  [stream]
  (s/put! stream {:type :error :msg "illegal message"})
  (s/close! stream))

(defn handle-want-heartbeat
  [stream state interval]
  (when-let [heartbeat-cancel-fn (:heartbeat state)]
    (heartbeat-cancel-fn))
  (assoc state :heartbeat
         (time/every (* 100 interval)
                     #(s/put! stream {:type :heartbeat}))))

(defn set-client-info
  [stream state req]
  (if (:client-info state)
    ; disconnect with error if they've already declared as camera or dispatcher
    (disconnect-client stream)
    (do
      (when (= :IAmDispatcher (:type req))
        (doseq [road (:roads req)]
          (swap! dispatchers update road (fnil conj #{}) stream)
          (let [[pending _] (swap-vals! pending-tickets dissoc road)]
            (s/put-all! stream (get pending road)))))
      (assoc state :client-info req))))

(defn speed
  [[timestamp1 mile1] [timestamp2 mile2]]
  (* 3600 (/ (abs (- mile1 mile2))
             (double (abs (- timestamp2 timestamp1))))))

(defn timestamp->day
  [time]
  (math/floor (/ time 86400)))

(defn no-day-overlap?
  [[start1 end1] [start2 end2]]
  (let [[start1 end1 start2 end2] (map timestamp->day [start1 end1 start2 end2])]
    (or (< end1 start2)
        (> start1 end2))))

(defn send-ticket
  [sent-tickets [plate road reading1 reading2]]
  (let [[[timestamp1 mile1] [timestamp2 mile2]] (sort [reading1 reading2])
        timestamps [timestamp1 timestamp2]
        speed (math/round (* 100 (speed reading1 reading2)))
        ticket {:type :ticket
                :plate plate
                :road road
                :mile1 mile1
                :timestamp1 timestamp1
                :mile2 mile2
                :timestamp2 timestamp2
                :speed speed}]
    (if (every? (partial no-day-overlap? timestamps)
                (get sent-tickets plate))
      (do (if-let [dispatcher (first (get @dispatchers road))]
            (s/put! dispatcher ticket)
            (swap! pending-tickets update road conj ticket))
          (update sent-tickets plate conj timestamps))
      sent-tickets)))

(defn process-plate
  [stream {:keys [type road mile limit]} {:keys [plate timestamp] :as req}]
  (if-not (= :IAmCamera type)
    (disconnect-client stream)
    (let [reading [timestamp mile]
          readings (-> plates-seen
                       (swap! update-in [road plate] conj reading)
                       (get-in [road plate]))
          speeders  (filter #(> (speed reading %) limit) readings)]
      (doseq [speeder speeders]
        (s/put! ticketer [plate road reading speeder])))))

(defn process-request
  [stream state req]
  (case (:type req)
    :WantHeartbeat              (handle-want-heartbeat stream state (:interval req))
    (:IAmCamera :IAmDispatcher) (set-client-info stream state req)
    :plate                      (do (process-plate stream (:client-info state) req)
                                    state)
    (disconnect-client stream)))

(defn handler [stream]
  (d/let-flow [final-state (s/reduce (partial process-request stream)
                                     {}
                                     stream)]
              (when-not (s/closed? stream)
                (disconnect-client stream))
              (when (= :IAmDispatcher (get-in final-state [:client-info :type]))
                (doseq [road (get-in final-state [:client-info :roads])]
                  (swap! dispatchers update road disj stream)))))

(s/reduce send-ticket {} ticketer)
(protohackers/start-server #'handler protocol)
