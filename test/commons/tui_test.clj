(ns commons.tui-test
  (:require [clojure.test :refer [deftest is testing]]
            [commons.tui :as tui]))

;; Sample WorktreeInfo maps for testing

(def main-wt
  {:path "/home/user/myrepo"
   :head "abc123"
   :branch "main"
   :detached? false
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil
   :main? true
   :bare? false})

(def feature-wt
  {:path "/home/user/myrepo-feature-foo"
   :head "def456"
   :branch "feature/foo"
   :detached? false
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil
   :main? false
   :bare? false})

(def detached-wt
  {:path "/home/user/myrepo-detached"
   :head "aabb1122"
   :branch nil
   :detached? true
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil
   :main? false
   :bare? false})

(def bare-wt
  {:path "/home/user/myrepo-bare"
   :head "ccdd3344"
   :branch nil
   :detached? false
   :locked? false
   :lock-reason nil
   :prunable? false
   :prune-reason nil
   :main? false
   :bare? true})

(def locked-wt
  (assoc feature-wt :locked? true :lock-reason "in use" :branch "locked-branch"))

(def prunable-wt
  (assoc feature-wt :prunable? true :prune-reason "gone" :branch "prunable-branch"))

;;; format-worktree-line

(deftest test-format-worktree-line-basic
  (testing "normal worktree shows branch and path"
    (let [line (tui/format-worktree-line feature-wt)]
      (is (clojure.string/includes? line "feature/foo"))
      (is (clojure.string/includes? line "/home/user/myrepo-feature-foo")))))

(deftest test-format-worktree-line-main-indicator
  (testing "main worktree shows [main] indicator"
    (let [line (tui/format-worktree-line main-wt)]
      (is (clojure.string/includes? line "[main]")))))

(deftest test-format-worktree-line-detached
  (testing "detached worktree shows (detached at ...) form"
    (let [line (tui/format-worktree-line detached-wt)]
      (is (clojure.string/includes? line "detached"))
      (is (clojure.string/includes? line "aabb1122")))))

(deftest test-format-worktree-line-bare
  (testing "bare worktree shows (bare)"
    (let [line (tui/format-worktree-line bare-wt)]
      (is (clojure.string/includes? line "bare")))))

(deftest test-format-worktree-line-locked
  (testing "locked worktree shows [locked] indicator"
    (let [line (tui/format-worktree-line locked-wt)]
      (is (clojure.string/includes? line "[locked]")))))

(deftest test-format-worktree-line-prunable
  (testing "prunable worktree shows [prunable] indicator"
    (let [line (tui/format-worktree-line prunable-wt)]
      (is (clojure.string/includes? line "[prunable]")))))

;;; numbered-select (single)

(deftest test-numbered-select-single
  (testing "numbered-select returns correct item for single input"
    (let [items ["alpha" "beta" "gamma"]
          result (with-in-str "2\n"
                   (tui/numbered-select items {:prompt "Pick:"}))]
      (is (= "beta" result)))))

;;; numbered-select (multi)

(deftest test-numbered-select-multi
  (testing "numbered-select returns multiple items for comma-separated input"
    (let [items ["alpha" "beta" "gamma"]
          result (with-in-str "1,3\n"
                   (tui/numbered-select items {:prompt "Pick:" :multi? true}))]
      (is (= ["alpha" "gamma"] (vec result))))))

;;; confirm

(deftest test-confirm-yes
  (testing "confirm returns true for 'y'"
    (is (true? (with-in-str "y\n" (tui/confirm "Continue?" false))))))

(deftest test-confirm-no
  (testing "confirm returns false for 'n'"
    (is (false? (with-in-str "n\n" (tui/confirm "Continue?" true))))))

(deftest test-confirm-empty-uses-default-true
  (testing "confirm returns true for empty input when default is true"
    (is (true? (with-in-str "\n" (tui/confirm "Continue?" true))))))

(deftest test-confirm-empty-uses-default-false
  (testing "confirm returns false for empty input when default is false"
    (is (false? (with-in-str "\n" (tui/confirm "Continue?" false))))))
