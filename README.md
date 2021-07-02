# specter-sci

A [SCI](https://github.com/borkdude/sci) configuration that enables runtime usage of
[specter](https://github.com/redplanetlabs/specter) in native images.

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

## Run

``` clojure
$ ./specter_sci
{:a {:aa 2}, :b {:ba 0, :bb 3}}
[3 3 18 6 12]
[2 1 3 6 10 4 8]
{:a [1 2 3]}
{:a {:b {}}}
{}
[0 2 2 4 4 5 6 7]
[0 1 :a :b :c :d :e 4 5 6 7 8 9]
[[1 :a :b] (1 2 :a :b) [:c :a :b]]
[2 1 2 6 7 4 1 2]
[10]
[0 1 2 3 10 5 8 7 6 9 4 11 12 13 14 15]
[[1 2 3 4 5 6 :c :d] [7 0 -1] [8 8 :c :d] []]
[{:a 1, :b 3} {:a -8, :b -10} {:a 14, :b 10} {:a 3}]
{:a 11, :b 3}
[{:a 2, :c [2 3], :d 4} {:a 4, :c [1 11 0]} {:a -1, :c [1 1 1], :d 2}]
```

## License

[Unlicense](https://unlicense.org/).


