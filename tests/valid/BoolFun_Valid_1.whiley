import whiley.lang.System

function f(bool b) -> ASCII.string:
    return Any.toString(b)

method main(System.Console sys) -> void:
    bool x = true
    sys.out.println(f(x))
    x = false
    sys.out.println(f(x))
