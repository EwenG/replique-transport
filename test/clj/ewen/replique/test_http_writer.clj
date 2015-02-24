(ns test-http-writer
  (:require [clojure.test :as t :refer [deftest is run-tests]]
            [ewen.replique.http-writer :as http-w]))

(deftest test-headers->string
  (let [headers {:content-length  "7",
                 :x-forwarded-for "192.168.10.1",
                 :connection      "Keep-Alive",
                 :keep-alive      "timeout=15",
                 :host            "localhost:57794"}]
    (is (= (http-w/headers->string headers)
           "keep-alive: timeout=15\nhost: localhost:57794\ncontent-length: 7\nconnection: Keep-Alive\nx-forwarded-for: 192.168.10.1\n"))))

(deftest test-args->string
  (let [args1 {:arg1k "arg1v" :arg2k "arg2v"}
        args2 {}
        args3 nil]
    (is (= (http-w/args->string args1)
           "?arg1k=arg1v&arg2k=arg2v"))
    (is (= (http-w/args->string args2)
           ""))
    (is (= (http-w/args->string args3)
           ""))))

(deftest request->string
  (let [request {:content "Payload",
                 :headers {:content-length  "7",
                           :x-forwarded-for "192.168.10.1",
                           :connection      "Keep-Alive",
                           :keep-alive      "timeout=15",
                           :host            "localhost:57794"},
                 :args    {:ee "rr"},
                 :path    "/gg/hh",
                 :method  "GET"
                 :protocol "HTTP/1.0"}]
    (is (= (http-w/request->string request)
           "GET /gg/hh?ee=rr HTTP/1.0\nkeep-alive: timeout=15\nhost: localhost:57794\ncontent-length: 7\nconnection: Keep-Alive\nx-forwarded-for: 192.168.10.1\n\nPayload"))))




