(ns ewen.replique.http-writer)

(defn headers->string [headers]
  (->> (for [[header-k header-v] headers]
        (format "%s: %s\n" (name header-k) header-v))
      (apply str)))

(defn args->string [args]
  (loop [[[arg-k arg-v] & args-rest] (seq args)
         args-string ""
         separator ""
         first-char "?"]
    (if (nil? arg-k)
      args-string
      (recur args-rest
             (format "%s%s%s%s=%s" first-char args-string separator (name arg-k) arg-v)
             "&"
             ""))))

(defn request->string [{:keys [method path args protocol headers content]}]
  (apply str [method " " path (args->string args) " " protocol "\n" (headers->string headers) "\n" content]))

