(defproject com.frosku/mirrorpool "0.1.0-SNAPSHOT"
  :author "Frosku <frosku@frosku.com>"
  :description "Tool to clone Derpibooru image data."
  :url "http://github.com/Frosku/derpidownload"
  :license {:name "The Unlicense"
            :url "https://unlicense.org"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
                 [throttler "1.0.0"]
                 [clj-http "3.10.1"]
                 [danlentz/clj-uuid "0.1.9"]
                 [cheshire "5.10.0"]
                 [juxt/crux-core "20.06-1.9.1-beta"]
                 [juxt/crux-lmdb "20.06-1.9.1-alpha"]]
  :main mirrorpool.core
  :aot :all
  :repl-options {:init-ns mirrorpool.core})
