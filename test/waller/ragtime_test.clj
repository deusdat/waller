(ns waller.ragtime-test
  (:require [clojure.test :refer :all]
            [travesedo.collection :as col]
            [travesedo.index :as tidx]
            [travesedo.database :as db]
            [waller.ragtime :refer :all]))

(def ctx {:conn {:type :simple,
                 :url "http://arangodb27:8529"},
          :db "waller",
          :collection "blog"})

(defn clear-out-database
  [f]
  (col/delete-collection ctx)
  (f))

(use-fixtures :once clear-out-database)

;; Presumes that there is a folder named test-resources and that
;; the folder is added as a resource-paths
(deftest creates-database
  (let [ac (arango-connection "arangos://testing:pwd@arangodb27:8529/testing-db")]
    (is (= {:type :simple, :url "https://arangodb27:8529",
            :uname "testing", :password "pwd"} 
           (:conn ac)))
    (is (= "testing-db" (:db ac)))))

(deftest loads-edns
  (println "Loading edns")
  (let [edns (load-resources "migrations")]
    (is (= 2 (count edns)))))

(deftest integration-test
  ;; Makes sure that the database is returned to normal.
  (let [c  (db/drop ctx)]
    (is (some #{(:code c)} '(200 404))))
  (migrate-from-classpath {:url "arango://arangodb27:8529/waller"})
  (let [c (col/get-all-collections (merge ctx {:exclude-system true}))
        blog (:blog (:names c))
        blog-indexes (tidx/read-all ctx "blog")
        indexes (:indexes blog-indexes)]
    (is (= 2 (count (:names c))))
    (is (= 2 (count indexes)))))