(ns commons.setup-test
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [commons.setup :as setup]))

(defn with-temp-dir
  "Creates a temp dir, calls f with the dir path string, then cleans up."
  [f]
  (let [tmp (str (fs/create-temp-dir))]
    (try (f tmp)
         (finally (fs/delete-tree tmp)))))

;;; load-setup-commands

(deftest test-load-setup-commands-plain-array
  (testing "plain JSON array → {:commands [...] :file-path ...}"
    (with-temp-dir
      (fn [tmp]
        (spit (str tmp "/worktrees.json") "[\"npm install\", \"cp .env.example .env\"]")
        (let [result (setup/load-setup-commands tmp)]
          (is (some? result))
          (is (= ["npm install" "cp .env.example .env"] (:commands result)))
          (is (clojure.string/includes? (:file-path result) "worktrees.json")))))))

(deftest test-load-setup-commands-object-format
  (testing "object with setup-worktree key → same result"
    (with-temp-dir
      (fn [tmp]
        (spit (str tmp "/worktrees.json")
              "{\"setup-worktree\": [\"echo hello\", \"echo world\"]}")
        (let [result (setup/load-setup-commands tmp)]
          (is (some? result))
          (is (= ["echo hello" "echo world"] (:commands result))))))))

(deftest test-load-setup-commands-cursor-takes-precedence
  (testing ".cursor/worktrees.json takes precedence over .claude and root"
    (with-temp-dir
      (fn [tmp]
        (fs/create-dirs (str tmp "/.cursor"))
        (fs/create-dirs (str tmp "/.claude"))
        (spit (str tmp "/.cursor/worktrees.json") "[\"cursor-cmd\"]")
        (spit (str tmp "/.claude/worktrees.json") "[\"claude-cmd\"]")
        (spit (str tmp "/worktrees.json") "[\"root-cmd\"]")
        (let [result (setup/load-setup-commands tmp)]
          (is (= ["cursor-cmd"] (:commands result)))
          (is (clojure.string/includes? (:file-path result) ".cursor")))))))

(deftest test-load-setup-commands-claude-takes-precedence-over-root
  (testing ".claude/worktrees.json takes precedence over root worktrees.json"
    (with-temp-dir
      (fn [tmp]
        (fs/create-dirs (str tmp "/.claude"))
        (spit (str tmp "/.claude/worktrees.json") "[\"claude-cmd\"]")
        (spit (str tmp "/worktrees.json") "[\"root-cmd\"]")
        (let [result (setup/load-setup-commands tmp)]
          (is (= ["claude-cmd"] (:commands result)))
          (is (clojure.string/includes? (:file-path result) ".claude")))))))

(deftest test-load-setup-commands-missing-file
  (testing "returns nil when no setup file found"
    (with-temp-dir
      (fn [tmp]
        (is (nil? (setup/load-setup-commands tmp)))))))

(deftest test-load-setup-commands-empty-array
  (testing "empty commands array → nil"
    (with-temp-dir
      (fn [tmp]
        (spit (str tmp "/worktrees.json") "[]")
        (is (nil? (setup/load-setup-commands tmp)))))))

(deftest test-load-setup-commands-falls-back-to-root
  (testing "falls back to root worktrees.json when .cursor and .claude absent"
    (with-temp-dir
      (fn [tmp]
        (spit (str tmp "/worktrees.json") "[\"root-only-cmd\"]")
        (let [result (setup/load-setup-commands tmp)]
          (is (= ["root-only-cmd"] (:commands result))))))))
