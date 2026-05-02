(ns commons.editors)

;; Canonical set — single source of truth for all editor validation.
;; Imported by commands.open, commands.config, and anywhere validation is needed.
(def valid-editors
  #{"idea" "webstorm" "pycharm" "vscode" "cursor" "vimr" "vim" "nvim" "sublime" "zed"})

;; Default CLI executables per editor name.
(def default-executables
  {"idea"     "idea"
   "webstorm" "webstorm"
   "pycharm"  "pycharm"
   "vscode"   "code"
   "cursor"   "cursor"
   "vimr"     "vimr"
   "vim"      "vim"
   "nvim"     "nvim"
   "sublime"  "subl"
   "zed"      "zed"})
