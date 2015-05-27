(ns waller.core
  (:require [ragtime.core :refer [Migratable connection]]
            [travesedo.database :as tdb]
            [travesedo.collection :as tcol]
            [travesedo.document :as tdoc]
            [clojure.string :as cstr]))

(def ^:private base-conn {:type :simple})

(def ^:private migration-db "waller-migrator")
(def ^:private migration-col "migrations")
(def ^:private migration-preface (str "/_api/document/" migration-col "/"))

(defn create-db-users 
  "Creates a map with key of :payload with a value of {:name, :users []} if the
   connection has credentials."
  [conn]
  (if-let [username (:username conn)]
    (if-let [password (:password conn)]
    {:users [{:username username, :password password}]})))

(defn create-db! [conn]
  (let [req  (assoc conn 
                :payload (merge {:name (:db conn)} (create-db-users conn)))]
    (tdb/create req)))

(defn success? [res]
  (< 199 (:code res) 300))

(defn ensure-tracking-database! 
  [conn]
  (let [res (tdb/get-database-info conn)]
    (if (success? res)
      :success
      (let [cret (create-db! conn)] 
        (assert (success? cret) (str "Couldn't create migration db "  cret))
        :success))))

(defn ensure-tracking-collection!
  [conn]
  (let [base-conn {:conn conn}
        res (tcol/get-collection-info (assoc conn
                                        :collection migration-col))]
    (if (success? res) 
      :success 
      (tcol/create (assoc conn
                     :payload {:name migration-col})))))

(defn ensure-track-store!
  "Makes sure that the database and table for migrations is stored"
  [conn]
  (let [success (ensure-tracking-database! conn)]
    (assert (= success :success))
    (ensure-tracking-collection! conn)))

(defrecord ArangoDatabase
  [conn db]
  Migratable
  (add-migration-id [this id]
    (ensure-track-store! this)
    (tdoc/create (merge this {:in-collection migration-col, 
                              :payload {:_key id, :id id}})))
  
  (remove-migration-id [this id]
    (ensure-track-store! this)
    (tdoc/delete (assoc this :_id (str migration-col "/" id))))
  
  (applied-migration-ids [this]
    (ensure-track-store! this)
    (let [ids (:documents (tdoc/read-all-docs (assoc this
                                            :in-collection migration-col
                                            :type :key))),
          sorted-ids (map #(cstr/replace %  migration-preface "") (sort ids))]
      sorted-ids)))

(defn find-credentials 
  "Finds the credentials associated with the url, or an empty map if none"
  [uri]
    (when-let [creds (.getUserInfo uri)]
      (zipmap [:username :password] (cstr/split creds #":"))))

(defn find-url [uri]
  (let [src-port (.getPort uri)
        scheme (case (.getScheme uri) "arangos" "https" "arango" "http")
        port (if (> -1) (str ":" src-port) "")
        host (.getHost uri)]
    {:url (str scheme "://" host port)}))

(defn find-db [uri]
  (subs (.getPath uri) 1))

(defn create-context [url]
  (let [uri (java.net.URI. url)]
    (assoc {:db (find-db uri)} 
      :conn (merge base-conn (find-url uri) (find-credentials uri)))))

(defn arango-connection [url]
  (let [{:keys [conn db]} (create-context url)] 
    (->ArangoDatabase conn db)))

(defmethod connection "arango" [url]
  (arango-connection url))
(defmethod connection "arangos" [url]
  (arango-connection url))