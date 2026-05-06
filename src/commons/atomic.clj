(ns commons.atomic
  (:require [babashka.fs :as fs]
            [babashka.process :as p]))

(defn with-atomic-rollback
  "Runs operation-fn with a register-rollback! function. On success returns the result.
   On exception, runs registered rollback actions in LIFO order, then re-throws.
   When no rollback actions were registered, prints 'Cleaning up...' / 'Done.' instead
   of 'Rolling back...' / 'Rollback complete.'"
  [operation-fn]
  (let [rollback-actions (atom [])]
    (try
      (let [result (operation-fn (fn [rollback-thunk]
                                   (swap! rollback-actions conj rollback-thunk)))]
        result)
      (catch Exception e
        (if (empty? @rollback-actions)
          (println "Cleaning up...")
          (println "Rolling back changes..."))
        (doseq [action (reverse @rollback-actions)]
          (try (action)
               (catch Exception re
                 (println "Rollback step failed:" (.getMessage re)))))
        (if (empty? @rollback-actions)
          (println "Done.")
          (println "Rollback complete."))
        (throw e)))))

(defn create-worktree
  "Creates a git worktree and registers a rollback to remove it on failure.
   - create-branch? true → git worktree add -b <branch> <path>
   - create-branch? false → git worktree add <path> <branch>"
  [register-rollback! path branch create-branch?]
  (let [result (if create-branch?
                 @(p/process {:err :string} "git" "worktree" "add" "-b" branch path)
                 @(p/process {:err :string} "git" "worktree" "add" path branch))]
    (when (not (zero? (:exit result)))
      (throw (ex-info (str "Failed to create worktree at " path)
                      {:exit (:exit result) :err (:err result)})))
    (register-rollback!
      (fn []
        (println (str "Rolling back: removing worktree at " path "..."))
        (p/shell {:continue true} (str "git worktree remove --force " path))
        (when (fs/exists? path)
          (fs/delete-tree path))))))
