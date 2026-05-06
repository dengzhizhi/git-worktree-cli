(ns commons.atomic-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.process :as p]
            [commons.atomic :as atomic]))

;;; create-worktree — stderr captured in ex-info

(deftest test-create-worktree-stderr-in-exception
  (testing "create-worktree includes git stderr in ex-info data"
    (let [err-msg "fatal: 'main' is already used by worktree at '/path'"
          result  {:exit 128 :out "" :err err-msg}]
      (with-redefs [p/process (fn [& _] (delay result))]
        (try
          (atomic/create-worktree (fn [_]) "/path" "main" false)
          (is false "Expected exception to be thrown")
          (catch clojure.lang.ExceptionInfo e
            (let [data (ex-data e)]
              (is (= 128 (:exit data)))
              (is (= err-msg (:err data))))))))))

(deftest test-create-worktree-no-b-flag-for-existing-branch
  (testing "create-worktree omits -b flag when create-branch? is false"
    (let [process-args (atom nil)
          result       {:exit 0 :out "" :err ""}]
      (with-redefs [p/process (fn [& args]
                                (reset! process-args args)
                                (delay result))]
        (atomic/create-worktree (fn [_]) "/path" "main" false)
        ;; Should not include "-b"
        (is (not-any? #{"-b"} @process-args))
        ;; Should include "add", path, branch
        (is (some #{"add"} @process-args))
        (is (some #{"main"} @process-args))
        (is (some #{"/path"} @process-args))))))

;;; with-atomic-rollback — conditional rollback messages

(deftest test-with-atomic-rollback-empty-actions
  (testing "with-atomic-rollback prints 'Cleaning up...' when no rollback actions registered"
    (let [output (with-out-str
                   (try
                     (atomic/with-atomic-rollback
                       (fn [_register!]
                         (throw (ex-info "test error" {}))))
                     (catch Exception _)))]
      (is (str/includes? output "Cleaning up..."))
      (is (str/includes? output "Done."))
      (is (not (str/includes? output "Rolling back"))))))

(deftest test-with-atomic-rollback-rollback-message
  (testing "with-atomic-rollback prints 'Rolling back...' when rollback actions registered"
    (let [output (with-out-str
                   (try
                     (atomic/with-atomic-rollback
                       (fn [register!]
                         (register! (fn [] (println "cleanup step")))
                         (throw (ex-info "test error" {}))))
                     (catch Exception _)))]
      (is (str/includes? output "Rolling back changes..."))
      (is (str/includes? output "Rollback complete."))
      (is (not (str/includes? output "Cleaning up"))))))

(deftest test-with-atomic-rollback-success-no-rollback
  (testing "with-atomic-rollback returns result on success without rollback messages"
    (let [output (with-out-str
                   (atomic/with-atomic-rollback
                     (fn [_register!]
                       "success-result")))]
      (is (str/blank? output)))))
