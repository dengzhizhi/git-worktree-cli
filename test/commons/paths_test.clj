(ns commons.paths-test
  (:require [clojure.test :refer [deftest is testing]]
            [commons.paths :as paths]))

;;; validate-branch-name

(deftest test-validate-branch-name-valid
  (testing "valid branch names"
    (is (true? (:valid? (paths/validate-branch-name "main"))))
    (is (true? (:valid? (paths/validate-branch-name "feature/my-thing"))))
    (is (true? (:valid? (paths/validate-branch-name "fix-123"))))
    (is (true? (:valid? (paths/validate-branch-name "release/v1.0"))))
    (is (true? (:valid? (paths/validate-branch-name "a"))))))

(deftest test-validate-branch-name-empty
  (testing "empty branch name is invalid"
    (let [r (paths/validate-branch-name "")]
      (is (false? (:valid? r)))
      (is (string? (:error r))))))

(deftest test-validate-branch-name-spaces
  (testing "branch name with spaces is invalid"
    (is (false? (:valid? (paths/validate-branch-name "my branch"))))))

(deftest test-validate-branch-name-special-chars
  (testing "branch names with invalid characters"
    (is (false? (:valid? (paths/validate-branch-name "branch~name"))))
    (is (false? (:valid? (paths/validate-branch-name "branch^name"))))
    (is (false? (:valid? (paths/validate-branch-name "branch:name"))))
    (is (false? (:valid? (paths/validate-branch-name "branch?name"))))
    (is (false? (:valid? (paths/validate-branch-name "branch*name"))))
    (is (false? (:valid? (paths/validate-branch-name "branch[name"))))
    (is (false? (:valid? (paths/validate-branch-name "branch\\name"))))))

(deftest test-validate-branch-name-double-dot
  (testing "branch name with .. is invalid"
    (is (false? (:valid? (paths/validate-branch-name "branch..name"))))))

(deftest test-validate-branch-name-dot-lock
  (testing "branch name ending with .lock is invalid"
    (is (false? (:valid? (paths/validate-branch-name "mybranch.lock"))))))

;;; sanitize-branch-name

(deftest test-sanitize-branch-name-slashes
  (testing "sanitize replaces / with -"
    (is (= "feature-my-thing" (paths/sanitize-branch-name "feature/my-thing")))
    (is (= "a-b-c" (paths/sanitize-branch-name "a/b/c")))
    (is (= "main" (paths/sanitize-branch-name "main")))))

;;; resolve-worktree-path

(deftest test-resolve-worktree-path-custom
  (testing "custom-path takes precedence"
    (with-redefs [commons.config/get-worktreepath (fn [] nil)]
      (let [result (paths/resolve-worktree-path "feature/foo"
                                                {:custom-path "/custom/path"
                                                 :cwd "/home/user/myrepo"})]
        ;; Should absolutize /custom/path
        (is (string? result))
        (is (clojure.string/includes? result "custom"))))))

(deftest test-resolve-worktree-path-configured-base
  (testing "configured worktreepath → worktreepath/repo-name/sanitized-branch"
    (with-redefs [commons.config/get-worktreepath (fn [] "/worktrees")
                  commons.git/get-repo-root       (fn [] "/home/user/myrepo")]
      (let [result (paths/resolve-worktree-path "feature/foo"
                                                {:cwd "/home/user/myrepo"})]
        (is (clojure.string/ends-with? result "/worktrees/myrepo/feature-foo"))))))

(deftest test-resolve-worktree-path-sibling
  (testing "no config → sibling dir (parent/cwd-name-sanitized-branch)"
    (with-redefs [commons.config/get-worktreepath (fn [] nil)]
      (let [result (paths/resolve-worktree-path "feature/foo"
                                                {:cwd "/home/user/myrepo"})]
        (is (clojure.string/ends-with? result "/home/user/myrepo-feature-foo"))))))
