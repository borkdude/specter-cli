# specter CLI

An example CLI that uses a [SCI](https://github.com/borkdude/sci) configuration
that enables runtime usage of
[specter](https://github.com/redplanetlabs/specter) in native images.

## Usage

Currently the CLI accepts one argument, `-q`, that represents a body of a
function with one argument, `q`. The public vars from `com.rpl.specter` are
referred automatically.

``` clojure
$ $ echo '{:a {:aa 1} :b {:ba -1 :bb 2}}' | ./specter -q '(transform [MAP-VALS MAP-VALS] inc q)'
{:a {:aa 2}, :b {:ba 0, :bb 3}}
```

## Implementation details

The configuration contains a number of interesting tweaks:

- A patch for the `com.rpl.specter.impl/closed-source` function which originally
  uses `clojure.core/eval`, which doesn't work in native images. This is
  replaced by `sci/eval-form`.
- A patch for the `com.rpl.specter/path` macro which originally uses
  `clojure.core/intern` as a side effect during macro-expansion. This is
  replaced by `sci.core/intern`.

For GraalVM we need to provide a reflection config that contains
`clojure.lang.Equiv/equals` since the `path` macro generates code which does
interop on this class and this macro is expanded at runtime inside the SCI
context. We also need to add `clojure.lang.Util` to SCI's `:classes` option for
the same reason.

## Build

Set `GRAALVM_HOME` to your local GraalVM install.
Run `script/build`.

## License

Copyright © 2021 Michiel Borkent

This Specter CLI is licensed under Apache License v2.0, same as Specter. See
LICENSE.
