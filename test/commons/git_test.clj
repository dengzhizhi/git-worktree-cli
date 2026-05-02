(ns commons.git-test
  (:require [clojure.test :refer [deftest is testing]]
            [commons.git :as git]))

;; Fixture: multi-worktree porcelain output
(def sample-porcelain
  "worktree /home/user/project
HEAD abc123def456abc123def456abc123def456abc123
branch refs/heads/main

worktree /home/user/project-feature-foo
HEAD def456abc123def456abc123def456abc123def456
branch refs/heads/feature/foo

worktree /home/user/project-detached
HEAD aabbccddeeff00112233445566778899aabbccdd
detached

worktree /home/user/project-bare
HEAD 0000000000000000000000000000000000000000
bare

worktree /home/user/project-locked
HEAD 111222333444555666777888999000aaabbbcccd
branch refs/heads/locked-branch
locked my lock reason

worktree /home/user/project-prunable
HEAD aaabbbcccdddeeefffaabbccddeeff0011223344
branch refs/heads/prunable-branch
prunable gitdir file points to non-existent location
")

(deftest test-parse-worktrees-count
  (testing "parses correct number of worktrees"
    (let [wts (git/parse-worktrees sample-porcelain)]
      (is (= 6 (count wts))))))

(deftest test-parse-worktrees-first-is-main
  (testing "first worktree block has :main? true"
    (let [wts (git/parse-worktrees sample-porcelain)]
      (is (true? (:main? (first wts))))
      (is (every? #(false? (:main? %)) (rest wts))))))

(deftest test-parse-worktrees-branch-stripping
  (testing "strips refs/heads/ prefix from branch"
    (let [wts (git/parse-worktrees sample-porcelain)]
      (is (= "main" (:branch (first wts))))
      (is (= "feature/foo" (:branch (second wts)))))))

(deftest test-parse-worktrees-path
  (testing "parses worktree path correctly"
    (let [wts (git/parse-worktrees sample-porcelain)]
      (is (= "/home/user/project" (:path (first wts)))))))

(deftest test-parse-worktrees-detached
  (testing "detached worktree has :detached? true and nil branch"
    (let [wts (git/parse-worktrees sample-porcelain)
          detached (nth wts 2)]
      (is (true? (:detached? detached)))
      (is (nil? (:branch detached))))))

(deftest test-parse-worktrees-bare
  (testing "bare worktree has :bare? true"
    (let [wts (git/parse-worktrees sample-porcelain)
          bare (nth wts 3)]
      (is (true? (:bare? bare))))))

(deftest test-parse-worktrees-locked
  (testing "locked worktree has :locked? true and lock-reason"
    (let [wts (git/parse-worktrees sample-porcelain)
          locked (nth wts 4)]
      (is (true? (:locked? locked)))
      (is (= "my lock reason" (:lock-reason locked))))))

(deftest test-parse-worktrees-prunable
  (testing "prunable worktree has :prunable? true and prune-reason"
    (let [wts (git/parse-worktrees sample-porcelain)
          prunable (nth wts 5)]
      (is (true? (:prunable? prunable)))
      (is (= "gitdir file points to non-existent location" (:prune-reason prunable))))))

(deftest test-parse-worktrees-locked-no-reason
  (testing "locked with no reason has :lock-reason nil"
    (let [porcelain "worktree /home/user/main\nHEAD abc123\nbranch refs/heads/main\n\nworktree /home/user/locked\nHEAD def456\nbranch refs/heads/lb\nlocked\n"
          wts (git/parse-worktrees porcelain)
          locked (second wts)]
      (is (true? (:locked? locked)))
      (is (nil? (:lock-reason locked))))))

(deftest test-parse-worktrees-empty
  (testing "empty/blank input returns empty vec"
    (is (= [] (git/parse-worktrees "")))
    (is (= [] (git/parse-worktrees "\n\n")))))

(deftest test-parse-worktrees-defaults
  (testing "normal worktree has false booleans by default"
    (let [wts (git/parse-worktrees sample-porcelain)
          main (first wts)]
      (is (false? (:detached? main)))
      (is (false? (:bare? main)))
      (is (false? (:locked? main)))
      (is (false? (:prunable? main)))
      (is (nil? (:lock-reason main)))
      (is (nil? (:prune-reason main))))))
