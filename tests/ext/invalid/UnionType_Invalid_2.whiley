define pos as int where $ > 0
define poslist as [pos]
define plt as pos | poslist

define nat as int where $ >= 0
define natlist as [nat]
define nlt as nat | natlist

nlt g(int y) requires y >= 0:
    return y

plt f(int x) requires x >= 0:
    return g(x)

void System::main([string] args):
    debug str(f(0))