(ns waller.core-test
  (:require [clojure.test :refer :all]
            [waller.core :refer :all]))

(def arango-url "arango://testing:tree@arangodb:8596/testing-db")
(def arangos-url "arangos://testing:tree@arangodb:8596/testing-db")

(def arango-uri (java.net.URI. arango-url))
(def arangos-uri (java.net.URI. arangos-url))

(deftest shouldFindDatabase
  (is (= "testing-db" (find-db arango-uri) (find-db arangos-uri))))

(deftest shouldCreateURLPortionOfCtx
  (is (= {:url "http://arangodb:8596"} (find-url arango-uri)))
  (is (= {:url "https://arangodb:8596"} (find-url arangos-uri))))

(deftest shouldFindCredentials
  (is (= {:uname "testing", :password "tree"}
         (find-credentials arango-uri)
         (find-credentials arangos-uri))))