import whiley.lang.System

function append(ASCII.string input) -> {int}:
    {int} rs = {}
    for i in 0 .. |input|:
        rs = {(int) input[i]} + rs
    return rs

method main(System.Console sys) -> void:
    {int} xs = append("abcdefghijklmnopqrstuvwxyz")
    sys.out.println(Any.toString(xs))
