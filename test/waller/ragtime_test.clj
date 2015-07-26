(ns waller.ragtime-test
  (:require [clojure.test :refer :all]
            [travesedo.collection :as col]
            [travesedo.index :as tidx]
            [waller.ragtime :refer :all]))

(def ctx {:conn {:type :simple,
                 :url "http://arangodb26:8529"},
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
  (let [ac (arango-connection "arangos://testing:pwd@arangodb:8529/testing-db")]
    (is (= {:type :simple, :url "https://arangodb:8529",
            :uname "testing", :password "pwd"} 
           (:conn ac)))
    (is (= "testing-db" (:db ac)))))

(deftest loads-edns
  (let [edns (load-resources "migrations")]
    (is (= 2 (count edns)))))

(deftest integration-test
  ;; Makes sure that the database is returned to normal.
  (let [c  (col/get-all-collections (merge ctx {:exclude-system true}))]
    (is (= 1 (count (:names c))))
    (is (:waller (:names c))))
  (migrate-from-classpath {:url "arango://arangodb26:8529/waller"})
  (let [c (col/get-all-collections (merge ctx {:exclude-system true}))
        blog (:blog (:names c))
        blog-indexes (tidx/read-all ctx "blog")
        indexes (:indexes blog-indexes)]
    (is (= 2 (count (:names c))))
    (is (= 2 (count indexes)))))