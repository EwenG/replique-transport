(ns test-http-parser
  (:require [clojure.test :as t :refer [deftest is run-tests]]
            [ewen.replique.http-parser :as http-p]
            [clojure.java.io :as io]
            [ewen.replique.utils :as utils])
  (:import (java.io ByteArrayInputStream PushbackReader)))

 (defn string->is [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))

 (deftest string-transitions
  (is (= (http-p/string-transitions "test-string")
         {"" #{\t},
          "test-str" #{\i},
          "test-" #{\s},
          "tes" #{\t},
          "test-st" #{\r},
          "t" #{\e},
          "test-stri" #{\n},
          "test-s" #{\t},
          "test-strin" #{\g},
          "te" #{\s},
          "test" #{\-}})))

 (deftest strings-transitions
  (is (= (http-p/strings-transitions ["ab" "cd"])
         {"c" #{\d}, "" #{\a \c}, "a" #{\b}})))

 (deftest parse-strings
  (let [find-strings ["GET " "POST "]
        valid-transitions (http-p/strings-transitions find-strings)
        rdr (utils/string->is "GET OTHER_STUFF")
        ;Invalid char read while reading
        invalid-rdr1 (utils/string->is "GAT ")
        ;No string found. End of stream reached.
        invalid-rdr2 (utils/string->is "GE")
        ]
   (is (= (http-p/parse-string {:current-string ""} rdr valid-transitions find-strings)
          {:state :success, :current-string "GET "}))
   (is (= (http-p/parse-string {:current-string ""} invalid-rdr1 valid-transitions find-strings)
          {:state :error, :current-string "G", :error-char \A}))
   (is (= (http-p/parse-string {:current-string ""} invalid-rdr2 valid-transitions find-strings)
          {:state :error, :current-string "GE", :error-char nil}))))

(deftest parse-path
  (let [path-string "/gg/hh "
        rdr (-> (utils/string->is path-string) io/reader (PushbackReader.))]
    (is (= (http-p/transition-fn :parse-path rdr nil)
           {:terminal-char \space, :state :success, :current-string "/gg/hh"}))))



