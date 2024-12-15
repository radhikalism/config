# config

<!-- badges -->
[![cljdoc badge](https://cljdoc.org/badge/com.lambdaisland/config)](https://cljdoc.org/d/com.lambdaisland/config) [![Clojars Project](https://img.shields.io/clojars/v/com.lambdaisland/config.svg)](https://clojars.org/com.lambdaisland/config)
<!-- /badges -->

Configuration library

## Features

<!-- installation -->
## Installation

To use the latest release, add the following to your `deps.edn` ([Clojure CLI](https://clojure.org/guides/deps_and_cli))

```
com.lambdaisland/config {:mvn/version "0.0.0"}
```

or add the following to your `project.clj` ([Leiningen](https://leiningen.org/))

```
[com.lambdaisland/config "0.0.0"]
```
<!-- /installation -->

## Rationale

`lambdaisland/config` implements a pattern we've settled on through doing lots
of different Clojure projects, about how to handle configuration, in particular
the kind of things that differ between environments (dev, test, staging, prod),
and that you might want to set or override on multiple levels.

It is highly flexible in how you configure the sources that are checked, but has
opinionated defaults, and allows plugging in custom "providers", for instance
for checking a secret store like Hashicorp Vault or Google Secret Manager.

## Usage

### Illustrative Example

```clj
(def config
  (config/create {:prefix "my-app"
                  :env :dev}))

(config/get config :http/port) ;;=> 8080
```

This will check, in order, until it's found a value:

- The `$HTTP__PORT` environment variable
- `config.local.edn` in the JVM's CWD
- `$XDG_CONFIG_HOME/my-app.edn`
- The `my-app.http.port` Java system property (`System/getProperty`)
- `my-app/dev.edn` on the CLASSPATH (e.g. under `resources`)
- `my-app/config.edn` on the CLASSPATH

To know where a given setting came from, use `config/source`

```clj
(config/source config :http/port)
;;=> `"$HTTP__PORT environment variable"
```

### Detailed Usage

`lambdaisland/config` is based on the `ConfigProvider` protocol.

```clj
(defprotocol ConfigProvider
  (-value [this k])
  (-source [this k])
  (-reload [this]))
```

The result of `config/create` is a three-element map. The "environment" name, a
sequence of config providers, and an atom which acts as a cache of values
already accessed.

```clj
{:env :prod
 :providers [,,,<implement ConfigProvider protocol>,,,]
 :values (atom {:http/port {:val 8080 :source "$HTTP__PORT environment variable}})
```

`:env` can be explicitly passed in, otherwise we check the `PREFIX_ENV` (e.g.
`MY_APP_ENV`) env var, or the `prefix.env` System property (`my-app.env`). If
neither is set and the `CI` env var is true, then we default to `:test`, if not
we fall back to `:dev`.

`create` can take a number of other options besides `:env` and `:prefix`.

- `:env-vars false` - Don't check environemnt variables
- `:prefix-env true` - Include the prefix when checking environment variables,
  e.g. `MY_APP__HTTP__PORT` instead of `HTTP_PORT` 
- `:java-system-props false` - Don't check Java system properties
- `:local-config false` - Don't check `config.local.edn`
- `:xdg-config false` - Don't check `XDG_CONFIG_HOME` (default: `~/.config`)

If you want a different precedence order, or want to inject your own
`ConfigProvider`, then don't use `create`, but construct your own config map as
you see fit.

### Idiomatic Usage

The general idea is:

- Create a `resources/<prefix>/config.edn` file with your base config. Whenever
  adding a new config key it's a good idea to add a sensible default here
- Create a `resources/<prefix>/<env>.edn` for each environment. This way you can
  check in sensible `dev.edn` settings, and separate `prod.edn` settings.
- During development, create `config.local.edn` so you can easily change
  settings locally. Add `*.local.*` to `.gitignore`.
- When deploying/running in specific settings, use whatever makes most sense in
  that context to customize the deployment. If you're running through some cloud
  or lambda provider env vars might be your main option. If you control the
  `clojure` or `java` command line invocation, then system props might be handy.
  If you want a file on the filesystem you can look at and tweak, then the
  `XDG_CONFIG_HOME` convention is useful.
  
At the end of the boot process it can be a good idea to print/log
`config/sources` or `config/entries` (perhaps with
`clojure.pprint/print-table`), so when you go in to debug things you have a
record of where various configuration items are coming from.

<!-- opencollective -->
## Lambda Island Open Source

Thank you! config is made possible thanks to our generous backers. [Become a
backer on OpenCollective](https://opencollective.com/lambda-island) so that we
can continue to make config better.

<a href="https://opencollective.com/lambda-island">
<img src="https://opencollective.com/lambda-island/organizations.svg?avatarHeight=46&width=800&button=false">
<img src="https://opencollective.com/lambda-island/individuals.svg?avatarHeight=46&width=800&button=false">
</a>
<img align="left" src="https://github.com/lambdaisland/open-source/raw/master/artwork/lighthouse_readme.png">

&nbsp;

config is part of a growing collection of quality Clojure libraries created and maintained
by the fine folks at [Gaiwan](https://gaiwan.co).

Pay it forward by [becoming a backer on our OpenCollective](http://opencollective.com/lambda-island),
so that we continue to enjoy a thriving Clojure ecosystem.

You can find an overview of all our different projects at [lambdaisland/open-source](https://github.com/lambdaisland/open-source).

&nbsp;

&nbsp;
<!-- /opencollective -->

<!-- contributing -->
## Contributing

We warmly welcome patches to config. Please keep in mind the following:

- adhere to the [LambdaIsland Clojure Style Guide](https://nextjournal.com/lambdaisland/clojure-style-guide)
- write patches that solve a problem 
- start by stating the problem, then supply a minimal solution `*`
- by contributing you agree to license your contributions as MPL 2.0
- don't break the contract with downstream consumers `**`
- don't break the tests

We would very much appreciate it if you also

- update the CHANGELOG and README
- add tests for new functionality

We recommend opening an issue first, before opening a pull request. That way we
can make sure we agree what the problem is, and discuss how best to solve it.
This is especially true if you add new dependencies, or significantly increase
the API surface. In cases like these we need to decide if these changes are in
line with the project's goals.

`*` This goes for features too, a feature needs to solve a problem. State the problem it solves first, only then move on to solving it.

`**` Projects that have a version that starts with `0.` may still see breaking changes, although we also consider the level of community adoption. The more widespread a project is, the less likely we're willing to introduce breakage. See [LambdaIsland-flavored Versioning](https://github.com/lambdaisland/open-source#lambdaisland-flavored-versioning) for more info.
<!-- /contributing -->

<!-- license -->
## License

Copyright &copy; 2024 Arne Brasseur and Contributors

Licensed under the term of the Mozilla Public License 2.0, see LICENSE.
<!-- /license -->
