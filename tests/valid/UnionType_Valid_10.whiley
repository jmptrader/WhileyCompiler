import whiley.lang.System

type tenup is int

type msg1 is {tenup op, [int] data}

type msg2 is {int index}

type msgType is msg1 | msg2

function f(msgType m) -> ASCII.string:
    return Any.toString(m)

method main(System.Console sys) -> void:
    msg1 x = {op: 11, data: []}
    sys.out.println(f(x))
