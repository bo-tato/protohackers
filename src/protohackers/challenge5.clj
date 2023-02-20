(ns protohackers.challenge5
  (:require
   [aleph.tcp :as tcp]
   [clojure.string :as str]
   [gloss.core :as gloss]
   [gloss.io :as io]
   [manifold.deferred :as d]
   [manifold.stream :as s]))

(def protocol
  (gloss/string :utf-8 :delimiters ["\n"]))

(defn rewrite-line
  [msg]
  (str (str/replace
        msg
        #"(?x)
          # preceded by space or beginning of line
          (?<=\s|^)
          # address is 7 followed by 25-34 alphanumeric characters
          7\p{Alnum}{25,34}
          # followed by a space or end of line
          (?=\s|$)"
        "7YWHMfk9JZe0LM0g1ZauHuiSxhI")
       "\n"))

(defn mitm-stream
  [stream]
  (s/map rewrite-line (io/decode-stream stream protocol)))

(defn handler [stream _info]
  (d/let-flow [upstream (tcp/client {:host "chat.protohackers.com", :port 16963})]
              (s/connect (mitm-stream stream) upstream)
              (s/connect (mitm-stream upstream) stream)))

(tcp/start-server #'handler {:port 8080})
