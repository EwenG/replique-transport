(ns test-http-transport
  (:require [clojure.test :as t :refer [deftest is run-tests]]
            [ewen.replique.http-transport :as http]
            [clojure.tools.nrepl.transport :as nrepl-t]
            [ewen.replique.utils :as utils])
  (:import (java.io ByteArrayOutputStream PipedInputStream PipedOutputStream)))



(let [payload "Payload"]
  (def request1 (format
"GET /gg/hh?ee=rr&ff HTTP/1.0
Host: localhost:57794
Keep-Alive: timeout=15
Connection: Keep-Alive
X-Forwarded-For: 192.168.10.1
Content-Length: %s

%s"
(count payload)
payload)))




(def nrepl-response-done-1 {:status #{:done}})
(def nrepl-response-ws-handshake {:status #{:ws-handshake}})
(def nrepl-response-static {:status #{:done} :body "<html></html>"})


(deftest test-recv
  (let [in (utils/string->is request1)
        out (ByteArrayOutputStream.)
        http-transport (http/http-transport in out nil)]
    (is (= (nrepl-t/recv http-transport)
           {:http-transport true
            :op "gg"
            :path ["gg" "hh"]
            :content "Payload"
            :headers {:content-length "7"
                      :x-forwarded-for "192.168.10.1"
                      :connection "Keep-Alive"
                      :keep-alive "timeout=15"
                      :host "localhost:57794"}
            :args {:ee "rr"}
            :method "GET"
            :protocol "HTTP/1.0"}))))








(deftest test-send-done-1
  (let [in (PipedInputStream.)
        out->in (PipedOutputStream. in)
        http-transport (http/http-transport nil out->in nil)]
    (.send http-transport nrepl-response-done-1)
    (.close out->in)
    (is (= (slurp in)
           "HTTP/1.1  200 OK\r\nContent-Type: text/html; charset=utf-8\r\nServer: ClojureScript REPL\r\n\r\n"))))





(deftest test-send-ws-handshake
  (let [in (PipedInputStream.)
        out->in (PipedOutputStream. in)
        http-transport (http/http-transport nil out->in nil)]
    (.send http-transport nrepl-response-ws-handshake)
    (.close out->in)
    (is (= (slurp in)
           "HTTP/1.1  101 Switching Protocols\r\nContent-Type: text/html; charset=utf-8\r\nServer: ClojureScript REPL\r\n\r\n"))))

(deftest test-send-static
  (let [in (PipedInputStream.)
        out->in (PipedOutputStream. in)
        http-transport (http/http-transport nil out->in nil)]
    (.send http-transport nrepl-response-static)
    (.close out->in)
    (is (= (slurp in)
           "HTTP/1.1  200 OK\r\nContent-Type: text/html; charset=utf-8\r\nServer: ClojureScript REPL\r\n\r\n<html></html>"))))











(comment
  (binding [*ns* 'test-http-transport]
    (run-tests))
  )



