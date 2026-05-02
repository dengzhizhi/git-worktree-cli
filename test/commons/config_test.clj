(ns commons.config-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [commons.config :as config]))

;; Use a temp directory for config file I/O, overriding XDG_CONFIG_HOME
(def ^:dynamic *test-config-dir* nil)

(defn with-temp-config-dir [f]
  (let [tmp (str (fs/create-temp-dir))]
    (try
      (binding [*test-config-dir* tmp]
        ;; Override config-path by rebinding the env var lookup
        (with-redefs [config/config-path (fn [] (str tmp "/worktree/config.edn"))]
          (f)))
      (finally
        (fs/delete-tree tmp)))))

(use-fixtures :each with-temp-config-dir)

(deftest test-read-config-missing-file
  (testing "read-config returns {} when no file exists"
    (is (= {} (config/read-config)))))

(deftest test-write-then-read-config
  (testing "write-config then read-config round-trips data"
    (config/write-config {:editor "vim" :worktreepath "/some/path"})
    (is (= {:editor "vim" :worktreepath "/some/path"} (config/read-config)))))

(deftest test-get-editor-default
  (testing "get-editor returns nil when no config file"
    (is (nil? (config/get-editor)))))

(deftest test-get-editor-path-not-set
  (testing "get-editor-path returns nil when no override configured"
    (is (nil? (config/get-editor-path "vscode")))))

(deftest test-set-get-editor-path-round-trip
  (testing "set-editor-path! then get-editor-path returns the path"
    (config/set-editor-path! "vscode" "/custom/code")
    (is (= "/custom/code" (config/get-editor-path "vscode")))))

(deftest test-set-editor-path-preserves-other-keys
  (testing "set-editor-path! does not clobber :editor or other paths"
    (config/set-editor! "nvim")
    (config/set-editor-path! "vscode" "/custom/code")
    (is (= "nvim" (config/get-editor)))
    (is (= "/custom/code" (config/get-editor-path "vscode")))))

(deftest test-set-editor-path-multiple
  (testing "two editor paths stored independently"
    (config/set-editor-path! "vscode" "/custom/code")
    (config/set-editor-path! "nvim" "/opt/nvim")
    (is (= "/custom/code" (config/get-editor-path "vscode")))
    (is (= "/opt/nvim" (config/get-editor-path "nvim")))))

(deftest test-get-editor-path-uses-keyword-internally
  (testing "editor-paths stored as keywords, retrievable by string key"
    (config/set-editor-path! "zed" "/usr/bin/zed")
    (is (= "/usr/bin/zed" (config/get-editor-path "zed")))))

(deftest test-set-editor-round-trip
  (testing "set-editor! then get-editor returns set value"
    (config/set-editor! "code")
    (is (= "code" (config/get-editor)))))

(deftest test-get-worktreepath-default
  (testing "get-worktreepath returns nil when not set"
    (is (nil? (config/get-worktreepath)))))

(deftest test-set-worktreepath-absolute
  (testing "set-worktreepath! stores an absolute path"
    (config/set-worktreepath! "/abs/path/to/worktrees")
    (is (= "/abs/path/to/worktrees" (config/get-worktreepath)))))

(deftest test-set-worktreepath-tilde-expansion
  (testing "set-worktreepath! expands ~/... to absolute path"
    (config/set-worktreepath! "~/worktrees")
    (let [result (config/get-worktreepath)
          home   (System/getProperty "user.home")]
      (is (str/starts-with? result home))
      (is (str/ends-with? result "/worktrees")))))

(deftest test-clear-worktreepath
  (testing "clear-worktreepath! removes the key"
    (config/set-worktreepath! "/some/path")
    (config/clear-worktreepath!)
    (is (nil? (config/get-worktreepath)))))

(deftest test-set-editor-preserves-worktreepath
  (testing "set-editor! preserves existing worktreepath"
    (config/set-worktreepath! "/my/trees")
    (config/set-editor! "emacs")
    (is (= "emacs" (config/get-editor)))
    (is (= "/my/trees" (config/get-worktreepath)))))

(deftest test-config-path-format
  (testing "config-path ends with worktree/config.edn"
    (is (str/ends-with? (config/config-path) "worktree/config.edn"))))
