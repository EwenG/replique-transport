(ns ewen.replique.http-transport
  (:import (java.io ByteArrayInputStream PipedInputStream PipedOutputStream ByteArrayOutputStream))
  (:require [clojure.tools.nrepl.transport :as t]
            [clojure.set :refer [subset?]]
            [ewen.replique.http-parser :as http]
            [clojure.string :as str]
            [ewen.replique.utils :as utils]))




;; Response processing

(defn nrepl-status->http-status [status]
  (->> (cond (subset? #{:ws-handshake} status) "101 Switching Protocols"
             (subset? #{:done :unknow-op :error} status) "400 Bad Request"
             (subset? #{:done :not-found :error} status) "404 Not Found"
             (subset? #{:done :error} status) "500 Internal Server Error"
             (subset? #{:done} status) "200 OK"
             :else "500 Internal Server Error")
       (format "HTTP/1.1  %s\r\n")))

(defn nrepl-msg->http-response
  [{:keys [status headers body] :as msg}]
  (let [status (nrepl-status->http-status status)
        headers (merge headers
                       {:Server       "ClojureScript REPL"
                        :Content-Type "text/html; charset=utf-8"})
        body (binding [*print-readably* true]
               (str body))]
    {:status status :headers headers :body body}))

(defn http-response->bytes [{:keys [status headers body] :as http-response}]
  ;out is never closed since closing a ByteArrayOutputStream has no effect
  (let [out (ByteArrayOutputStream.)]
    (.write out (.getBytes status "UTF-8"))
    (doseq [[k v] headers]
      (->> (.getBytes (format "%s: %s\r\n" (name k) v) "UTF-8")
          (.write out)))
    (.write out (.getBytes "\r\n" "UTF-8"))
    (when body
      (.write out (-> body str (.getBytes "UTF-8"))))
    (.toByteArray out)))












;; Request processing

(defn parse-path [path]
  {:pre [(string? path)
         (.startsWith path "/")]}
  (let [op (str/split path #"/")]
    (filter (complement str/blank?) op)))

(defn http-request->nrepl-msg [{{:keys [path method] :as http-request} :request :as http-request-full}]
  (let [path (parse-path path)]
    (-> http-request
        (assoc :http-transport true)
        (dissoc :path)
        (assoc :op (if (= "POST" method)
                     (-> http-request :args :type name)
                     (first path)))
        (assoc :path path))))












(defn http-transport [in out s]
  (reify t/Transport
    (send [this http-response]
      (let [http-response-bytes (-> http-response
                                    nrepl-msg->http-response
                                    http-response->bytes)]
        (locking out
          (.write out http-response-bytes)
          (.close out))))
    (recv [this] (-> @(future (http/parse-request in))
                     http-request->nrepl-msg))
    (recv [this timeout] (if-let [http-request (-> (future (http/parse-request in))
                                                   (deref timeout nil))]
                           (http-request->nrepl-msg http-request)))
    java.io.Closeable
    (close [this]
      (.close in)
      (.close out)
      (when s (.close s)))))














(let [payload "Payload"
      request (format "GET /gg/hh?ee=rr&ff
Host: localhost:57794
Keep-Alive: timeout=15
Connection: Keep-Alive
X-Forwarded-For: 192.168.10.1
Content-Length: %s

%s" (count payload) payload)
      nrepl-response {:status #{:done}}
      in1 (utils/string->is request)
      in2 (PipedInputStream.)
      ->in2 (PipedOutputStream. in2)
      out (ByteArrayOutputStream.)
      transp1 (http-transport in1 out nil)
      transp2 (http-transport in2 out nil)]
  #_(t/recv transp1)
  #_(t/recv transp2 1000)
  (-> nrepl-response
      nrepl-msg->http-response
      http-response->bytes
      (String. "UTF-8")))



(comment

  (require '[clojure.tools.nrepl.server :refer [start-server stop-server default-handler]])
  (require '[clojure.tools.nrepl :as repl])

  (with-open [conn (repl/connect :port 57794)]
    (-> (repl/client conn 400000)                         ; message receive timeout required
        (repl/message {:op   "eval"
                       :code "(+ 2 3)"
                       #_:session #_session})
        doall))

  (with-open [conn (repl/connect :port 57794)]
    (-> (repl/client conn 400000)                         ; message receive timeout required
        (repl/message {:op "ls-sessions"
                       #_:session #_session})
        doall))




  )