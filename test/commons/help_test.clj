(ns commons.help-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [commons.help :as help]))

(def sample-entry
  {:cmds      ["new"]
   :desc      "Create a new worktree for the given branch"
   :args->opts [:branch-name]
   :spec      {:path     {:alias :p :desc "Path for new worktree"}
               :checkout {:alias :c :desc "Create branch if absent" :coerce :boolean :default false}
               :install  {:alias :i :desc "Package manager (npm, pnpm, bun, yarn)"}}})

(def no-args-entry
  {:cmds [:desc "List all worktrees"]
   :desc "List all worktrees"
   :spec {:name-only {:desc "Output branch names only" :coerce :boolean :default false}
          :dir-only  {:desc "Output paths only"        :coerce :boolean :default false}}})

(def multi-cmd-entry
  {:cmds      ["config" "set"]
   :desc      "Set a configuration value"
   :args->opts [:key :value]
   :spec      {}})

;;; Usage line

(deftest test-usage-includes-command-name
  (testing "usage line includes the command name"
    (is (str/includes? (help/format-cmd-help sample-entry) "worktree new"))))

(deftest test-usage-includes-positional-args
  (testing "usage line includes positional args in angle brackets"
    (is (str/includes? (help/format-cmd-help sample-entry) "<branch-name>"))))

(deftest test-usage-multi-word-command
  (testing "usage line includes all words of multi-word command"
    (is (str/includes? (help/format-cmd-help multi-cmd-entry) "worktree config set"))))

(deftest test-usage-includes-multiple-positional-args
  (testing "usage line includes all positional args"
    (let [out (help/format-cmd-help multi-cmd-entry)]
      (is (str/includes? out "<key>"))
      (is (str/includes? out "<value>")))))

;;; Description

(deftest test-description-appears-in-output
  (testing "command description appears in output"
    (is (str/includes? (help/format-cmd-help sample-entry) "Create a new worktree for the given branch"))))

;;; Options section

(deftest test-options-section-present
  (testing "Options: header is present"
    (is (str/includes? (help/format-cmd-help sample-entry) "Options:"))))

(deftest test-option-long-name-appears
  (testing "long option name appears with -- prefix"
    (is (str/includes? (help/format-cmd-help sample-entry) "--path"))))

(deftest test-option-alias-appears
  (testing "alias appears with - prefix"
    (is (str/includes? (help/format-cmd-help sample-entry) "-p"))))

(deftest test-option-description-appears
  (testing "option description appears in output"
    (is (str/includes? (help/format-cmd-help sample-entry) "Path for new worktree"))))

(deftest test-non-boolean-shows-value-placeholder
  (testing "non-boolean option shows <value> placeholder"
    (let [out (help/format-cmd-help sample-entry)]
      (is (re-find #"--path.*<value>" out)))))

(deftest test-boolean-omits-value-placeholder
  (testing "boolean option does not show <value> placeholder on its flag line"
    (let [out (help/format-cmd-help sample-entry)
          checkout-line (first (filter #(str/includes? % "--checkout") (str/split-lines out)))]
      (is (not (str/includes? checkout-line "<value>"))))))

(deftest test-default-false-not-shown
  (testing "default false is not shown to avoid noise"
    (let [out (help/format-cmd-help sample-entry)
          checkout-line (first (filter #(str/includes? % "--checkout") (str/split-lines out)))]
      (is (not (str/includes? checkout-line "(default: false)"))))))

(deftest test-help-flag-always-present
  (testing "--help, -h always appears in options"
    (let [out (help/format-cmd-help sample-entry)]
      (is (str/includes? out "--help"))
      (is (str/includes? out "-h")))))
