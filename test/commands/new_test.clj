(ns commands.new-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [commons.git :as git]
            [commons.paths :as paths]
            [commons.tui :as tui]
            [commons.worktree :as worktree]
            [commands.new :as cmd-new]))

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

;;; Common mocks for all tests

(def repo-root "/home/user/project")
(def default-branch "my-feature")
(def default-target-path (str repo-root "/.worktrees/" default-branch))

(defn with-valid-state
  "Sets up common mocks so that new/run passes validation and reaches create-or-use-worktree."
  [branch-name & mocks]
  (let [base-mocks {#'git/get-repo-root          (constantly repo-root)
                    #'paths/validate-branch-name (constantly {:valid? true})
                    #'git/is-main-repo-bare?     (constantly false)
                    #'git/is-worktree-clean?     (constantly true)
                    #'paths/resolve-worktree-path (constantly default-target-path)
                    #'fs/cwd                     (constantly repo-root)}
        all-mocks  (merge base-mocks (apply hash-map mocks))]
    (with-redefs-fn all-mocks
      (fn [] (cmd-new/run {:opts {:branch-name (name branch-name)}})))))

;;; Current branch error

(deftest test-run-current-branch-error
  (testing "run with current branch exits 1 with specific error message"
    (let [{:keys [code output]} (capture-exit #'worktree/exit!
                                  #(with-valid-state
                                     default-branch
                                     #'git/find-worktree-by-branch (fn [b] {:branch b :path "/other/wt"})
                                     #'git/get-current-branch     (constantly default-branch)))]
      (is (= 1 code))
      (is (str/includes? output (str "Error: cannot create worktree for current branch (" default-branch ")"))))))

;;; Other-worktree branch error

(deftest test-run-other-worktree-error
  (testing "run with branch checked out in another worktree exits 1 with specific message"
    (let [{:keys [code output]} (capture-exit #'worktree/exit!
                                  #(with-valid-state
                                     default-branch
                                     #'git/find-worktree-by-branch (fn [b] {:branch b :path "/other/wt"})
                                     #'git/get-current-branch     (constantly "different-branch")))]
      (is (= 1 code))
      (is (str/includes? output "Error: branch 'my-feature' is already checked out at '/other/wt'")))))

;;; Generic git failure

(deftest test-run-generic-git-failure
  (testing "run with git failure prints stderr and exits 1"
    (let [err-msg "fatal: some git error"]
      (capture-exit #'worktree/exit!
        (fn []
          (with-redefs [git/find-worktree-by-branch (constantly nil)
                        git/branch-exists-local?   (constantly false)
                        git/get-upstream-remote    (constantly nil)
                        fs/exists?                 (constantly false)
                        p/process                  (fn [& _] (delay {:exit 128 :out "" :err err-msg}))
                        git/get-repo-root          (constantly repo-root)
                        paths/validate-branch-name (constantly {:valid? true})
                        git/is-main-repo-bare?     (constantly false)
                        git/is-worktree-clean?     (constantly true)
                        paths/resolve-worktree-path (constantly default-target-path)
                        fs/cwd                     (constantly repo-root)]
            (cmd-new/run {:opts {:branch-name default-branch}})))))))

(deftest test-run-generic-git-failure-msg
  (testing "run with git failure shows 'Failed to create worktree:' + git stderr"
    (let [err-msg "fatal: could not create worktree"
          {:keys [code output]} (capture-exit #'worktree/exit!
                                  (fn []
                                    (with-redefs [git/find-worktree-by-branch (constantly nil)
                                                  git/branch-exists-local?   (constantly false)
                                                  git/get-upstream-remote    (constantly nil)
                                                  fs/exists?                 (constantly false)
                                                  p/process                  (fn [& _] (delay {:exit 128 :out "" :err err-msg}))
                                                  git/get-repo-root          (constantly repo-root)
                                                  paths/validate-branch-name (constantly {:valid? true})
                                                  git/is-main-repo-bare?     (constantly false)
                                                  git/is-worktree-clean?     (constantly true)
                                                  paths/resolve-worktree-path (constantly default-target-path)
                                                  fs/cwd                     (constantly repo-root)]
                                      (cmd-new/run {:opts {:branch-name default-branch}}))))]
      (is (= 1 code))
      (is (str/includes? output "Failed to create worktree:"))
      (is (str/includes? output err-msg)))))

;;; Happy path

(deftest test-run-happy-path
  (testing "run with valid inputs succeeds without exiting"
    (let [exit-code (atom nil)]
      (with-redefs [git/find-worktree-by-branch (constantly nil)
                    git/branch-exists-local?   (constantly false)
                    git/get-upstream-remote    (constantly nil)
                    fs/exists?                 (constantly false)
                    p/process                  (fn [& _] (delay {:exit 0 :out "" :err ""}))
                    git/get-repo-root          (constantly repo-root)
                    paths/validate-branch-name (constantly {:valid? true})
                    git/is-main-repo-bare?     (constantly false)
                    git/is-worktree-clean?     (constantly true)
                    paths/resolve-worktree-path (constantly default-target-path)
                    fs/cwd                     (constantly repo-root)
                    cmd-new/exit!              (fn [code] (reset! exit-code code))]
        (let [output (with-out-str
                       (cmd-new/run {:opts {:branch-name default-branch}}))]
          (is (nil? @exit-code) "Should not exit on happy path")
          (is (str/includes? output "Worktree ready at:"))
          (is (str/includes? output default-target-path)))))))

;;; Stash restore on pre-check exit

(deftest test-run-stash-restore-on-pre-check-exit
  (testing "run restores stash before exiting when pre-check fires after stashing"
    (let [apply-stash-calls (atom [])
          exit-code         (atom nil)]
      (with-redefs [git/get-repo-root           (constantly repo-root)
                    paths/validate-branch-name  (constantly {:valid? true})
                    git/is-main-repo-bare?      (constantly false)
                    git/is-worktree-clean?      (constantly false)
                    tui/handle-dirty-state      (constantly :stash)
                    git/stash-changes           (fn [& _] "stash-hash-123")
                    fs/cwd                      (constantly repo-root)
                    paths/resolve-worktree-path (constantly default-target-path)
                    git/find-worktree-by-branch (fn [b] {:branch b :path "/other/wt"})
                    git/get-current-branch      (constantly default-branch)
                    git/apply-stash             (fn [hash cwd]
                                                  (swap! apply-stash-calls conj {:hash hash :cwd cwd}))
                    worktree/exit!              (fn [code]
                                                  (reset! exit-code code)
                                                  (throw (ex-info "test-exit" {:code code})))]
        (try
          (cmd-new/run {:opts {:branch-name default-branch}})
          (catch clojure.lang.ExceptionInfo e
            (when-not (= "test-exit" (ex-message e))
              (throw e))))
        (is (= 1 @exit-code))
        (is (pos? (count @apply-stash-calls)) "apply-stash should be called at least once")
        (is (= "stash-hash-123" (:hash (first @apply-stash-calls))))))))
