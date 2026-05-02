(ns commands.merge
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [commons.git :as git]))

(defn run
  "Merge a worktree's branch into the current branch."
  [{:keys [opts]}]
  (let [{:keys [branch-name auto-commit message]} opts]
    (when (nil? branch-name)
      (println "Usage: worktree merge <branchName>")
      (System/exit 1))

    (let [branch-name  (name branch-name)
          current      (git/get-current-branch)
          target-wt    (git/find-worktree-by-branch branch-name)]

      (when (nil? target-wt)
        (println (str "Error: no worktree found for branch '" branch-name "'"))
        (println "The branch must have an active worktree to merge.")
        (System/exit 1))

      (when (= current branch-name)
        (println (str "Error: cannot merge branch into itself (current branch: " current ")"))
        (System/exit 1))

      ;; Check if target worktree is dirty
      (let [target-path (:path target-wt)
            is-clean    (git/is-worktree-clean? target-path)]
        (when (not is-clean)
          (if auto-commit
            (let [msg (or message (str "Auto-commit before merge into " current))]
              (println (str "Auto-committing changes in '" branch-name "'..."))
              @(p/process "git" "-C" target-path "add" ".")
              (let [commit-result @(p/process {:err :string}
                                              "git" "-C" target-path "commit" "-m" msg)]
                (when (not (zero? (:exit commit-result)))
                  (println "Error: auto-commit failed")
                  (println (:err commit-result))
                  (System/exit 1)))
              (println "Auto-commit complete."))
            (do
              (println (str "Error: worktree '" branch-name "' has uncommitted changes."))
              (println "Use --auto-commit to commit automatically, or commit manually first.")
              (System/exit 1)))))

      ;; Perform the merge
      (println (str "Merging '" branch-name "' into '" current "'..."))
      (let [result @(p/process {:err :string} "git" "merge" branch-name)]
        (if (zero? (:exit result))
          (do
            (println (str "Successfully merged '" branch-name "' into '" current "'"))
            (println (str "Note: worktree at " (:path target-wt) " is preserved.")))
          (do
            (println "Merge failed:")
            (println (:err result))
            (System/exit 1)))))))
