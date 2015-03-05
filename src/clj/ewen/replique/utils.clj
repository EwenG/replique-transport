(ns ewen.replique.utils
  (:import (java.io ByteArrayInputStream)))

(defn string->is [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))
