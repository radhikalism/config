(ns repl-sessions.config-walkthrough
  (:require
   [lambdaisland.config :as config]))

(def config
  (config/create {:prefix "my-app"
                  :prefix-env true}))

(:values config)

()

(config/source config :bar/baz)
