(ns gallows.test.db.core
  (:require [gallows.db.core :as db]
            [gallows.db.migrations :as migrations]
            [clojure.test :refer :all]
            [clojure.java.jdbc :as jdbc]
            [conman.core :refer [with-transaction]]
            [environ.core :refer [env]]
            [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'gallows.db.core/*db*)
    (migrations/migrate ["migrate"])
    (f)))

(deftest test-users
  (with-transaction [t-conn db/*db*]
    (jdbc/db-set-rollback-only! t-conn)
    (is (= 1 (db/create-user!
               {:id         "1"
                :name       "Sam"
                :email      "sam.smith@example.com"
                :pass       "pass"})))
    (is (= [{:id         "1"
             :name        "Sam"
             :email      "sam.smith@example.com"
             :pass       "pass"}]
           (db/get-user {:id "1"})))))
