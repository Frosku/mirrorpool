(ns mirrorpool.core
  (:require [mirrorpool.derpi :as derpi]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(def cli-options
  [["-k" "--api-key KEY" "Api key to fetch with"
    :id :api-key]
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :update-fn inc]
   ["-d" "--database DATABASE" "Database location"
    :id :database
    :default "db"]
   ["-i" "--image-directory IMAGE_DIRECTORY" "Directory to download images to"
    :id :image-directory
    :default "img"]
   ["-r" "--restart" "Always start at page 1, even if the query has been run before"]
   ["-q" "--query QUERY" "Tags to download"
    :id :query
    :default (str/join " OR " ["grotesque"
                               "semi-grimdark"
                               "grimdark"
                               "safe"
                               "explicit"
                               "questionable"
                               "suggestive"])]
   ["-h" "--help"]])

(defn -main [& args]
  (let [args (parse-opts args cli-options)]
    (if (= true (:help (:options args)))
      (println (:summary args))
      (derpi/download-all! (:api-key (:options args))
                           (:query (:options args))
                           (:database (:options args))
                           (:image-directory (:options args))
                           (:restart (:options args))
                           (:verbosity (:options args))))))
