(ns ewen.replique.utils)

(defn string->is [s]
  (ByteArrayInputStream. (.getBytes s "UTF-8")))
