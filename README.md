# Specter CLI

An native CLI that uses [Specter](https://github.com/redplanetlabs/specter) to transform EDN from stdin.

This CLI is compiled with GraalVM `native-image` and executed using [SCI](https://github.com/borkdude/sci).

## Download

You can get pre-built binaries for macOS and linux under
[Releases](https://github.com/borkdude/specter-cli/releases). If your OS is not
yet supported, you can try [building](#build) the CLI yourself.

## Usage

Currently the CLI accepts one argument, `-e`, that represents a Clojure
expression. The public vars from `com.rpl.specter` are referred
automatically. The question mark variable is bound to EDN from stdin.

``` clojure
$ echo '{:a {:aa 1} :b {:ba -1 :bb 2}}' | ./specter -e '(transform [MAP-VALS MAP-VALS] inc ?)'
{:a {:aa 2}, :b {:ba 0, :bb 3}}
```

## Status

For now this is mostly a proof of concept to see if I could get Specter working
within the contect of SCI and `native-image` (see
[this](https://github.com/borkdude/sci/issues/370) issue).

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
