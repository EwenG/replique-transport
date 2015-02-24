(ns ewen.replique.http-parser
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [ewen.replique.utils :as utils])
  (:import (java.io ByteArrayInputStream PushbackReader)))





;String parsing

(defn string-transitions
  "Given a string, return a map: {prefix1 #{\next-char1} prefix2 #{\next-char2} ... } such as the keys of the map
  are all the possible string prefix and the associated value is a set that contains the char following the
  prefix in the string."
  [s]
  (->> (reduce (fn [collect first-char]
                 (let [[prefix char-suffix] (last collect)]
                   (conj collect [(str prefix (first char-suffix)) #{first-char}])))
               [] s)
       (into {})))

(defn strings-transitions
  "Apply `string-transitions` function on all the strings in the given string collection.
  Then merge the resulting maps. Duplicated keys are merged by concatenating there values."
  [strings]
  (->> (map string-transitions strings)
       identity
       (apply merge-with into)))

(defn parse-string
  "Read a string from `rdr`. The read string is concatenated to `current-string`.
  valid-transitions is a map from string prefixs to a set of their next possible char.
  find-strings is a collection of possible strings to be read from rdr.
  If a invalid char is read (ie if it is not found in valid-transitions) then the
  error {:state :error :error-char err-c} is return with err-c the last
  read char from rdr. Otherwise returns {:state :success :current-string read-s}
  with read-s the fully read string."
  [{:keys [current-string] :as result}
                     rdr valid-transitions
                     find-strings]
  (let [first-char (.read rdr)
        first-char (if (= -1 first-char)
                     nil
                     (char first-char))
        next-string (str current-string first-char)]
    (cond
      (some #{next-string} find-strings)
      (assoc result :state :success
                    :current-string next-string)
      (not (some #{first-char} (get valid-transitions current-string)))
      (assoc result :state :error :error-char first-char)
      :else (recur (assoc result
                     :current-string
                     next-string)
                   rdr valid-transitions
                   find-strings))))





;Parse a string until a terminal char is read.

;//TODO Temporary hack. We need to properly handle \r\n
(defn remove-backslash-r [result]
  (update-in result [:current-string] #(.replaceAll % "\r" "")))

(defn parse-string-until [{:keys [current-string] :as result}
                          rdr terminal-chars invalid-chars]
  (let [first-char (.read rdr)
        first-char (if (= -1 first-char)
                     nil
                     (char first-char))]
    (cond
      (or (not first-char) (some #{first-char} invalid-chars))
      (assoc result :state :error :error-char first-char)
      (some #{first-char} terminal-chars)
      (assoc (remove-backslash-r result) :state :success :terminal-char first-char)
      :else (recur (assoc result
                     :current-string
                     (str current-string first-char))
                   rdr terminal-chars invalid-chars))))






;Transitions fns

(defmulti transition-fn (fn [state rdr request] state))

(defmethod transition-fn :init [_ _ _]
  {:state :success})

(let [valid-methods #{"GET " "POST "}
      valid-transitions (strings-transitions valid-methods)]
  (defmethod transition-fn :parse-method [_ rdr _]
    (parse-string {:current-string ""} rdr
                  valid-transitions
                  valid-methods)))

(defmethod transition-fn :parse-path [_ rdr _]
  (let [{:keys [terminal-char] :as result}
        (parse-string-until {:current-string ""}
                            rdr #{\? \space} #{\newline})]
    ;There is no args in the URL but parse-args will be called anyway,
    ;so we unread the space char.
    (when (= \space terminal-char)
      (.unread rdr (int \space)))
    result))

(defn string->key [s]
  (keyword (str/lower-case s)))

(defn parse-arg-key [rdr]
  (parse-string-until {:current-string ""}
                      rdr #{\= \space} #{\newline \&}))

(defn parse-arg-val [rdr]
  (parse-string-until {:current-string ""}
                      rdr #{\& \space} #{\newline \=}))

(defmethod transition-fn :parse-args [_ rdr _]
  (loop [rdr rdr
         result {:args {} :state :success}]
    (let [{:keys [state] :as key-res} (parse-arg-key rdr)
          k (-> key-res :current-string string->key)]
      (cond
        (= :error state)
        (assoc result :state :error
                      :sub-state key-res
                      :step :parse-key)
        (= \space (:terminal-char key-res))
        result
        :else (let [{:keys [state terminal-char] :as val-res} (parse-arg-val rdr)
                    v (-> val-res :current-string)]
                (if (= :error state)
                  (assoc result :state :error
                                :sub-state val-res
                                :step :parse-val)
                  (do (when (= \space terminal-char)
                        (.unread rdr (int \space)))
                      (recur rdr (update-in result [:args] assoc k v)))))))))

(defmethod transition-fn :parse-protocol[_ rdr _]
  (parse-string-until {:current-string ""}
                      rdr #{\newline} #{\space}))

(defn parse-header-key [rdr]
  (parse-string-until {:current-string ""}
                      rdr #{\: \newline} #{\space}))

(defn parse-header-val [rdr]
  (parse-string-until {:current-string ""}
                      rdr #{\newline} #{}))

(defn skip-spaces! [rdr]
  (loop [rdr rdr]
    (let [c-int (.read rdr)
          c (if (= -1 c-int) nil (char c-int))]
      (if-not (= \space c)
        (when c (.unread rdr c-int))
        (recur rdr)))))

(defmethod transition-fn :parse-headers [_ rdr _]
  (loop [rdr rdr
         result {:headers {} :state :success}]
    (let [{:keys [state] :as key-res} (parse-header-key rdr)
          k (-> key-res :current-string string->key)]
      (cond
        (and (= :error state)
             (= "" (:current-string key-res))
             (= \newline (:terminal-char key-res)))
        result
        (= :error state)
        (assoc result :state :error
                      :sub-state key-res
                      :step :parse-key)
        (= \newline (:terminal-char key-res))
        result
        :else (let [_ (skip-spaces! rdr)
                    {:keys [state] :as val-res} (parse-header-val rdr)
                    v (-> val-res :current-string)]
                (if (= :error state)
                  (assoc result :state :error
                                :sub-state val-res
                                :step :parse-val)
                  (recur rdr (update-in result [:headers] assoc k v))))))))

(defmethod transition-fn :parse-content [_ rdr {{:keys [content-length]} :headers}]
  (let [content (char-array (Integer/parseInt (or content-length "0")))]
    (.read rdr content)
    {:state :success :content (String. content)}))

(defmethod transition-fn :done [_ _ _]
  {:state :success})






;Swap request fns

(defmulti update-request-fn (fn [state request result] state))

(defmethod update-request-fn :init [_ request _]
  request)

(defmethod update-request-fn :parse-method [_ request {:keys [current-string]}]
  (assoc request :method (str/trim current-string)))

(defmethod update-request-fn :parse-path [_ request {:keys [current-string]}]
  (assoc request :path (str/trim current-string)))

(defmethod update-request-fn :parse-args [_ request {:keys [args]}]
  (assoc request :args args))

(defmethod update-request-fn :parse-protocol [_ request {:keys [current-string]}]
  (assoc request :protocol (str/trim current-string)))

(defmethod update-request-fn :parse-headers [_ request {:keys [headers]}]
  (assoc request :headers headers))

(defmethod update-request-fn :parse-content [_ request {:keys [content]}]
  (assoc request :content content))

(defmethod update-request-fn :done [_ request _]
  request)





;State transitions

(def state-transitions
  {:init :parse-method
   :parse-method :parse-path
   :parse-path :parse-args
   :parse-args :parse-protocol
   :parse-protocol :parse-headers
   :parse-headers :parse-content
   :parse-content :done})

(defn parse-request [in]
  (loop [rdr (PushbackReader. (io/reader in))
         main-state {:state :init :request {}}
         sub-state {:state :success}
         rdr rdr]
    (let [next-state (get state-transitions (:state main-state))]
      (cond
        (= :error (:state sub-state))
        (assoc main-state :state :error
                          :sub-state sub-state
                          :parse-step (:state main-state))
        (= :done (:state main-state)) main-state
        :else (let [updated-request (update-request-fn (:state main-state)
                                                       (:request main-state)
                                                       sub-state)]
                (recur rdr
                       (assoc main-state :state next-state
                                         :request updated-request)
                       (transition-fn next-state rdr updated-request)
                       rdr))))))

(comment

  ;;Utils


  (let [payload "Payload"
        request (format "GET /gg/hh?ee=rr&ff HTTP/1.0
Host: localhost:57794
Keep-Alive: timeout=15
Connection: Keep-Alive
X-Forwarded-For: 192.168.10.1
Content-Length: %s

%s" (count payload) payload)
        in (utils/string->is request)
        in-args (utils/string->is "abc=def&rr=tt
        ")]
    #_(char (.read rdr))
    #_(parse-method rdr)
    #_(parse-string-until {:current-string ""} rdr #{\space} #{})
    #_(transition-fn :parse-args rdr-args {})
    (parse-request in)
    )




  )