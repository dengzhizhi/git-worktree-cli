(ns commons.config
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn config-path
  "Returns the path to the worktree config EDN file.
   Respects $XDG_CONFIG_HOME, falling back to ~/.config."
  []
  (let [xdg  (System/getenv "XDG_CONFIG_HOME")
        base (if (and xdg (not (str/blank? xdg)))
               xdg
               (str (System/getProperty "user.home") "/.config"))]
    (str base "/worktree/config.edn")))

(defn read-config
  "Reads the EDN config file. Returns {} if the file does not exist."
  []
  (let [path (config-path)]
    (if (fs/exists? path)
      (edn/read-string (slurp path))
      {})))

(defn write-config
  "Writes a Clojure map to the config file as EDN. Creates parent dirs."
  [data]
  (let [path (config-path)]
    (fs/create-dirs (fs/parent path))
    (spit path (pr-str data))))

(defn get-editor
  "Returns the configured editor, or nil if not set."
  []
  (get (read-config) :editor))

(defn get-editor-path
  "Returns the configured executable override for editor, or nil if not set.
   Editor is a string; paths are stored as keywords internally."
  [editor]
  (get-in (read-config) [:editor-paths (keyword editor)]))

(defn set-editor-path!
  "Stores an executable path override for editor in config.
   Editor is a string; stored as a keyword key."
  [editor path]
  (write-config (assoc-in (read-config) [:editor-paths (keyword editor)] path)))

(defn set-editor!
  "Sets the editor in the config."
  [editor]
  (write-config (assoc (read-config) :editor editor)))

(defn get-worktreepath
  "Returns the configured worktree base path, or nil if not set."
  []
  (get (read-config) :worktreepath))

(defn set-worktreepath!
  "Sets the worktree base path. Expands ~/ and absolutizes the path."
  [path]
  (let [resolved (cond
                   (str/starts-with? path "~/")
                   (str (System/getProperty "user.home") (subs path 1))

                   (str/starts-with? path "~")
                   (str (System/getProperty "user.home") "/" (subs path 1))

                   :else
                   (str (fs/absolutize path)))]
    (write-config (assoc (read-config) :worktreepath resolved))))

(defn clear-worktreepath!
  "Removes the worktreepath key from the config."
  []
  (write-config (dissoc (read-config) :worktreepath)))
