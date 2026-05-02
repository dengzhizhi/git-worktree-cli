(ns commands.open
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [commons.config :as config]
            [commons.editors :as editors]
            [commons.git :as git]
            [commons.tui :as tui]))

(def exit! (fn [code] (System/exit code)))

(defn- resolve-executable
  "Returns the executable string for editor, using override if provided.
   Returns nil if editor is not in the default map (should not happen after validation)."
  [editor override]
  (or override (get editors/default-executables editor)))

(defn- open-in-editor
  "Opens path in the given editor using safe varargs shell invocation."
  [path editor]
  (let [override   (config/get-editor-path editor)
        executable (resolve-executable editor override)]
    ;; For absolute paths, verify the executable exists before attempting launch.
    (when (and (str/starts-with? executable "/")
               (not (fs/exists? executable)))
      (println (str "Error: editor executable not found: '" executable "'"))
      (println (str "Override with: wt config set-editor-path " editor " /path/to/" editor))
      (exit! 1))
    (println (str "Running: " executable " " path))
    (try
      (p/shell {:dir path} executable path)
      (catch Exception e
        (println (str "Error: failed to launch '" editor "': " (ex-message e)))
        (println (str "Override executable with: wt config set-editor-path " editor " /path/to/" editor))
        (exit! 1)))))

(defn run
  "Open a worktree in the configured editor."
  [{:keys [opts]}]
  (let [{:keys [path-or-branch editor]} opts
        editor (or editor (config/get-editor))]
    ;; Early exit: no editor at all
    (when (nil? editor)
      (println "No editor configured. Run: wt config set-editor <editor>")
      (exit! 1))
    ;; Early exit: invalid editor name
    (when-not (contains? editors/valid-editors editor)
      (println (str "Error: unsupported editor '" editor "'"))
      (println (str "Valid editors: " (str/join ", " (sort editors/valid-editors))))
      (exit! 1))
    (let [worktrees (git/get-worktrees)]
      (cond
        ;; Specific target given — try by path first, then branch
        path-or-branch
        (let [target (or (git/find-worktree-by-path (name path-or-branch))
                         (git/find-worktree-by-branch (name path-or-branch)))]
          (if target
            (if (fs/exists? (:path target))
              (open-in-editor (:path target) editor)
              (do (println (str "Error: worktree path does not exist: " (:path target)))
                  (exit! 1)))
            (do (println (str "Error: no worktree found for '" (name path-or-branch) "'"))
                (exit! 1))))

        ;; No target — interactive select
        :else
        (if-let [selected (tui/select-worktree worktrees {:prompt "Select worktree to open: "})]
          (if (fs/exists? (:path selected))
            (open-in-editor (:path selected) editor)
            (do (println (str "Error: worktree path does not exist: " (:path selected)))
                (exit! 1)))
          (println "No worktree selected."))))))
