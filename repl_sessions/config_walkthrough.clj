(ns repl-sessions.config-walkthrough
  (:require
   [lambdaisland.config :as config]))

(def config
  (config/create {:prefix "my-app"}))

(config/get config :http/port)
