(ns commands.config-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [commons.config :as config]
            [commands.config :as cmd-config]))

;; Reuse the same temp-dir fixture pattern as commons.config-test
(defn with-temp-config-dir [f]
  (let [tmp (str (fs/create-temp-dir))]
    (try
      (with-redefs [config/config-path (fn [] (str tmp "/worktree/config.edn"))]
        (f))
      (finally
        (fs/delete-tree tmp)))))

(use-fixtures :each with-temp-config-dir)

;;; set-editor-path!

(deftest test-set-editor-path-valid
  (testing "set-editor-path! with a valid editor stores the path in config"
    (with-redefs [cmd-config/exit! (fn [_] nil)]
      (cmd-config/set-editor-path! {:opts {:editor "vscode" :path "/custom/code"}})
      (is (= "/custom/code" (config/get-editor-path "vscode"))))))

(deftest test-set-editor-path-output-on-success
  (testing "set-editor-path! prints a confirmation message"
    (with-redefs [cmd-config/exit! (fn [_] nil)]
      (let [output (with-out-str
                     (cmd-config/set-editor-path! {:opts {:editor "nvim" :path "/opt/nvim"}}))]
        (is (str/includes? output "nvim"))
        (is (str/includes? output "/opt/nvim"))))))

(deftest test-set-editor-path-invalid-editor
  (testing "set-editor-path! with an invalid editor prints error and calls exit"
    (let [exit-code (atom nil)]
      (with-redefs [cmd-config/exit! (fn [code] (reset! exit-code code))]
        (let [output (with-out-str
                       (cmd-config/set-editor-path! {:opts {:editor "badeditor" :path "/foo"}}))]
          (is (str/includes? output "unsupported editor"))
          (is (str/includes? output "badeditor"))
          (is (= 1 @exit-code)))))))

(deftest test-set-editor-path-invalid-editor-lists-valid
  (testing "set-editor-path! error message includes list of valid editors"
    (let [exit-code (atom nil)]
      (with-redefs [cmd-config/exit! (fn [code] (reset! exit-code code))]
        (let [output (with-out-str
                       (cmd-config/set-editor-path! {:opts {:editor "notepad" :path "/foo"}}))]
          (is (str/includes? output "vscode"))
          (is (= 1 @exit-code)))))))

;;; get-editor-path!

(deftest test-get-editor-path-default
  (testing "get-editor-path! with no override prints the default executable"
    (with-redefs [cmd-config/exit! (fn [_] nil)]
      (let [output (with-out-str
                     (cmd-config/get-editor-path! {:opts {:editor "vscode"}}))]
        (is (str/includes? output "code"))))))

(deftest test-get-editor-path-override
  (testing "get-editor-path! after set-editor-path! prints the configured path"
    (with-redefs [cmd-config/exit! (fn [_] nil)]
      (config/set-editor-path! "vscode" "/opt/homebrew/bin/code")
      (let [output (with-out-str
                     (cmd-config/get-editor-path! {:opts {:editor "vscode"}}))]
        (is (str/includes? output "/opt/homebrew/bin/code"))))))

(deftest test-get-editor-path-invalid-editor
  (testing "get-editor-path! with invalid editor exits 1"
    (let [exit-code (atom nil)]
      (with-redefs [cmd-config/exit! (fn [code] (reset! exit-code code))]
        (with-out-str
          (cmd-config/get-editor-path! {:opts {:editor "notepad"}}))
        (is (= 1 @exit-code))))))

;;; set! — editor validation

(deftest test-set-editor-valid
  (testing "config set editor with valid name succeeds"
    (with-redefs [cmd-config/exit! (fn [_] nil)]
      (cmd-config/set! {:opts {:key :editor :value :vscode}})
      (is (= "vscode" (config/get-editor))))))

(deftest test-set-editor-invalid
  (testing "config set editor with invalid name exits 1"
    (let [exit-code (atom nil)]
      (with-redefs [cmd-config/exit! (fn [code] (reset! exit-code code))]
        (with-out-str
          (cmd-config/set! {:opts {:key :editor :value :badeditor}}))
        (is (= 1 @exit-code))))))

(deftest test-set-editor-invalid-output
  (testing "config set editor with invalid name prints error + valid list"
    (let [exit-code (atom nil)]
      (with-redefs [cmd-config/exit! (fn [code] (reset! exit-code code))]
        (let [output (with-out-str
                       (cmd-config/set! {:opts {:key :editor :value :notepad}}))]
          (is (str/includes? output "unsupported editor"))
          (is (str/includes? output "vscode"))
          (is (= 1 @exit-code)))))))
