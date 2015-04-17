(ns waller.core
  (:require [ragtime.core :refer [Migratable connection]]
            [travesedo.database :as tdb]
            [travesedo.collection :as tcol]
            [clojure.string :as cstr]))

(def ^:private base-conn {:type :simple})

(def ^:private migration-db "waller-migrator")

(defn create-db-payload 
  "Creates a map with key of :payload with a value of {:name, :users []} if the
   connection has credentials."
  [conn]
  (when-let [{:keys [username password]} conn] 
    {:users [{:username username, :password password}]}))

(defn create-db! [conn]
  (tdb/create {:conn conn, 
                :payload (merge {:name migration-db})}))

(defn ensure-track-store!
  [conn]
  (let [res (tdb/get-database-info {:conn conn, :db migration-db})]
    (if (< 199 (:code res) 300)
      res
      (create-db! conn))))

(defrecord ArangoDatabase
  [conn]
  Migratable
  (add-migration-id [this id]
    (println "Attempting to add id " id))
  (remove-migration-id [this id]
    (println "Attempting to remove id " id))
  (applied-migration-ids[this]
    (println "Attempting to get list")
    []))

(defn find-credentials 
  "Finds the credentials associated with the url, or an empty map if none"
  [uri]
    (when-let [creds (.getUserInfo uri)]
      (zipmap [:username :password] (cstr/split creds #":"))))

(defn find-url [uri]
  {:url (str (.getScheme uri) "://" (.getHost uri) 
          (if (> (.getPort uri) 80) 
            (str ":" (.getPort uri))
            ""))})

(defn create-context [url]
  (let [uri (java.net.URI. url)]
    (merge base-conn (find-url uri) (find-credentials uri))))

(defn arango-connection [url] 
  (map->ArangoDatabase {:conn (create-context url)}))

(defmethod connection "http" [url]
  (arango-connection url))
(defmethod connection "https" [url]
  (arango-connection url))