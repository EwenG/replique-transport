(defproject ewen.replique/replique-transport "0.1.0-SNAPSHOT"
  :description "HTTP and websocket nREPL transports, the aim of which is to handle clojurescript browser REPL connections."
  :url "https://github.com/EwenG/replique-transport"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/clojurescript "0.0-SNAPSHOT"]
                 [org.clojure/tools.nrepl "0.2.7"]]
  :aliases {"test-multi-transport" ["run" "-m" "ewen.replique.multi-transport/test-multi-transport"]}
  :jvm-opts ["-Xdebug" "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=57795" "-Dreplique.output.path=out"])