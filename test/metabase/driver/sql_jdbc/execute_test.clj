(ns metabase.driver.sql-jdbc.execute-test
  (:require [clojure.java.jdbc :as jdbc]
            [metabase.driver.sql-jdbc-test :as sql-jdbc-test]
            [metabase.query-processor :as qp]
            [metabase.test.data :as data]
            [metabase.test.data.datasets :as datasets])
  (:import java.sql.PreparedStatement))

(defn- do-with-max-rows [f]
  (let [orig-query jdbc/query
        max-rows   (atom nil)]
    (with-redefs [jdbc/query (fn [conn sql-params & [opts]]
                               (when (sequential? sql-params)
                                 (let [[statement] sql-params]
                                   (when (instance? PreparedStatement statement)
                                     (reset! max-rows (.getMaxRows ^PreparedStatement statement)))))
                               (orig-query conn sql-params opts))]
      (f)
      @max-rows)))

(defmacro ^:private with-max-rows
  "Runs query in `body`, and returns the max rows that was set for (via `PreparedStatement.setMaxRows()`) for that
  query. This number is the number we've instructed JDBC to limit the results to."
  [& body]
  `(do-with-max-rows (fn [] ~@body)))

;; We should be setting statement max rows based on appropriate limits when running queries
(datasets/expect-with-drivers @sql-jdbc-test/sql-jdbc-drivers
  10
  (with-max-rows
    (qp/process-query
      {:database (data/id)
       :type     :query
       :query    {:source-table (data/id :venues)
                  :limit        10}})))

(datasets/expect-with-drivers @sql-jdbc-test/sql-jdbc-drivers
  5
  (with-max-rows
    (qp/process-query
      {:database (data/id)
       :type     :query
       :query    {:source-table (data/id :venues)
                  :limit        10}
       :constraints {:max-results 5}})))


(datasets/expect-with-drivers @sql-jdbc-test/sql-jdbc-drivers
  15
  (with-max-rows
    (qp/process-query
      {:database    (data/id)
       :type        :native
       :native      (qp/query->native {:database (data/id)
                                       :type     :query
                                       :query    {:source-table (data/id :venues)}})
       :constraints {:max-results 15}})))
