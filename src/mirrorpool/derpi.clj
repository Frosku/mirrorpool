(ns mirrorpool.derpi
  (:require [crux.api :as crux]
            [clj-http.core :as ch]
            [clj-http.client :as cc]
            [clj-http.conn-mgr :as cm]
            [clj-uuid :as uuid]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))

(def ^:const api-base "https://derpibooru.org/api/v1/json/search/images")

(defn get-crux-node
  [database]
  (crux/start-node {:crux.node/topology '[crux.standalone/topology
                                                  crux.kv.lmdb/kv-store]
                            :crux.kv/db-dir (str (io/file database))}))

(defn show-info
  [verbosity min-verbosity text]
  (if (>= verbosity min-verbosity)
    (println (format "INFO: %s" text))
    (do)))

(defn clean-up
  [node verbosity]
  (println "Finished processing images.")
  (show-info verbosity 3 (str (crux/q (crux/db node)
                                      '{:find [id]
                                        :where [[e :crux.db/id id]]
                                        :full-results? true})))
  (System/exit 0))

(defn get-image!
  [image image-directory verbosity]
  (show-info verbosity 2 (format "Downloading image %s." (:id image)))
  (show-info verbosity 3 (format "Tagged with: %s"
                                 (str/join ", " (:tags image))))
  (with-open
    [in (io/input-stream (:full (:representations image)))
     out (io/output-stream
          (format "%s/%s.%s" image-directory (:id image) (:format image)))]
    (io/copy in out)))

(defn download-image-batch!
  [image-directory images verbosity]
  (loop [image (first images) images (next images)]
    (if (nil? image)
      (show-info verbosity 1 "Finished downloading page.")
      (do (get-image! image image-directory verbosity)
          (recur (first images) (next images))))))

(defn insert-to-database!
  [node images page verbosity]
  (show-info verbosity 1 "Inserting images into Crux.")
  (->> images
       (mapv (fn [r] {:crux.db/id (keyword (str "derpi/id" (:id r)))
                     :description (:description r)
                     :tags (:tags r)
                     :source (:source_url r)
                     :format (:format r)
                     :mime-type (:mime_type r)}))
       (mapv (fn [r] [:crux.tx/put r]))
       (crux/submit-tx node))
  (crux/submit-tx node [[:crux.tx/put
                         {:crux.db/id :derpi/last-page :page page}]]))

(defn download-page!
  [api-key query node image-directory page verbosity]
  (let [result (cc/get api-base
                       {:accept :json
                        :as :json
                        :query-params {"key" api-key
                                       "q" query
                                       "sf" "id"
                                       "sd" "asc"
                                       "page" (str page)
                                       "per_page" "50"}})
        images (-> result (get-in [:body :images]))]
    (if (not= (:status result) 200)
      (download-page! api-key query node image-directory page verbosity)
      (if (empty? images)
        (clean-up node verbosity)
        (do
          (show-info verbosity 1 (format "Downloaded page %s." page))
          (download-image-batch! image-directory images verbosity)
          (insert-to-database! node images page verbosity)
          (recur api-key query node image-directory (inc page) verbosity))))))

(defn download-all!
  [api-key query database image-directory verbosity]
  (download-page! api-key
                  query
                  (get-crux-node database)
                  image-directory
                  1
                  verbosity))
