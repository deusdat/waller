(ns waller.core
  (:require [ragtime.core :refer [Migratable connection]]
            [travesedo.database :as tdb]
            [travesedo.collection :as tcol]
            [travesedo.document :as tdoc]
            [clojure.string :as cstr]))

(def ^:private base-conn {:type :simple})

(def ^:private migration-db "waller-migrator")
(def ^:private migration-col "migrations")

(defn create-db-users 
  "Creates a map with key of :payload with a value of {:name, :users []} if the
   connection has credentials."
  [conn]
  (if-let [username (:username conn)]
    (if-let [password (:password conn)]
    {:users [{:username username, :password password}]})))

(defn create-db! [conn]
  (println (str "raw conn " conn))
  (let [req {:conn conn, 
                :payload (merge {:name migration-db} (create-db-users conn))}]
    (println req)
    (tdb/create req)))

(defn success? [res]
  (< 199 (:code res) 300))

(defn ensure-tracking-database! 
  [conn]
  (let [res (tdb/get-database-info {:conn conn, :db migration-db})]
    (if (success? res)
      :success
      (let [cret (create-db! conn)] 
        (assert (success? cret) (str "Couldn't create migration db "  cret))
        :success))))

(defn ensure-tracking-collection!
  [conn]
  (let [base-conn {:conn conn}
        res (tcol/get-collection-info (assoc base-conn 
                                        :db migration-db 
                                        :collection migration-col))]
    (println "Collection: " res)
    (if (success? res) 
      :success 
      (tcol/create (assoc base-conn 
                     :db migration-db 
                     :payload {:name migration-col})))))

(defn ensure-track-store!
  "Makes sure that the database and table for migrations is stored"
  [conn]
  (let [success (ensure-tracking-database! conn)]
    (assert (= success :success))
    (ensure-tracking-collection! conn)))

(defrecord ArangoDatabase
  [conn]
  Migratable
  (add-migration-id [this id]
    (ensure-track-store! conn)
    (tdoc/create {:conn conn, 
                  :in-collection migration-col, 
                  :db migration-db,
                  :payload {:_key id}}))
  
  (remove-migration-id [this id]
    (tdoc/delete (assoc this :db migration-db,
                        :_id (str migration-col "/" id))))
  
  (applied-migration-ids [this]
    (sort (:documents (tdoc/read-all-docs (assoc this :db migration-db
                                            :in-collection migration-col
                                            :type :key))))))

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