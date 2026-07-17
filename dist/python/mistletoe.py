# Mistletoe v0.1 -- Python implementation

from abc import ABC, abstractmethod
from enum import Enum, auto
from random import Random
from time import time
from typing import Callable, ClassVar, Union
import dataclasses
import inspect
import io
import math
import re
import sys
import threading

# ruff: noqa: TRY003  -- long exception messages seem preferable here to a very large number of exception types.
# ruff: noqa: SLF001  -- as a project convention, we use '_' to mean private to the implementation (not part of the API).
# ruff: noqa: N802    -- as a project convention, API functions start with an uppercase letter.
# ruff: noqa: PLC1901 -- explicit comparisons to "" are felt to be more readable than implicit boolean conversion


_Arg = Union["_Jump", "_Expr", int, float, str]
_Args = tuple[_Arg, ...]

Value = Union[int, float, str, bool, "_Tuple", "_FunctionDef", "_SpecialKeys"]
Values = list[Value]

_DestFn     = Callable[[Value],None]
_EvalFn     = Callable[["_Scope",_DestFn],None]
_EvalListFn = Callable[["_Scope"],list[Value]]
_EvalOneFn  = Callable[["_Scope"],Value]


def _foreach(vals: Values, dest: _DestFn) -> None:
    for v in vals:
        dest(v)


class _Scope:
    def __init__(self, parent: "_Scope", allowed_jumps: int|None = None):
        self._parent = parent
        self._vars: dict[Value,Values] = {}
        if allowed_jumps is not None:
            self._allowed_jumps = allowed_jumps
        elif parent is not None:
            self._allowed_jumps = parent.allowed_jumps
        else:
            raise ValueError()

    @property
    def parent(self): return self._parent

    @property
    def allowed_jumps(self): return self._allowed_jumps

    def get(self, k: Value) -> Values:
        v = self.get_optional(k)
        if v is None:
            raise MTException(f"Variable “{_to_str(k)}” ({_type_name(k)}-named) is not defined")
        return v

    def get_optional(self, k: Value) -> Values | None:
        v = self._vars.get(k)
        if v is None and self._parent is not None:
            v = self.parent.get_optional(k)
        return v

    def put(self, k: Value, v: Values) -> None:
        self._vars[k] = v

    def __str__(self) -> str:
        return f"{self._vars} -> {self._parent}"


@dataclasses.dataclass
class _Jump:
    name: str
    bit: int
    Throw: ClassVar["_Jump"]
    def __str__(self): return self.name


class _ExecState(threading.local):
    _instance = None

    @staticmethod
    def instance():
        if _ExecState._instance is None:
            _ExecState._instance = _ExecState()
        return _ExecState._instance

    def __init__(self):
        self._global_scope = _Scope(None, 0)

    def reset(self) -> None:
        self.random = Random()
        self.current_jump: _Jump | None = None
        self.exception: Values|None = None

    @property
    def global_scope(self) -> _Scope:
        return self._global_scope

    def end_jump(self, jump: _Jump) -> bool:
        if self.current_jump == jump:
            self.current_jump = None
            return True
        return False


class _Expr(ABC):
    _EMPTY: "_Expr"
    _UNKNOWN_LENGTH = -1

    def __init__(self, name: str, static_length: int = _UNKNOWN_LENGTH):
        self._name = name
        self._static_length = static_length

    def staticLength(self) -> list[Value]: return [self._static_length]

    @abstractmethod
    def _eval(self, scope: _Scope, dest: _DestFn) -> None:
        raise NotImplementedError

    def _eval_mutable_list(self, scope: _Scope) -> Values:
        return self._eval_list(scope)

    def _eval_list(self, scope: _Scope) -> Values:
        vals: Values = []
        self._eval(scope, vals.append)
        return vals

    def _eval_one(self, scope: _Scope, default: Value|None = None):
        result: Value|None = None

        def tmp_dest(obj: Value):
            nonlocal result
            if result is not None:
                raise MTException(f"Expected one value but found multiple: {_repr(result)}, {_repr(obj)}, ...")
            result = obj

        self._eval(scope, tmp_dest)
        if result is None:
            if default is not None:
                result = default
            else:
                raise MTException("Expected one value but found zero")

        return result


    def _map(self, name: str, op: Callable[[Value],Value]) -> "_Expr":
        return _call_expr(
            name,
            lambda scope, dest: self._eval(scope, lambda v: dest(op(v))),
            static_length = self._static_length
        )

    def _infix(self, name: str, args: _Args, op: Callable[[Value,Value],Value]) -> "_Expr":
        right = _args_to_expr(args)

        llen = self._static_length
        rlen = right._static_length

        if llen == 0 or rlen == 0:
            return _Expr._EMPTY

        elif llen == 1:
            def e(scope, dest):
                x = self._eval_one(scope)
                right._eval(scope, lambda y: dest(op(x, y)))
            return _call_expr(name, e, static_length = rlen)

        elif rlen == 1:
            def e(scope, dest):
                y = right._eval_one(scope)
                self._eval(scope, lambda x: dest(op(x, y)))
            return _call_expr(name, e, static_length = llen)

        else:
            def e(scope, dest):
                operands1 = self._eval_list(scope)
                operands2 = right._eval_list(scope)

                if len(operands1) == 0 or len(operands2) == 0:
                    return

                it1 = iter(operands1)
                it2 = iter(operands2)
                longest = max(len(operands1), len(operands2))

                for _i in range(longest):
                    try:
                        v1 = next(it1)
                    except StopIteration:
                        it1 = iter(operands1)
                        v1 = next(it1)

                    try:
                        v2 = next(it2)
                    except StopIteration:
                        it2 = iter(operands2)
                        v2 = next(it2)

                    dest(op(v1, v2))

            ul = _Expr._UNKNOWN_LENGTH
            return _call_expr(name, e, static_length = ul if llen is ul or rlen is ul else max(llen, rlen))


    def As(self, *key_args):
        return _assign("As", _args_to_expr(key_args), self, lambda scope, k, v: scope.put(k, v))

    def Call(self, *args):  return _call("Call", self, _args_to_expr(args))
    def Apply(self, *args): return _call("Apply", _args_to_expr(args), self)

    def Eq(self, *args): return self._infix("Eq", args, lambda x, y: x == y)
    def Ne(self, *args): return self._infix("Ne", args, lambda x, y: x != y)
    def Le(self, *args): return self._infix("Le", args, _compare("Le", lambda x, y: x <= y))
    def Lt(self, *args): return self._infix("Lt", args, _compare("Lt", lambda x, y: x < y))
    def Gt(self, *args): return self._infix("Gt", args, _compare("Gt", lambda x, y: x > y))
    def Ge(self, *args): return self._infix("Ge", args, _compare("Ge", lambda x, y: x >= y))
    def IsNaN(self):  return self._map("IsNaN", lambda v: math.isnan(_to_float(v)))

    def In(self, *args):
        right_expr = _args_to_expr(args)
        def e(scope, dest):
            right_set = set()
            right_expr._eval(scope, right_set.add)
            self._eval(scope, lambda v: dest(v in right_set))
        return _call_expr("In", e, static_length = self._static_length)

    def NotIn(self, *args): return self.In(*args).Not()

    def Plus(self, *args):  return self._infix("Plus",  args, _int_or_float(lambda x, y: x + y,       lambda x, y: x + y))
    def Minus(self, *args): return self._infix("Minus", args, _int_or_float(lambda x, y: x - y,       lambda x, y: x - y))
    def Times(self, *args): return self._infix("Times", args, _int_or_float(lambda x, y: x * y,       lambda x, y: x * y))
    def Div(self, *args):   return self._infix("Div",   args, _int_or_float(lambda x, y: x // y,      lambda x, y: x / y))
    def Mod(self, *args):   return self._infix("Mod",   args, _int_or_float(lambda x, y: x % y,       lambda x, y: x % y))
    def Pow(self, *args):   return self._infix("Pow",   args, _int_or_float(lambda x, y: int(x ** y), lambda x, y: x ** y))

    def Neg(self): return self._map("Neg", lambda v: -v     if type(v) is int else -_to_float(v))
    def Abs(self): return self._map("Abs", lambda v: abs(v) if type(v) is int else abs(_to_float(v)))

    def And(self, *args): return self._infix("And", args, lambda x, y: _to_bool(x) and _to_bool(y))
    def Or(self, *args):  return self._infix("Or",  args, lambda x, y: _to_bool(x) or _to_bool(y))
    def Not(self): return self._map("Not", lambda v: not _to_bool(v))

    def Int(self):   return self._map("Int", lambda v: _to_int(v))
    def Float(self): return self._map("Float", lambda v: _to_float(v))
    def Str(self):   return self._map("Str", lambda v: _to_str(v))

    def Tuple(self): return _single_expr("Tuple", lambda scope: _Tuple(self._eval_list(scope)))
    def Flatten(self):
        return _call_expr("Flatten",
                          lambda scope, dest: self._eval(scope, lambda v: _foreach(v, dest) if type(v) is _Tuple else dest(v)))

    def At(self, *args):
        index_expr = _args_to_expr(args)
        def e(scope, dest):
            src = self._eval_list(scope)
            length = len(src)
            def d(v):
                idx = _to_int(v)
                if idx < -length or idx >= length:
                    raise MTException(f"At: index {idx} is invalid for a {length}-value sequence")
                dest(src[idx])

            index_expr._eval(scope, d)
        return _call_expr("At", e, static_length = index_expr._static_length)

    def Len(self):
        def e(scope):
            count = 0
            def d(_v):
                nonlocal count
                count += 1
            self._eval(scope, d)
            return count

        return _single_expr("Len", e)

    def Sum(self):
        def e(scope):
            ints_only = True
            int_sum = 0
            float_sum = 0.0

            def d(v):
                nonlocal ints_only, int_sum, float_sum
                if ints_only:
                    if isinstance(v, int):
                        int_sum += v
                        return
                    float_sum = float(int_sum)
                    ints_only = False
                float_sum += _to_float(v)

            self._eval(scope, d)
            return int_sum if ints_only else float_sum

        return _single_expr("Sum", e)

    def All(self):
        def e(scope):
            def d(v):
                if not _to_bool(v):
                    raise _StopIteration
            try:
                self._eval(scope, d)
            except _StopIteration:
                return False
            else:
                return True

        return _single_expr("All", e)

    def Any(self):
        def e(scope):
            def d(v):
                if _to_bool(v):
                    raise _StopIteration
            try:
                self._eval(scope, d)
            except _StopIteration:
                return True
            else:
                return False

        return _single_expr("Any", e)

    def SLen(self):
        return self._map("SLen", lambda v: len(_to_str(v)))

    def Cat(self, *args):
        return self._infix("Cat", args, lambda x, y: _to_str(x) + _to_str(y))

    def Join(self, *args):
        delim_expr = _args_to_expr(args)
        def e(scope):
            sb = io.StringIO()
            delim = _to_str(delim_expr._eval_one(scope, ""))
            if delim == "":
                self._eval(scope, lambda v: sb.write(_to_str(v)))
            else:
                use_delim = False
                def d(v):
                    nonlocal use_delim
                    if use_delim:
                        sb.write(delim)
                    else:
                        use_delim = True
                    sb.write(_to_str(v))
                self._eval(scope, d)
            return sb.getvalue()

        return _single_expr("Join", e)

    def To(self, *args):
        end_expr = _args_to_expr(args)
        def e(scope, dest):
            for i in range(_to_int(self._eval_one(scope)), end_expr._eval_one(scope)):
                dest(i)
        return _call_expr("To", e)

    def Ord(self):
        def e(scope, dest):
            def d(v):
                for ch in _to_str(v):
                    dest(ord(ch))
            self._eval(scope, d)
        return _call_expr("Ord", e)

    def Chr(self):
        def m(v):
            cp = _to_int(v)
            if 0 <= cp <= 0x10ffff and not (0xd800 <= cp <= 0xdfff):
                return chr(cp)
            raise MTException(f"Chr: {hex(cp)} ({cp}) is not a valid code point")
        return self._map("Chr", m)

    def RandShuffle(self):
        def e(scope):
            src = self._eval_mutable_list(scope)
            _ExecState.instance().random.shuffle(src)
            return src
        return _list_expr("RandShuffle", e, static_length = self._static_length)

    def RandChoose(self, *args):
        n_expr = _args_to_expr(args)
        def e(scope, dest):
            pop = self._eval_mutable_list(scope)
            n = _to_int(n_expr._eval_one(scope, 1))
            if n < 0 or n > len(pop):
                raise MTException(f"RandChoose: cannot choose {n} value(s) from a {len(pop)}-value sequence")
            for x in _ExecState.instance().random.sample(pop, n):
                dest(x)
        return _call_expr("RandChoose", e)

    def With(self, *args):
        body = _args_to_expr(args)
        def e(outer_scope, dest):
            inner_scope = _Scope(outer_scope)
            inner_scope.put(_SpecialKeys.IT, self._eval_list(outer_scope))
            body._eval(inner_scope, dest)
        return _call_expr("With", e, static_length = body._static_length)


    def ForEach(self, *args):
        body = _args_to_expr(args)
        def e(outer_scope, dest):
            state = _ExecState.instance()

            inner_scope = _Scope(outer_scope, outer_scope.allowed_jumps | Break.bit | Continue.bit)
            idx = 0
            item_var = [None]
            idx_var = [0]
            inner_scope.put(_SpecialKeys.ITEM, item_var)
            inner_scope.put(_SpecialKeys.IDX, idx_var)

            def d(v):
                nonlocal idx
                item_var[0] = v
                body._eval(inner_scope, dest)
                state.end_jump(Continue)
                if state.current_jump is not None:
                    raise _StopIteration
                idx += 1
                idx_var[0] = idx

            try:
                self._eval(outer_scope, d)
            except _StopIteration:
                pass
            state.end_jump(Break)

        llen = self._static_length
        rlen = body._static_length
        ul = _Expr._UNKNOWN_LENGTH
        return _call_expr("ForEach", e, static_length = ul if llen is ul or rlen is ul else llen * rlen)

    def Eval(self):
        return _call_expr(
            "Eval",
            lambda outer_scope, dest: self._eval(outer_scope, lambda v:
                _Parser(f"({_to_str(v)})").parse_list(_Parser.SyntaxMode.NORMAL)._eval(_Scope(outer_scope), dest)))

    def Print(self):
        return _void_expr("Print", lambda scope: self._eval(scope, lambda v: print(v, end="")))

    def Done(self):
        return _void_expr("Done", lambda scope: self._eval(scope, lambda _v: None))

    def Note(self, *_args):
        return self


def _call_expr(name: str, fn: _EvalFn, **kwargs) -> _Expr:
    class E(_Expr):
        def _eval(self, scope, dest): fn(scope, dest)
    return E(name, **kwargs)

def _void_expr(name: str, fn: Callable[[_Scope],None]) -> _Expr:
    class E(_Expr):
        def _eval(self, scope, _dest): fn(scope)
    return E(name, static_length = 0)

def _general_list_expr(name: str, fn: _EvalListFn, mutable_list_fn: _EvalListFn, **kwargs) -> _Expr:
    class E(_Expr):
        def _eval(self, scope, dest):        _foreach(fn(scope), dest)
        def _eval_list(self, scope):         return fn(scope)
        def _eval_mutable_list(self, scope): return mutable_list_fn(scope)
        def Len(self):
            return _single_expr("Len", lambda scope: len(fn(scope)))
    return E(name, **kwargs)

def _list_expr(name: str, fn: _EvalListFn, **kwargs) -> _Expr:
    return _general_list_expr(name, fn, fn, **kwargs)

def _stored_list_expr(name: str, key: Value, **kwargs) -> _Expr:
    return _general_list_expr(name,
                              lambda scope: scope.get(key),
                              lambda scope: list(scope.get(key)),
                              **kwargs)

def _single_expr(name, fn: _EvalOneFn) -> _Expr:
    class E(_Expr):
        def _eval_one(self, scope, _def_v = None): return fn(scope)
        def _eval_list(self, scope):              return [fn(scope)]
        def _eval(self, scope, dest):             dest(fn(scope))
    return E(name, static_length = 1)


class _VExpr(_Expr):
    def __init__(self, key_expr: _Expr):
        super().__init__("V")
        self._key_expr = key_expr

    #override
    def _eval(self, scope: _Scope, dest: _DestFn):
        self._key_expr._eval(scope, lambda k: _foreach(scope.get(k), dest))


    def Pop(self) -> _Expr:
        def e(scope, dest):
            def d(k):
                value_list = scope.get(k)
                if len(value_list) > 0:
                    dest(value_list.pop(0))
            self._key_expr._eval(scope, d)

        return _call_expr("Pop", e)


    def Set(self, *value_args: _Arg) -> _Expr:
        return _assign("Set", self._key_expr, _args_to_expr(value_args),
                       lambda scope, k, v: scope.put(k, v))

    def SetGlobal(self, *value_args: _Arg) -> _Expr:
        return _assign("SetGlobal", self._key_expr, _args_to_expr(value_args),
                       lambda _scope, k, v: _ExecState.instance().global_scope.put(k, v))

    def Update(self, *value_args: _Arg) -> _Expr:
        def mutator(scope, k, v):
            scope.get(k)[:] = v
        return _assign("Update", self._key_expr, _args_to_expr(value_args), mutator)

    def Append(self, *value_args: _Arg) -> _Expr:
        return _assign("Append", self._key_expr, _args_to_expr(value_args),
                       lambda scope, k, v: scope.get(k).extend(v))


class _ThenExpr(_Expr):
    def __init__(self, condition: _Expr, then_body: _Expr):
        super().__init__("If-Then")
        self._condition = condition
        self._then_body = then_body
        self._else_body = _Expr._EMPTY

    def _body(self, scope: _Scope):
        return self._then_body if _to_bool(self._condition._eval_one(scope)) else self._else_body

    #override
    def _eval(self, scope: _Scope, dest: _DestFn) -> None:
        return self._body(scope)._eval(scope, dest)

    #override
    def _eval_list(self, scope: _Scope) -> Values:
        return self._body(scope)._eval_list(scope)

    #override
    def _eval_one(self, scope: _Scope, default: Value|None = None) -> Value:
        return self._body(scope)._eval_one(scope, default)

    def Else(self, *args) -> _Expr:
        self._else_body = _args_to_expr(args)

        then_len = self._then_body._static_length
        else_len = self._else_body._static_length
        if then_len == else_len != _Expr._UNKNOWN_LENGTH:
            self._static_length = then_len

        return self


class _IfExpr:  # Does not derive from _Expr
    def __init__(self, condition: _Expr):
        self._condition = condition

    def Then(self, *then_body: _Arg) -> _ThenExpr:
        return _ThenExpr(self._condition, _args_to_expr(then_body))


class _WhileExpr:  # Does not derive from _Expr
    def __init__(self, condition: _Expr):
        self._condition = condition

    def Do(self, *args) -> _Expr:
        body = _args_to_expr(args)
        def e(outer_scope, dest):
            state = _ExecState.instance()
            inner_scope = _Scope(outer_scope, outer_scope.allowed_jumps | Break.bit | Continue.bit)
            idx = 0
            idx_var = [0]
            inner_scope.put(_SpecialKeys.IDX, idx_var)

            while _to_bool(self._condition._eval_one(inner_scope)):
                body._eval(inner_scope, dest)
                state.end_jump(Continue)
                if state.current_jump is not None:
                    break
                idx += 1
                idx_var[0] = idx

            state.end_jump(Break)

        return _call_expr("While-Do", e)


class _TryExpr(_Expr):
    def __init__(self, try_body: _Expr):
        super().__init__("Try")
        self._try_body = try_body
        self._finally_body = _Expr._EMPTY
        self._catches: list[tuple[_Expr,_Expr]] = []

    def _add_catch(self, catch_pattern: _Expr, catch_body: _Expr):
        self._catches.append((catch_pattern, catch_body))

    def Catch(self, *pattern_args: _Arg) -> "_CatchExpr":
        return _CatchExpr(self, _args_to_expr(pattern_args))

    def Finally(self, *args: _Arg) -> _Expr:
        self._finally_body = _args_to_expr(args)
        return _list_expr("Try", self._eval_list)

    #override
    def _eval(self, scope: _Scope, dest: _DestFn) -> None:
        for v in self._eval_list(scope):
            dest(v)

    #override
    def _eval_list(self, scope: _Scope) -> Values:
        if len(self._catches) == 0 and self._finally_body is None:
            raise MTException("Try: expected at least one Catch and/or a Finally")

        values = self._try_body._eval_mutable_list(scope)
        state = _ExecState.instance()

        if state.end_jump(_Jump.Throw):
            scope.put(_SpecialKeys.EX, state.exception)
            scope.put(_SpecialKeys.PARTIAL, values)
            for pattern_expr, catch_body in self._catches:
                pattern = pattern_expr._eval_list(scope)
                if len(state.exception) >= len(pattern) and all(m == p for m, p in zip(state.exception, pattern, strict=False)):
                    state.exception = None
                    values = catch_body._eval_list(scope)
                    break
            else:
                state.current_jump = _Jump.Throw
                values = []

        self._finally_body._eval(scope, values.append)
        return values


class _CatchExpr:  # Does not derive from _Expr
    def __init__(self, try_expr: _TryExpr, catch_pattern: _Expr):
        self._try_expr = try_expr
        self._catch_pattern = catch_pattern

    def Do(self, *args: _Arg) -> _TryExpr:
        self._try_expr._add_catch(self._catch_pattern, _args_to_expr(args))
        return self._try_expr


@dataclasses.dataclass
class _FunctionDef:
    parent_scope: _Scope
    body: _Expr
    def __str__(self): return "Fn(...)"


class _Tuple(tuple):
    def __str__(self):
        return "T(" + ", ".join(_repr(v) for v in self) + ")"


class MTException(Exception):  # noqa: N818 -- the name "MTException" is intended to be consistent across different languages.
    pass


class _SpecialKeys(Enum):
    VAL = auto()
    ARG = auto()
    THIS_FN = auto()
    IDX = auto()
    ITEM = auto()
    IT = auto()
    EX = auto()
    PARTIAL = auto()


TRUE    = _single_expr("TRUE",  lambda _scope: True)
FALSE   = _single_expr("FALSE", lambda _scope: False)

Val     = _stored_list_expr("Val",     _SpecialKeys.VAL)
Arg     = _stored_list_expr("Arg",     _SpecialKeys.ARG)
ThisFn  = _stored_list_expr("ThisFn",  _SpecialKeys.THIS_FN, static_length = 1)
Idx     = _stored_list_expr("Idx",     _SpecialKeys.IDX,     static_length = 1)
Item    = _stored_list_expr("Item",    _SpecialKeys.ITEM,    static_length = 1)
It      = _stored_list_expr("It",      _SpecialKeys.IT)
Ex      = _stored_list_expr("Ex",      _SpecialKeys.EX)
Partial = _stored_list_expr("Partial", _SpecialKeys.PARTIAL)

Time = _single_expr("Time", lambda _scope: time())
Rand = _single_expr("Rand", lambda _scope: _ExecState.instance().random.random())
Pi   = _single_expr("Pi",   lambda _scope: math.pi)
Inf  = _single_expr("Inf",  lambda _scope: math.inf)
MNaN = _single_expr("MNaN", lambda _scope: math.nan)

Break       = _Jump("Break",    0x01)
Continue    = _Jump("Continue", 0x02)
Return      = _Jump("Return",   0x04)
_Jump.Throw = _Jump("<exception>", 0x08)

def MT(*args: _Arg) -> Values:
    state = _ExecState.instance()
    state.reset()
    values = _args_to_expr(args)._eval_list(_Scope(state.global_scope))
    if state.end_jump(_Jump.Throw):
        raise MTException(f"Unhandled exception: {_repr(state.exception)}")
    return values

def Scope(*args: _Arg) -> _Expr:
    body = _args_to_expr(args)
    return _call_expr(
        "Scope",
        lambda outer_scope, dest: body._eval(_Scope(outer_scope), dest),
        static_length = body._static_length
    )

def C(*args: _Arg) -> _Expr: return _args_to_expr(args)

def I(*args: _Arg) -> _Expr: # noqa: E743 -- "I" is the required function name
    return _args_to_expr(args)._map("I", lambda v: _to_int(v))

def F(*args: _Arg) -> _Expr: return _args_to_expr(args)._map("F", lambda v: _to_float(v))
def S(*args: _Arg) -> _Expr: return _args_to_expr(args)._map("S", lambda v: _to_str(v))

def Fn(*args: _Arg) -> _Expr:
    body = _args_to_expr(args)
    return _single_expr("Fn", lambda scope: _FunctionDef(scope, body))

def T(*args: _Arg) -> _Expr:
    body = _args_to_expr(args)
    return _single_expr("T", lambda scope: _Tuple(body._eval_list(scope)))

def V(*args: _Arg)     -> _VExpr:     return _VExpr(_args_to_expr(args))
def If(*args: _Arg)    -> _IfExpr:    return _IfExpr(_args_to_expr(args))
def While(*args: _Arg) -> _WhileExpr: return _WhileExpr(_args_to_expr(args))
def Try(*args: _Arg)   -> _TryExpr:   return _TryExpr(_args_to_expr(args))

def Throw(*args: _Arg) -> _Expr:
    ex_expr = _args_to_expr(args)
    def e(scope):
        state = _ExecState.instance()
        state.exception = ex_expr._eval_list(scope)
        state.current_jump = _Jump.Throw
    return _void_expr("Throw", e)

def Note(*_args: str) -> _Expr:
    return _Expr._EMPTY


# --- Internal functions ---


class _StopIteration(Exception):  # noqa: N818 -- this exception doesn't represent an error
    pass

_Expr._EMPTY = _void_expr("<empty>", lambda _scope: None)

def _arg_args_to_expr(arg: _Arg) -> _Expr:
    if isinstance(arg, _Jump):
        def jexpr(scope):
            if (scope.allowed_jumps & arg.bit) == 0:
                raise MTException(f"{arg.name} not permitted here")
            _ExecState.instance().current_jump = arg
        return _void_expr(arg.name, jexpr)

    elif isinstance(arg, _Expr):
        return arg

    elif isinstance(arg, float):
        if arg % 1.0 == 0.0:  # noqa: RUF069 -- exact floating point equality is desired here
            v = int(arg)
            return _single_expr("<int-literal>", lambda _scope: v)
        else:
            return _single_expr("<float-literal>", lambda _scope: arg)

    elif type(arg) is int:  # Excludes 'bool', which inherits from 'int'
        return _single_expr("<int-literal>", lambda _scope: arg)

    elif isinstance(arg, str):
        return _Parser(_escape_str(arg, "%")).parse_string_literal()

    else:
        raise MTException(f"Unsupported host language value {type(arg)} “{arg}”")

def _args_to_expr(args: _Args) -> _Expr:
    n = len(args)
    if n == 0:
        return _Expr._EMPTY
    elif n == 1:
        return _arg_args_to_expr(args[0])
    else:
        state = _ExecState.instance()
        expr_list = [_arg_args_to_expr(a) for a in args]

        static_len = 0
        for expr in expr_list:
            sl = expr._static_length
            if sl is _Expr._UNKNOWN_LENGTH:
                static_len = _Expr._UNKNOWN_LENGTH
                break
            static_len += sl

        def e(scope, dest):
            for expr in expr_list:
                expr._eval(scope, dest)
                if state.current_jump is not None:
                    break
        return _call_expr("<expr-list>", e, static_length = static_len)


def _to_int(v: Value) -> int:
    if type(v) is int:
        return v

    elif type(v) is float:
        return int(v)

    elif type(v) is str:
        try:
            return int(float(v))
        except ValueError:
            pass

    raise MTException(f"Cannot convert {_type_name(v)} “{_repr(v)}” to Int")


def _to_float(v: Value) -> float:
    if type(v) is float:
        return v

    elif type(v) is int:
        return float(v)

    elif type(v) is str:
        try:
            return float(v)
        except ValueError:
            pass

    raise MTException(f"Cannot convert {_type_name(v)} “{_repr(v)}” to Float")


def _to_bool(v: Value) -> bool:
    if type(v) is bool:
        return v

    raise MTException(f"Expected Bool but found {_type_name(v)} “{_repr(v)}”")


def _to_str(value: Value) -> str:
    # ruff: disable[E701] -- alignment for readability
    if type(value) is str:     return value
    if type(value) is bool:    return "TRUE" if value else "FALSE"
    if type(value) is float:
        if value == math.inf:  return "Inf"
        if value == -math.inf: return "Inf.Neg()"
        if math.isnan(value):  return "MNaN"
    return str(value)
    # ruff: enable[E701]


def _escape_str(s: str, percent: str) -> str:
    sb = io.StringIO()
    sb.write('"')
    for ch in s:
        # ruff: disable[E701] -- alignment for readability
        if   ch == "\t": sb.write("\\t")
        elif ch == "\n": sb.write("\\n")
        elif ch == "\r": sb.write("\\r")
        elif ch == "\"": sb.write("\\\"")
        elif ch == "\\": sb.write("\\\\")
        elif ch == "%":  sb.write(percent)
        else:            sb.write(ch)
        # ruff: enable[E701]
    sb.write('"')
    return sb.getvalue()


def _repr(v: Value) -> str:
    if isinstance(v, str):
        return _escape_str(v, "%%")

    elif isinstance(v, list):
        return "[" + ", ".join(_repr(v1) for v1 in v) + "]"

    else:
        return _to_str(v)


def _type_name(v: Value) -> str:
    return {int: "Int", float: "Float", str: "Str", bool: "Bool",
            _Tuple: "Tuple", _FunctionDef: "Fn", _SpecialKeys: "SpecialKey"}[type(v)]


def _compare(name: str, op: Callable[[Value,Value],Value]) -> Callable[[Value,Value],Value]:
    def chk_op(x: Value, y: Value):
        tx = type(x)
        ty = type(y)
        if (tx in [int, float] and ty in [int, float]) or (tx is str and ty is str):
            return op(x, y)
        raise MTException(f"{name}: {_type_name(x)} “{_repr(x)}” and {_type_name(y)} “{_repr(y)}” are not comparable")

    return chk_op


def _int_or_float(int_fn: Callable[[int,int],int|float],
                  float_fn: Callable[[float,float],int|float]) -> Callable[[Value,Value],Value]:

    def f(x: Value, y: Value) -> Value:
        try:
            if type(x) is int and type(y) is int:
                return int_fn(x, y)
            else:
                return float_fn(_to_float(x), _to_float(y))
        except ZeroDivisionError as e:
            raise MTException("Division by zero") from e

    return f


def _assign(name: str, key_expr: _Expr, value_expr: _Expr,
            mutator: Callable[[_Scope,Value,Values],None]) -> _Expr:
    def e(outer_scope):
        keys = key_expr._eval_list(outer_scope)
        if len(keys) == 0:
            raise MTException("Cannot assign to an empty list of variables")

        old_values = []
        for k in keys:
            v = outer_scope.get_optional(k)
            if v is not None:
                old_values.extend(v)

        inner_scope = _Scope(outer_scope)
        inner_scope.put(_SpecialKeys.VAL, old_values)
        new_values = value_expr._eval_list(inner_scope)

        value_it = iter(new_values)
        n_keys = len(keys)
        for i in range(n_keys - 1):
            value_list = []
            try:
                value_list.append(next(value_it))
            except StopIteration:
                pass
            mutator(outer_scope, keys[i], value_list)
        mutator(outer_scope, keys[n_keys - 1], list(value_it))

    return _void_expr(name, e)


def _call(name: str, fn_expr: _Expr, fn_arg_expr: _Expr) -> _Expr:
    def e(outer_scope, dest):
        fn_args = fn_arg_expr._eval_list(outer_scope)

        def d(fn_ident):
            if isinstance(fn_ident, _FunctionDef):
                fn = fn_ident
            elif (lookup := outer_scope.get(fn_ident)) is not None and len(lookup) == 1 and isinstance(lookup[0], _FunctionDef):
                fn = lookup[0]
            else:
                raise MTException(f"Function “{fn_ident}” ({_type_name(fn_ident)}-named) is not defined")

            inner_scope = _Scope(fn.parent_scope, Return.bit)
            inner_scope.put(_SpecialKeys.ARG, fn_args)
            inner_scope.put(_SpecialKeys.THIS_FN, [fn])
            fn.body._eval(inner_scope, dest)
            _ExecState.instance().end_jump(Return)

        fn_expr._eval(outer_scope, d)

    return _call_expr(name, e)


class _Parser:
    @dataclasses.dataclass
    class TT:
        label: str
        pattern: re.Pattern = dataclasses.field(compare = False)

    OPEN       = TT("“(”",          re.compile(r"\("))
    CLOSE      = TT("“)”",          re.compile(r"\)"))
    COMMA      = TT("“,”",          re.compile(r","))
    DOT        = TT("“.”",          re.compile(r"\."))
    HEX_NUMBER = TT("<hex-number>", re.compile(r"[-+]?0x[0-9a-fA-F]+"))
    NUMBER     = TT("<number>",     re.compile(r"[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?"))
    STR_DOUBLE = TT("“\"”",         re.compile(r"\""))
    STR_SINGLE = TT("“'”",          re.compile(r"'"))
    KEYWORD    = TT("<keyword>",    re.compile(r"[A-Z][a-zA-Z0-9]*"))
    IDENTIFIER = TT("<identifier>", re.compile(r"[a-z][a-zA-Z0-9_]*"))

    class SyntaxMode(Enum):
        NORMAL = auto()
        STRING_EMBEDDED = auto()

    ESCAPE = re.compile(r"[%DH(]|x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{6}")
    ERROR_CONTEXT = 15

    def __init__(self, s: str):
        self._input = s
        self._token = ""
        self._pos = 0

    def parse_string_literal(self) -> _Expr:
        components: list[_Expr] = []
        sb = io.StringIO()

        def add_last_str():
            nonlocal sb
            s = sb.getvalue()
            if len(s) > 0:
                sb = io.StringIO()
                components.append(_single_expr("<str-component>", lambda _scope: s))

        input_len = len(self._input)
        start_pos = self._pos

        delim_ch = self._input[self._pos]
        self._pos += 1

        while self._pos < input_len:
            ch = self._input[self._pos]
            if ch == delim_ch:
                self._pos += 1
                add_last_str()

                def combine(scope):
                    final_sb = io.StringIO()
                    for c in components:
                        c._eval(scope, lambda v: final_sb.write(_to_str(v)))
                    return final_sb.getvalue()

                return _single_expr("<str-literal>", combine)

            elif ch == "\\":
                escape_ch = self._input[self._pos+1 : self._pos+2]

                # ruff: disable[E701] -- alignment for readability
                if escape_ch == "t": sb.write("\t")
                elif escape_ch == "n": sb.write("\n")
                elif escape_ch == "r": sb.write("\r")
                elif escape_ch == "\"": sb.write("\"")
                elif escape_ch == "\\": sb.write("\\")
                else:
                    raise self.parse_err("illegal \\-escape")
                self._pos += 2
                # ruff: enable[E701]

            elif ch == "%":
                escape_match = _Parser.ESCAPE.match(self._input, self._pos + 1)
                if escape_match is None:
                    raise self.parse_err("illegal %-escape (use “%%” for a literal “%”)")

                escape = escape_match.group()
                self._pos += 1 + len(escape)
                escape_ch = escape[0]

                # ruff: disable[E701] -- alignment for readability
                if   escape_ch == "%": sb.write("%")
                elif escape_ch == "'": sb.write("'")
                elif escape_ch == "D": sb.write("$")
                elif escape_ch == "H": sb.write("#")
                elif escape_ch in "xuU":
                    sb.write(chr(int(escape[1:], base=16)))
                elif escape_ch == "(":
                    add_last_str()
                    self._pos -= 1
                    components.append(self.parse_list(_Parser.SyntaxMode.STRING_EMBEDDED))
                else:
                    raise AssertionError
                # ruff: enable[E701]

            elif ch == '"':
                raise self.parse_err("illegal char “\"” (use \\\")")

            elif ch in "$#":
                raise self.parse_err(f"illegal char “{ch}” (use a %-escape)")

            else:
                sb.write(ch)
                self._pos += 1

        self._pos = start_pos
        raise self.parse_err("unclosed string literal")

    def parse_operator(self, expr, syntax_mode):
        try:
            o = getattr(expr, self._token)
            if callable(o):
                n_params = len(inspect.signature(o).parameters)
                if n_params == 0:
                    self.next(_Parser.OPEN)
                    self.next(_Parser.CLOSE)
                    return o()
                elif n_params == 1:
                    return o(self.parse_list(syntax_mode))
                else:
                    raise AssertionError
            else:
                return o
        except AttributeError as e:
            self._pos -= len(self._token)
            raise self.parse_err(f"unexpected symbol “{self._token}”") from e

    def parse_list(self, syntax_mode: SyntaxMode) -> _Expr:
        str_tt = _Parser.STR_DOUBLE if syntax_mode is _Parser.SyntaxMode.NORMAL else _Parser.STR_SINGLE
        identifier_tt = None if syntax_mode is _Parser.SyntaxMode.NORMAL else _Parser.IDENTIFIER

        args: list[_Arg] = []
        self.next(_Parser.OPEN)
        tt = self.next(_Parser.HEX_NUMBER, _Parser.NUMBER, str_tt, identifier_tt, _Parser.KEYWORD, _Parser.CLOSE)
        while tt != _Parser.CLOSE:
            if tt == _Parser.NUMBER:
                args.append(float(self._token))
            elif tt == _Parser.HEX_NUMBER:
                args.append(int(self._token, base=16))
            elif tt in [_Parser.STR_DOUBLE, _Parser.STR_SINGLE]:
                self._pos -= len(self._token)
                args.append(self.parse_string_literal())

            elif tt in [_Parser.IDENTIFIER, _Parser.KEYWORD]:
                if tt == _Parser.IDENTIFIER:
                    def fn(var_name):
                        return lambda _s: var_name
                    expr = _VExpr(_single_expr("<identifier>", fn(self._token)))
                else:
                    expr = self.parse_operator(sys.modules[__name__], syntax_mode)

                if isinstance(expr, _Jump):
                    args.append(expr)
                else:
                    while self.next(_Parser.DOT, _Parser.COMMA, _Parser.CLOSE) is _Parser.DOT:
                        self.next(_Parser.KEYWORD)
                        expr = self.parse_operator(expr, syntax_mode)

                    self._pos -= len(self._token)
                    if not isinstance(expr, _Expr):
                        raise self.parse_err("incomplete expression")

                    args.append(expr)
            else:
                raise AssertionError

            tt = self.next(_Parser.COMMA, _Parser.CLOSE)
            if tt == _Parser.COMMA:
                tt = self.next(_Parser.HEX_NUMBER, _Parser.NUMBER, str_tt, identifier_tt, _Parser.KEYWORD)

        return _args_to_expr(tuple(args))


    def next(self, *expected_tokens) -> TT:
        input_len = len(self._input)
        while self._pos < input_len and self._input[self._pos].isspace():
            self._pos += 1

        for token_type in expected_tokens:
            if token_type is None:
                continue
            if match := token_type.pattern.match(self._input, self._pos):
                self._token = match.group()
                self._pos += len(self._token)
                return token_type

        if len(expected_tokens) == 1:
            raise self.parse_err(f"expected {expected_tokens[0].label}")
        else:
            expected = ", ".join(tt.label for tt in expected_tokens if tt is not None)
            raise self.parse_err(f"expected one of {expected}")


    def parse_err(self, msg: str) -> MTException:

        p = self._pos
        inp = self._input
        if self._input[0] == "(" and self._input[-1] == ")":
            p -= 1
            inp = inp[1:-1]

        line = 1 + sum(ch == "\n" for ch in inp[:p])
        col = p - inp.rfind("\n", 0, p)

        pre_cutoff = max(0, p - _Parser.ERROR_CONTEXT)
        pre_context = ("..." if pre_cutoff > 0 else "") + inp[pre_cutoff:p]

        post_cutoff = min(len(inp), p + _Parser.ERROR_CONTEXT)
        post_context = inp[p:post_cutoff] + ("..." if post_cutoff < len(inp) else "")

        return MTException(f"Parsing error at line {line}, col {col}: {msg} in “{pre_context}▶{post_context}”")
