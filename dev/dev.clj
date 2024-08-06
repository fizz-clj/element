(ns dev
  (:require [cljs.repl.browser :as b]
            [cider.piggieback :as p]
            [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.api :as shadow]))

(comment ;; with shadow
  (do
    (server/start!)
    (shadow/watch :dev)
    (shadow/repl :dev))
  1)

(defn run-plain-repl
  []
  (p/cljs-repl (b/repl-env)))

(comment
  ;; In conjure, use ConjurePiggieback and the following
  ;; :ConjurePiggieback (cljs.repl.browser/repl-env)
  (p/cljs-repl (b/repl-env))

  1
  (js/alert "hi")
  :cljs/quit)

