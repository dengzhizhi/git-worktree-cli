(ns commands.list-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [commands.list :as list]
            [commons.git :as git]))

;; Sample WorktreeInfo maps

(def normal-wt
  {:path "/home/user/myrepo"
   :head "abc1234567"
   :branch "main"
   :detached? false
   :bare? false
   :main? true
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil})

(def detached-wt
  {:path "/home/user/myrepo-detached"
   :head "aabb112233"
   :branch nil
   :detached? true
   :bare? false
   :main? false
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil})

(def bare-wt
  {:path "/home/user/myrepo-bare"
   :head "ccdd334455"
   :branch nil
   :detached? false
   :bare? true
   :main? false
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil})

;;; branch-name (private helper)

(def branch-name #'list/branch-name)

(deftest test-branch-name-normal
  (testing "returns branch name for normal worktree"
    (is (= "main" (branch-name normal-wt)))))

(deftest test-branch-name-detached
  (testing "returns (detached at XXXXXXX) using first 7 chars of head"
    (is (= "(detached at aabb112)" (branch-name detached-wt)))))

(deftest test-branch-name-bare
  (testing "returns (bare) for bare worktree"
    (is (= "(bare)" (branch-name bare-wt)))))

;;; run with --name-only

(deftest test-run-name-only
  (testing "--name-only outputs one branch name per line, no headers"
    (with-redefs [git/get-worktrees (constantly [normal-wt bare-wt detached-wt])]
      (let [output (with-out-str (list/run {:opts {:name-only true :dir-only false}}))]
        (is (= ["main" "(bare)" "(detached at aabb112)"]
               (str/split-lines output)))))))

;;; run with --dir-only

(deftest test-run-dir-only
  (testing "--dir-only outputs one path per line, no headers"
    (with-redefs [git/get-worktrees (constantly [normal-wt bare-wt])]
      (let [output (with-out-str (list/run {:opts {:name-only false :dir-only true}}))]
        (is (= ["/home/user/myrepo" "/home/user/myrepo-bare"]
               (str/split-lines output)))))))

;;; run with no flags

(deftest test-run-default-shows-header
  (testing "default output includes header with count"
    (with-redefs [git/get-worktrees (constantly [normal-wt])]
      (let [output (with-out-str (list/run {:opts {}}))]
        (is (str/includes? output "Worktrees (1):"))))))

;;; run with empty worktree list

(deftest test-run-empty
  (testing "empty worktree list shows helpful message"
    (with-redefs [git/get-worktrees (constantly [])]
      (let [output (with-out-str (list/run {:opts {}}))]
        (is (str/includes? output "No worktrees found"))))))

;;; --name-only takes precedence over --dir-only

(deftest test-run-name-only-takes-precedence
  (testing "when both flags set, --name-only wins"
    (with-redefs [git/get-worktrees (constantly [normal-wt])]
      (let [output (with-out-str (list/run {:opts {:name-only true :dir-only true}}))]
        (is (= ["main"] (str/split-lines output)))))))
