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

(defn to-console
  [verbosity min-verbosity text]
  (if (>= verbosity min-verbosity)
    (println text)
    (do)))

(defn show-message
  [verbosity message]
  (to-console verbosity 1 message))

(defn show-info
  [verbosity message]
  (to-console verbosity 2 (format "INFO: %s" message)))

(defn show-debug
  [verbosity message]
  (to-console verbosity 3 (format "DEBUG: %s" message)))

(defn clean-up
  [node verbosity]
  (println "Finished processing images.")
  (show-debug verbosity (str (crux/q (crux/db node)
                                     '{:find [id]
                                       :where [[e :crux.db/id id]]
                                       :full-results? true})))
  (System/exit 0))

(defn get-image!
  [image image-directory verbosity]
  (show-debug verbosity (format "Downloading image %s with tags: %s."
                                (:id image)
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
      (show-info verbosity "Sucessfully downloaded images.")
      (do (get-image! image image-directory verbosity)
          (recur (first images) (next images))))))

(defn insert-to-database!
  [query node images page verbosity]
  (show-info verbosity "Inserting images into local data store.")
  (->> images
       (mapv #(assoc % :crux.db/id (keyword (str "derpi/id" (:id %)))))
       (mapv (fn [r] [:crux.tx/put r]))
       (crux/submit-tx node)
       (crux/await-tx node))
  (crux/await-tx node (crux/submit-tx node
                  [[:crux.tx/put
                    {:crux.db/id
                     (keyword (format "derpi/last-page-%s" (hash query)))
                     :page page}]])))

(defn get-last-page
  [query node]
  (first (first (crux/q (crux/db node)
                        {:find '[p]
                         :where '[[e :crux.db/id ?query-id]
                                  [e :page p]]
                         :args [{'?query-id
                                 (keyword (format "derpi/last-page-%s" (hash query)))}]}))))

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
          (show-message verbosity (format "Working on page %s." page))
          (download-image-batch! image-directory images verbosity)
          (insert-to-database! query node images page verbosity)
          (recur api-key query node image-directory (inc page) verbosity))))))

(defn download-all!
  [api-key query database image-directory restart verbosity]
  (let [node (get-crux-node database)
        start-page (if (or restart (nil? (get-last-page query node))) 1 (get-last-page query node))]
    (download-page! api-key query node image-directory start-page verbosity)))
