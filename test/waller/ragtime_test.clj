(ns waller.ragtime-test
  (:require [clojure.test :refer :all]
            [waller.ragtime :refer :all]))

;; Presumes that there is a folder named test-resources and that
;; the folder is added as a resource-paths
(deftest createsDatabase
  (let [ac (arango-connection "arangos://testing:pwd@arangodb:8529/testing-db")]
    (is (= {:type :simple, :url "https://arangodb:8529",
            :uname "testing", :password "pwd"} 
           (:conn ac)))
    (is (= "testing-db" (:db ac)))))

(deftest loadsTheEdns
  (let [edns (load-resources "migrations")]
    (is (= 1 (count edns)))))