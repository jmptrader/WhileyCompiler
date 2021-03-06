constant ADD is 1
constant SUB is 2
constant MUL is 3
constant DIV is 4

type binop is ({int op, expr left, expr right} r) where r.op == ADD || r.op == SUB || r.op == MUL || r.op == DIV

type expr is int | binop

function f(expr e) -> expr:
    return e

method main() -> expr:
    expr e1 = {op: ADD, left: {op: 0, left: 2, right: 2}, right: 2}
    return f(e1)
