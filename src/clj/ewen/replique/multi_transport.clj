(ns ewen.replique.multi-transport
  (:require [ewen.replique.http-transport :as http-t]
            [clojure.java.io :as io]
            [clojure.tools.nrepl.transport :as t]
            [clojure.tools.nrepl.server :refer [start-server default-handler]]
            [ewen.replique.http-parser :as http-p]
            [ewen.replique.http-writer :as http-w]
            [clojure.tools.reader.edn :as edn]
            [ewen.replique.utils :as utils])
  (:import (java.io PushbackInputStream SequenceInputStream)
           (java.net Socket)))



(defrecord MultiTransport [wrapped-transport]
  t/Transport
  (send [this msg] (t/send wrapped-transport msg))
  (recv [this] (t/recv wrapped-transport))
  (recv [this timeout] (t/recv wrapped-transport timeout))
  java.io.Closeable
  (close [this] (.close wrapped-transport)))

(defn multi-transport*
  "Create an HTTP transport or a bencode transport depending on the value of the
  first few bytes read from the socket."
  [in out s]
  (let [http-method-bytes (byte-array 8)
        in (PushbackInputStream. in 8)
        _ (.read in http-method-bytes)
        _ (.unread in http-method-bytes)
        http-method-match (re-matches #"^(GET |POST |PUT |DELETE |OPTIONS |HEAD ).*"
                                      (String. http-method-bytes "UTF-8"))
        transport (cond (not http-method-match)
                        (t/bencode in out s)
                        (not= "GET " (second http-method-match))
                        (http-t/http-transport in out s)
                        :else
                        (let [{{:keys [connection upgrade sec-websocket-key]} :headers :as request}
                              (-> (http-p/parse-request in) :request)]
                          (if (and (= "Upgrade" connection)
                                   (= "websocket" upgrade))
                            (let [ws-not-implemented-yet
                                  (-> (http-t/nrepl-msg->http-response {:status #{:error :done}})
                                      http-t/http-response->bytes)]
                              (locking out
                                (.write out ws-not-implemented-yet)
                                (.close out)))
                            (http-t/http-transport (-> (http-w/request->string request)
                                                       utils/string->is
                                                       (SequenceInputStream. in))
                                                   out s))))]
    (-> (MultiTransport. transport)
        (assoc :socket s))))

(defn multi-transport
  "Create an HTTP transport or a bencode transport depending on the value of the
  first few bytes read from the socket."
  [^Socket s]
  (let [hostAddress  (.. s getLocalAddress getHostAddress)
        port (.. s getLocalPort)]
    (multi-transport* (io/input-stream s)
                      (io/output-stream s)
                      s)))

(defn test-multi-transport []
  (start-server :bind "127.0.0.1"
                :port 57794
                :transport-fn multi-transport
                :handler (default-handler)))


