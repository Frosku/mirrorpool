(ns mirrorpool.derpi
  (:require [crux.api :as crux]
            [clj-http.core :as ch]
            [clj-http.client :as cc]
            [clj-http.conn-mgr :as cm]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:gen-class))

(def ^:const api-base "https://derpibooru.org/api/v1/json/search/images")

(defn get-crux-node
  [database]
  (crux/start-node {:crux.node/topology '[crux.standalone/topology
                                          crux.kv.lmdb/kv-store]
                    :crux.kv/db-dir (str (io/file database "db"))
                    :crux.standalone/event-log-kv-store 'crux.kv.lmdb/kv
                    :crux.standalone/event-log-dir (str (io/file database "evt"))
                    :crux.standalone/event-log-sync? true
                    :crux.kv/sync? true}))

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
  [query node images page verbosity]
  (show-info verbosity 1 "Inserting images into Crux.")
  (->> images
       (mapv #(assoc % :crux.db/id (keyword (str "derpi/id" (:id %)))))
       (mapv (fn [r] [:crux.tx/put r]))
       (crux/submit-tx node))
  (crux/submit-tx node [[:crux.tx/put
                         {:crux.db/id (keyword (format "derpi/last-page-%s" (hash query)))
                          :page page}]]))

(defn get-last-page
  [query node]
  (first (first (crux/q (crux/db node) {:find '[p]
                          :where '[[e :crux.db/id ?query-id]
                                   [e :page p]]
                          :args [{'?query-id (keyword (format "derpi/last-page-%s" (hash query)))}]}))))

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
      (if (or (empty? images) (>= (* (dec page) 50) (get-in result [:body :total])))
        (clean-up node verbosity)
        (do
          (show-info verbosity 1 (format "Downloaded page %s." page))
          (download-image-batch! image-directory images verbosity)
          (insert-to-database! query node images page verbosity)
          (recur api-key query node image-directory (inc page) verbosity))))))

(defn download-all!
  [api-key query database image-directory restart verbosity]
  (let [node (get-crux-node database)
        start-page (if (or restart (nil? (get-last-page query node))) 1 (get-last-page query node))]
    (download-page! api-key query node image-directory start-page verbosity)))
