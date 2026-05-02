(ns commands.open-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [commons.config :as config]
            [commons.git :as git]
            [commands.open :as cmd-open]))

;; Temp config dir fixture
(defn with-temp-config-dir [f]
  (let [tmp (str (fs/create-temp-dir))]
    (try
      (with-redefs [config/config-path (fn [] (str tmp "/worktree/config.edn"))]
        (f))
      (finally
        (fs/delete-tree tmp)))))

(use-fixtures :each with-temp-config-dir)

;;; resolve-executable (pure function)

(def resolve-executable #'cmd-open/resolve-executable)

(deftest test-resolve-executable-uses-default
  (testing "returns default executable when no override"
    (is (= "code" (resolve-executable "vscode" nil)))))

(deftest test-resolve-executable-uses-override
  (testing "returns override path when provided"
    (is (= "/custom/code" (resolve-executable "vscode" "/custom/code")))))

(deftest test-resolve-executable-nil-for-unknown
  (testing "returns nil for unknown editor (after validation would have caught it)"
    (is (nil? (resolve-executable "badeditor" nil)))))

(deftest test-resolve-executable-override-beats-default
  (testing "override always wins over default"
    (is (= "/opt/homebrew/bin/nvim" (resolve-executable "nvim" "/opt/homebrew/bin/nvim")))))

;;; Helper: exit! that stops execution (throws) so code doesn't continue past the exit point

(defn- capture-exit
  "Calls f, intercepts exit! calls. Returns {:code <n> :output <str>}."
  [exit-var f]
  (let [exit-code (atom nil)
        output    (with-out-str
                    (with-redefs-fn {exit-var (fn [code]
                                                (reset! exit-code code)
                                                (throw (ex-info "test-exit" {:code code})))}
                      (fn []
                        (try (f)
                             (catch clojure.lang.ExceptionInfo e
                               (when-not (= "test-exit" (ex-message e))
                                 (throw e)))))))]
    {:code @exit-code :output output}))

;;; run — invalid editor validation

(deftest test-run-invalid-editor-exits
  (testing "run with invalid editor name exits 1"
    (let [{:keys [code]} (capture-exit #'cmd-open/exit!
                           #(with-redefs [config/get-editor (constantly "badeditor")]
                              (cmd-open/run {:opts {}})))]
      (is (= 1 code)))))

(deftest test-run-invalid-editor-output
  (testing "run with invalid editor prints error and valid editors"
    (let [{:keys [output]} (capture-exit #'cmd-open/exit!
                             #(with-redefs [config/get-editor (constantly "notepad")]
                                (cmd-open/run {:opts {}})))]
      (is (str/includes? output "unsupported editor"))
      (is (str/includes? output "vscode")))))

;;; run — no editor configured

(deftest test-run-no-editor-configured-exits
  (testing "run with no editor configured exits 1"
    (let [{:keys [code]} (capture-exit #'cmd-open/exit!
                           #(with-redefs [config/get-editor (constantly nil)]
                              (cmd-open/run {:opts {}})))]
      (is (= 1 code)))))

(deftest test-run-no-editor-configured-output
  (testing "run with no editor configured prints actionable message"
    (let [{:keys [output]} (capture-exit #'cmd-open/exit!
                             #(with-redefs [config/get-editor (constantly nil)]
                                (cmd-open/run {:opts {}})))]
      (is (str/includes? output "No editor configured")))))

;;; run — valid editor calls p/shell with varargs

(deftest test-run-valid-editor-calls-shell-with-varargs
  (testing "run with valid editor calls p/shell with [executable path] not string concat"
    (let [shell-args (atom nil)
          exit-code  (atom nil)
          fake-wt    {:path "/fake/worktree" :branch "my-branch"}]
      (with-redefs [cmd-open/exit!                  (fn [code] (reset! exit-code code))
                    config/get-editor               (constantly "vscode")
                    config/get-editor-path          (constantly nil)
                    fs/exists?                      (constantly true)
                    git/get-worktrees               (constantly [fake-wt])
                    git/find-worktree-by-path       (constantly nil)
                    git/find-worktree-by-branch     (constantly fake-wt)
                    p/shell                         (fn [& args] (reset! shell-args (vec args)) nil)]
        (cmd-open/run {:opts {:path-or-branch "my-branch"}})
        ;; Should not have exited
        (is (nil? @exit-code))
        ;; p/shell called as (shell {:dir path} executable path)
        ;; so args = [{:dir "/fake/worktree"} "code" "/fake/worktree"]
        (is (= {:dir "/fake/worktree"} (first @shell-args)))
        (is (= "code" (second @shell-args)))
        (is (= "/fake/worktree" (nth @shell-args 2)))))))
