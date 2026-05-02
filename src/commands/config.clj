(ns commands.config
  (:require [clojure.string :as str]
            [commons.config :as config]
            [commons.editors :as editors]))

(def exit! (fn [code] (System/exit code)))

(def ^:private valid-keys #{"editor" "worktreepath"})
(def ^:private clearable-keys #{"worktreepath"})

(defn set!
  "config set <key> <value>"
  [{:keys [opts]}]
  (let [{:keys [key value]} opts]
    (cond
      (nil? key)
      (do (println "Usage: worktree config set <key> <value>")
          (println "Valid keys:" (str/join ", " (sort valid-keys)))
          (exit! 1))

      (nil? value)
      (do (println (str "Error: missing value for key '" key "'"))
          (exit! 1))

      (not (contains? valid-keys (name key)))
      (do (println (str "Error: unknown config key '" (name key) "'"))
          (println "Valid keys:" (str/join ", " (sort valid-keys)))
          (exit! 1))

      (= (name key) "editor")
      (if (contains? editors/valid-editors (name value))
        (do (config/set-editor! (name value))
            (println (str "Set editor to '" (name value) "'")))
        (do (println (str "Error: unsupported editor '" (name value) "'"))
            (println (str "Valid editors: " (str/join ", " (sort editors/valid-editors))))
            (exit! 1)))

      (= (name key) "worktreepath")
      (do (config/set-worktreepath! (name value))
          (println (str "Set worktreepath to '" (config/get-worktreepath) "'"))))))

(defn get!
  "config get <key>"
  [{:keys [opts]}]
  (let [{:keys [key]} opts]
    (cond
      (nil? key)
      (do (println "Usage: worktree config get <key>")
          (println "Valid keys:" (str/join ", " (sort valid-keys)))
          (exit! 1))

      (not (contains? valid-keys (name key)))
      (do (println (str "Error: unknown config key '" (name key) "'"))
          (exit! 1))

      (= (name key) "editor")
      (println (config/get-editor))

      (= (name key) "worktreepath")
      (let [v (config/get-worktreepath)]
        (if v
          (println v)
          (println "(not set)"))))))

(defn clear!
  "config clear <key>"
  [{:keys [opts]}]
  (let [{:keys [key]} opts]
    (cond
      (nil? key)
      (do (println "Usage: worktree config clear <key>")
          (println "Clearable keys:" (str/join ", " (sort clearable-keys)))
          (exit! 1))

      (not (contains? clearable-keys (name key)))
      (do (println (str "Error: key '" (name key) "' cannot be cleared (or unknown)"))
          (exit! 1))

      (= (name key) "worktreepath")
      (do (config/clear-worktreepath!)
          (println "Cleared worktreepath")))))

(defn show-path!
  "config path — print path to config file"
  [_]
  (println (config/config-path)))

(defn set-editor-path!
  "config set-editor-path <editor> <path>"
  [{:keys [opts]}]
  (let [{:keys [editor path]} opts]
    (cond
      (nil? editor)
      (do (println "Usage: worktree config set-editor-path <editor> <path>")
          (exit! 1))

      (not (contains? editors/valid-editors (name editor)))
      (do (println (str "Error: unsupported editor '" (name editor) "'"))
          (println (str "Valid editors: " (str/join ", " (sort editors/valid-editors))))
          (exit! 1))

      (nil? path)
      (do (println (str "Error: missing path for editor '" (name editor) "'"))
          (exit! 1))

      :else
      (do (config/set-editor-path! (name editor) (name path))
          (println (str "Set " (name editor) " path to '" (name path) "'"))))))

(defn get-editor-path!
  "config get-editor-path <editor>"
  [{:keys [opts]}]
  (let [{:keys [editor]} opts]
    (cond
      (nil? editor)
      (do (println "Usage: worktree config get-editor-path <editor>")
          (exit! 1))

      (not (contains? editors/valid-editors (name editor)))
      (do (println (str "Error: unsupported editor '" (name editor) "'"))
          (println (str "Valid editors: " (str/join ", " (sort editors/valid-editors))))
          (exit! 1))

      :else
      (let [override (config/get-editor-path (name editor))
            result   (or override (get editors/default-executables (name editor)))]
        (println result)))))
