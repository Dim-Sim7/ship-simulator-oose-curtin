// Mistletoe v0.1 -- Java callback implementation
package edu.curtin.app.message;

import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.lang.reflect.*;

@SuppressWarnings({
    // Adopts internal naming conventions necessitated by cross-language development.
    "PMD.ClassNamingConventions",
    "PMD.MethodNamingConventions",
    "PMD.FieldNamingConventions",
    "PMD.FormalParameterNamingConventions",

    // We use "_e"
    "PMD.LocalVariableNamingConventions",

    // Adopts a /*pkg*/ convention instead
    "PMD.CommentDefaultAccessModifier",

    // We trade-off having a very large set of public-facing methods to make it easy to transplant the entire implementation.
    "PMD.ExcessivePublicCount",
})
public class Mistletoe
{
    public static class MTException extends RuntimeException
    {
        private MTException(String msg) { super(msg); }
        private MTException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static abstract class _Expr
    {
        /*pkg*/ static final long UNKNOWN_LENGTH = -1L;
        /*pkg*/ static final _Expr _EMPTY = _voidExpr("<empty>", _scope -> {});

        private long _staticLength = UNKNOWN_LENGTH;

        /*pkg*/ _Expr(String name) { /* this._name = name; */ }

        /*pkg*/ long getStaticLength() { return _staticLength; }
        /*pkg*/ _Expr setStaticLength(long staticLen)
        {
            _staticLength = staticLen;
            return this;
        }
        public List<Object> staticLength()  // Expr property
        {
            return List.of(_staticLength);
        }

        /*pkg*/ abstract void _eval(_Scope scope, _DestFn dest);

        /*pkg*/ List<Object> _evalMutableList(_Scope scope) { return _evalList(scope); }
        /*pkg*/ List<Object> _evalList(_Scope scope)
        {
            var list = new ArrayList<Object>();
            _eval(scope, list::add);
            return list;
        }
        /*pkg*/ Object _evalOne(_Scope scope) { return _evalOne(scope, null); }
        /*pkg*/ Object _evalOne(_Scope scope, Object defaultValue)
        {
            Object[] singleValue = {null};
            _eval(scope, obj -> {
                if(singleValue[0] != null)
                {
                    throw new MTException(String.format("Expected one value but found multiple: %s, %s, ...",
                        _repr(singleValue[0]), _repr(obj)));
                }
                singleValue[0] = obj;
            });
            Object v = singleValue[0];
            if(v == null)
            {
                if(defaultValue != null) { v = defaultValue; }
                else { throw new MTException("Expected one value but found zero"); }
            }
            return v;
        }

        private _Expr _infix(String name, Object[] args, BinaryOperator<Object> op)
        {
            var right = _argsToExpr(args);
            long llen = _staticLength;
            long rlen = right._staticLength;

            if(llen == 0 || rlen == 0)
            {
                return _EMPTY;
            }
            else if(llen == 1)
            {
                return _callExpr(name, (scope, dest) ->
                {
                    var x = _evalOne(scope);
                    right._eval(scope, y -> dest.accept(op.apply(x, y)));
                })
                .setStaticLength(rlen);
            }
            else if(rlen == 1)
            {
                return _callExpr(name, (scope, dest) ->
                {
                    var y = right._evalOne(scope);
                    _eval(scope, x -> dest.accept(op.apply(x, y)));
                })
                .setStaticLength(llen);
            }
            else
            {
                return _callExpr(name, (scope, dest) ->
                {
                    var operands1 = _evalList(scope);
                    var operands2 = right._evalList(scope);

                    int len1 = operands1.size();
                    int len2 = operands2.size();
                    if(len1 == 0 || len2 == 0) { return; }

                    var it1 = operands1.iterator();
                    var it2 = operands2.iterator();
                    int len = Math.max(len1, len2);

                    for(int i = 0; i < len; i++)
                    {
                        if(!it1.hasNext())
                        {
                            it1 = operands1.iterator();
                        }
                        if(!it2.hasNext())
                        {
                            it2 = operands2.iterator();
                        }
                        dest.accept(op.apply(it1.next(), it2.next()));
                    }
                })
                .setStaticLength((llen == UNKNOWN_LENGTH || rlen == UNKNOWN_LENGTH) ? UNKNOWN_LENGTH
                                                                                    : Math.max(llen, rlen));
            }
        }

        private _Expr _map(String name, UnaryOperator<Object> op)
        {
            return _callExpr(name, (scope, dest) -> _eval(scope, v -> dest.accept(op.apply(v)))).setStaticLength(_staticLength);
        }

        public _Expr As(Object... args)
        {
            return _assign("As", _argsToExpr(args), this, (scope, k, v) -> scope.put(k, v));
        }

        public _Expr Call(Object... args)  { return _call("Call", this, _argsToExpr(args)); }
        public _Expr Apply(Object... args) { return _call("Apply", _argsToExpr(args), this); }

        public _Expr Eq(Object... args) { return _infix("Eq", args, (x, y) -> _eq(x, y)); }
        public _Expr Ne(Object... args) { return _infix("Ne", args, (x, y) -> !_eq(x, y)); }
        public _Expr Lt(Object... args) { return _infix("Lt", args, _compare("Lt", (x, y) -> x <  y, c -> c < 0)); }
        public _Expr Le(Object... args) { return _infix("Le", args, _compare("Le", (x, y) -> x <= y, c -> c <= 0)); }
        public _Expr Gt(Object... args) { return _infix("Gt", args, _compare("Gt", (x, y) -> x >  y, c -> c > 0)); }
        public _Expr Ge(Object... args) { return _infix("Ge", args, _compare("Ge", (x, y) -> x >= y, c -> c >= 0)); }
        public _Expr IsNaN()            { return _map("IsNaN", v -> Double.isNaN(_toFloat(v))); }

        public _Expr In(Object... args)
        {
            var rightExpr = _argsToExpr(args);
            return _callExpr("In", (scope, dest) ->
            {
                var set = new HashSet<>();
                rightExpr._eval(scope, set::add);
                _eval(scope, v -> dest.accept(set.contains(v)));
            })
            .setStaticLength(_staticLength);
        }

        public _Expr NotIn(Object... args) { return In(args).Not(); }

        public _Expr Plus(Object... args)  { return _infix("Plus",  args, _intOrFloat((x, y) -> x + y, (x, y) -> x + y)); }
        public _Expr Minus(Object... args) { return _infix("Minus", args, _intOrFloat((x, y) -> x - y, (x, y) -> x - y)); }
        public _Expr Times(Object... args) { return _infix("Times", args, _intOrFloat((x, y) -> x * y, (x, y) -> x * y)); }
        public _Expr Div(Object... args)   { return _infix("Div",   args, _intOrFloat((x, y) -> x / _nonZero(y),
                                                                                      (x, y) -> x / _nonZero(y))); }
        public _Expr Mod(Object... args)   { return _infix("Mod",   args, _intOrFloat((x, y) -> x % _nonZero(y),
                                                                                      (x, y) -> x % _nonZero(y))); }
        public _Expr Pow(Object... args)   { return _infix("Pow",   args, _intOrFloat((x, y) -> (long)Math.pow(x, y),
                                                                                      (x, y) -> Math.pow(x, y))); }
        public _Expr Neg() { return _map("Neg", v -> (v instanceof Long) ? (Object)(-(Long)v)        : (Object)(-_toFloat(v))); }
        public _Expr Abs() { return _map("Abs", v -> (v instanceof Long) ? (Object)Math.abs((Long)v) : (Object)Math.abs(_toFloat(v))); }

        public _Expr And(Object... args)   { return _infix("And", args, (x, y) -> _toBool(x) && _toBool(y)); }
        public _Expr Or(Object... args)    { return _infix("Or",  args, (x, y) -> _toBool(x) || _toBool(y)); }
        public _Expr Not() { return _map("Not", v -> !_toBool(v)); }

        public _Expr Int()   { return _map("Int",   v -> _toInt(v)); }
        public _Expr Float() { return _map("Float", v -> _toFloat(v)); }
        public _Expr Str()   { return _map("Str",   v -> _toStr(v)); }
        public _Expr Tuple() { return _singleExpr("Tuple", scope -> new _Tuple(_evalList(scope))); }
        public _Expr Flatten()
        {
            return _callExpr("Flatten", (scope, dest) -> _eval(scope, v ->
            {
                if(v instanceof _Tuple)
                {
                    ((_Tuple)v).forEach(dest);
                }
                else
                {
                    dest.accept(v);
                }
            }));
        }

        public _Expr At(Object... args)
        {
            var indexExpr = _argsToExpr(args);
            return _callExpr("At", (scope, dest) ->
            {
                var src = _evalList(scope);
                int len = src.size();
                indexExpr._eval(scope, v ->
                {
                    int idx = (int)_toInt(v);
                    if(idx < -len || idx >= len)
                    {
                        throw new MTException(String.format("At: index %d is invalid for a %d-value sequence", idx, len));
                    }
                    if(idx < 0) { idx += len; }
                    dest.accept(src.get(idx));
                });
            })
            .setStaticLength(indexExpr._staticLength);
        }

        public _Expr Len()
        {
            return _singleExpr("Len", scope ->
            {
                long[] count = {0L};
                _eval(scope, v -> count[0]++);
                return count[0];
            });
        }

        public _Expr Sum()
        {
            class Summer implements _DestFn
            {
                boolean intsOnly = true;
                long intSum = 0;
                double floatSum = 0.0;

                @Override
                public void accept(Object v)
                {
                    if(intsOnly)
                    {
                        if(v instanceof Long)
                        {
                            intSum += (Long)v;
                            return;
                        }
                        floatSum = (double)intSum; // NOPMD -- helpful to be explicit about types here.
                        intsOnly = false;
                    }
                    floatSum += _toFloat(v);
                }
            }

            return _singleExpr("Sum", scope ->
            {
                var summer = new Summer();
                _eval(scope, summer);
                return summer.intsOnly ? (Object)summer.intSum : (Object)summer.floatSum;
            });
        }

        public _Expr All()
        {
            return _singleExpr("All", scope ->
            {
                try
                {
                    _eval(scope, v -> { if(!_toBool(v)) { throw _StopIteration.EX; }});
                    return true;
                }
                catch(_StopIteration e) { return false; }
            });
        }

        public _Expr Any()
        {
            return _singleExpr("Any", scope ->
            {
                try
                {
                    _eval(scope, v -> { if(_toBool(v)) { throw _StopIteration.EX; }});
                    return false;
                }
                catch(_StopIteration e) { return true; }
            });
        }

        public _Expr SLen()
        {
            return _map("SLen", v ->
            {
                String s = _toStr(v);
                return (long)s.codePointCount(0, s.length());
            });
        }

        public _Expr Cat(Object... args) { return _infix("Cat", args, (x, y) -> _toStr(x) + _toStr(y)); }
        public _Expr Join(Object... args)
        {
            var delimExpr = _argsToExpr(args);
            return _singleExpr("Join", scope ->
            {
                var sb = new StringBuilder();
                String delim = _toStr(delimExpr._evalOne(scope, ""));
                if(delim.equals(""))
                {
                    _eval(scope, v -> sb.append(_toStr(v)));
                }
                else
                {
                    boolean[] useDelim = {false};
                    _eval(scope, v -> {
                        if(useDelim[0]) { sb.append(delim); }
                        else            { useDelim[0] = true; }
                        sb.append(_toStr(v));
                    });
                }
                return sb.toString();
            });
        }

        public _Expr To(Object... args)
        {
            var endExpr = _argsToExpr(args);
            return _callExpr("To", (scope, dest) ->
            {
                long from = _toInt(_evalOne(scope));
                long to = _toInt(endExpr._evalOne(scope));
                for(long i = from; i < to; i++)
                {
                    dest.accept(i);
                }
            });
        }

        public _Expr Ord()
        {
            return _callExpr("Ord",
                (scope, dest) -> _eval(scope, v -> _toStr(v).codePoints().forEach(cp -> dest.accept((long)cp))));
        }

        public _Expr Chr()
        {
            return _map("Chr", v ->
            {
                long cp = _toInt(v);
                if(0 <= cp && cp <= 0x10ffff && (cp < 0xd800 || cp > 0xdfff))
                {
                    return Character.toString((int)cp);
                }
                throw new MTException(String.format("Chr: %s0x%s (%d) is not a valid code point",
                    ((cp < 0) ? "-" : ""), Long.toString(Math.abs(cp), 16), cp));
            });
        }

        public _Expr RandShuffle()
        {
            return _listExpr("RandShuffle", scope ->
            {
                var values = _evalMutableList(scope);
                Collections.shuffle(values, _ExecState.instance().random);
                return values;
            })
            .setStaticLength(_staticLength);
        }

        public _Expr RandChoose(Object... args)
        {
            var nExpr = _argsToExpr(args);
            return _listExpr("RandChoose", scope ->
            {
                var pop = _evalMutableList(scope);
                long n = _toInt(nExpr._evalOne(scope, 1L));
                if(n < 0 || n > pop.size())
                {
                    throw new MTException(String.format(
                        "RandChoose: cannot choose %d value(s) from a %d-value sequence",
                        n, pop.size()));
                }
                Collections.shuffle(pop, _ExecState.instance().random);
                return pop.subList(0, (int)n);
            });
        }

        public _Expr With(Object... args)
        {
            var body = _argsToExpr(args);
            return _callExpr("With", (outerScope, dest) ->
            {
                var innerScope = new _Scope(outerScope);
                innerScope.put(_SpecialKeys.IT, _evalList(outerScope));
                body._eval(innerScope, dest);
            })
            .setStaticLength(body._staticLength);
        }

        public _Expr ForEach(Object... args)
        {
            var body = _argsToExpr(args);
            return _callExpr("ForEach", (outerScope, dest) ->
            {
                var state = _ExecState.instance();
                var innerScope = new _Scope(outerScope, outerScope.getAllowedJumps() | Break.bit | Continue.bit);
                try
                {
                    long[] idx = {0L};
                    var itemVar = new ArrayList<>();
                    var idxVar = new ArrayList<>();
                    itemVar.add(null);
                    idxVar.add(0L);
                    innerScope.put(_SpecialKeys.ITEM, itemVar);
                    innerScope.put(_SpecialKeys.IDX, idxVar);

                    _eval(outerScope, v ->
                    {
                        itemVar.set(0, v);
                        body._eval(innerScope, dest);
                        state.endJump(Continue);
                        if(state.currentJump != null)
                        {
                            throw _StopIteration.EX;
                        }
                        idx[0]++;
                        idxVar.set(0, idx[0]);
                    });
                }
                catch(_StopIteration _s) {} // NOPMD -- just needed to exit the callback.
                finally
                {
                    state.endJump(Break);
                }
            })
            .setStaticLength((_staticLength == UNKNOWN_LENGTH || body._staticLength == UNKNOWN_LENGTH)
                             ? UNKNOWN_LENGTH
                             : (_staticLength * body._staticLength));
        }

        public _Expr Eval()
        {
            return _callExpr("Eval", (outerScope, dest) -> _eval(outerScope, v ->
                new _Parser("(" + _toStr(v) + ")").parseList(_Parser.SyntaxMode.NORMAL)._eval(new _Scope(outerScope), dest)
            ));
        }

        public _Expr Print()
        {
            return _voidExpr("Print", scope -> _eval(scope, System.out::print));
        }

        public _Expr Done()
        {
            return _voidExpr("Done", scope -> _eval(scope, v -> {}));
        }

        public _Expr Note(Object... s)
        {
            return this;
        }
    }

    public static class _VExpr extends _Expr
    {
        private _Expr keyExpr;
        private _VExpr(_Expr keyExpr)
        {
            super("V");
            this.keyExpr = keyExpr;
        }

        @Override
        public void _eval(_Scope scope, _DestFn dest)
        {
            keyExpr._eval(scope, k -> scope.get(k).forEach(dest));
        }

        public _Expr Pop()
        {
            return _callExpr("Pop", (scope, dest) -> keyExpr._eval(scope, k ->
            {
                var list = scope.get(k);
                if(!list.isEmpty())
                {
                    dest.accept(list.remove(0));
                }
            }));
        }

        public _Expr Set(Object... valueArgs)
        {
            return _assign("Set", keyExpr, _argsToExpr(valueArgs),
                           (scope, k, v) -> scope.put(k, v));
        }
        public _Expr SetGlobal(Object... valueArgs)
        {
            return _assign("SetGlobal", keyExpr, _argsToExpr(valueArgs),
                           (_scope, k, v) -> _ExecState.instance().getGlobalScope().put(k, v));
        }

        public _Expr Update(Object... valueArgs)
        {
            return _assign("Update", keyExpr, _argsToExpr(valueArgs),
                (scope, k, v) -> {
                    var list = scope.get(k);
                    list.clear();
                    list.addAll(v);
                });
        }

        public _Expr Append(Object... valueArgs)
        {
            return _assign("Append", keyExpr, _argsToExpr(valueArgs),
                           (scope, k, v) -> scope.get(k).addAll(v));
        }
    }

    public static class _IfExpr // Does not extend _Expr
    {
        private _Expr condition;
        private _IfExpr(_Expr condition)
        {
            this.condition = condition;
        }

        public _ThenExpr Then(Object... args)
        {
            return new _ThenExpr(condition, _argsToExpr(args));
        }
    }

    public static class _ThenExpr extends _Expr
    {
        private _Expr condition;
        private _Expr thenBody;
        private _Expr elseBody = _EMPTY;

        private _ThenExpr(_Expr condition, _Expr thenBody)
        {
            super("If-Then");
            this.condition = condition;
            this.thenBody = thenBody;
        }

        private _Expr body(_Scope scope)
        {
            return _toBool(condition._evalOne(scope)) ? thenBody : elseBody;
        }

        @Override public void _eval(_Scope scope, _DestFn dest)     { body(scope)._eval(scope, dest); }
        @Override public List<Object> _evalList(_Scope scope)       { return body(scope)._evalList(scope); }
        @Override public Object _evalOne(_Scope scope, Object defV) { return body(scope)._evalOne(scope, defV); }

        public _Expr Else(Object... args)
        {
            elseBody = _argsToExpr(args);

            long thenLen = thenBody.getStaticLength();
            long elseLen = elseBody.getStaticLength();
            if(thenLen != UNKNOWN_LENGTH && thenLen == elseLen)
            {
                setStaticLength(thenLen);
            }
            return this;
        }
    }

    public static class _WhileExpr // does not implement _Expr
    {
        private _Expr condition;
        private _WhileExpr(_Expr condition)
        {
            this.condition = condition;
        }

        public _Expr Do(Object... args)
        {
            var body = _argsToExpr(args);
            return _callExpr("While-Do", (outerScope, dest) ->
            {
                var state = _ExecState.instance();
                var innerScope = new _Scope(outerScope, outerScope.getAllowedJumps() | Break.bit | Break.bit);
                try
                {
                    long idx = 0L;
                    var idxVar = new ArrayList<>();
                    idxVar.add(idx);
                    innerScope.put(_SpecialKeys.IDX, idxVar);

                    while(_toBool(condition._evalOne(innerScope)))
                    {
                        body._eval(innerScope, dest);
                        state.endJump(Continue);
                        if(state.currentJump != null)
                        {
                            break;
                        }
                        idx++;
                        idxVar.set(0, idx);
                    }
                }
                finally
                {
                    state.endJump(Break);
                }
            });
        }
    }

    public static class _TryExpr extends _Expr
    {
        private _Expr tryBody;
        private _Expr finallyBody = _EMPTY;
        private List<_Expr> catchPatterns = new ArrayList<>();
        private List<_Expr> catchBodies   = new ArrayList<>();

        private _TryExpr(_Expr tryBody)
        {
            super("Try");
            this.tryBody = tryBody;
        }

        private void _addCatch(_Expr catchPattern, _Expr catchBody)
        {
            catchPatterns.add(catchPattern);
            catchBodies.add(catchBody);
        }

        public _CatchExpr Catch(Object... args)
        {
            return new _CatchExpr(this, _argsToExpr(args));
        }

        public _Expr Finally(Object... args)
        {
            this.finallyBody = _argsToExpr(args);
            return this;
        }

        @Override
        public void _eval(_Scope scope, _DestFn dest)
        {
            _evalList(scope).forEach(dest);
        }

        @Override
        public List<Object> _evalList(_Scope scope)
        {
            int nCatches = catchPatterns.size();
            if(nCatches == 0 && finallyBody == null)
            {
                throw new MTException("Try: expected at least one Catch and/or a Finally");
            }

            var values = tryBody._evalMutableList(scope);
            var state = _ExecState.instance();

            if(state.endJump(_Jump.Throw))
            {
                scope.put(_SpecialKeys.EX, state.exception);
                scope.put(_SpecialKeys.PARTIAL, values);

                var cPatternIt = catchPatterns.iterator();
                var cBodyIt = catchBodies.iterator();
                var match = false;
                while(cPatternIt.hasNext())
                {
                    var pattern = cPatternIt.next()._evalList(scope);
                    var pSize = pattern.size();
                    var cBody = cBodyIt.next();
                    if(state.exception.size() >= pSize && state.exception.subList(0, pSize).equals(pattern))
                    {
                        state.exception = null;
                        values = cBody._evalList(scope);
                        match = true;
                        break;
                    }
                }
                if(!match)
                {
                    state.currentJump = _Jump.Throw;
                    values.clear();
                }
            }
            finallyBody._eval(scope, values::add);
            return values;
        }
    }

    public static class _CatchExpr  // Does not derive from _Expr
    {
        private _TryExpr tryExpr;
        private _Expr catchPattern;

        private _CatchExpr(_TryExpr tryExpr, _Expr catchPattern)
        {
            this.tryExpr = tryExpr;
            this.catchPattern = catchPattern;
        }

        public _TryExpr Do(Object... args)
        {
            tryExpr._addCatch(catchPattern, _argsToExpr(args));
            return tryExpr;
        }
    }


    public static final _Expr TRUE     = _singleExpr("TRUE",  _scope -> true);
    public static final _Expr FALSE    = _singleExpr("FALSE", _scope -> false);

    public static final _Expr Val      = _storedListExpr("Val",     _SpecialKeys.VAL);
    public static final _Expr Arg      = _storedListExpr("Arg",     _SpecialKeys.ARG);
    public static final _Expr ThisFn   = _storedListExpr("ThisFn",  _SpecialKeys.THIS_FN).setStaticLength(1);
    public static final _Expr Idx      = _storedListExpr("Idx",     _SpecialKeys.IDX).setStaticLength(1);
    public static final _Expr Item     = _storedListExpr("Item",    _SpecialKeys.ITEM).setStaticLength(1);
    public static final _Expr It       = _storedListExpr("It",      _SpecialKeys.IT);
    public static final _Expr Ex       = _storedListExpr("Ex",      _SpecialKeys.EX);
    public static final _Expr Partial  = _storedListExpr("Partial", _SpecialKeys.PARTIAL);

    public static final _Expr Time = _singleExpr("Time", _scope -> (double)System.currentTimeMillis() / 1000.0); // NOPMD -- helpful to be explicit about types.
    public static final _Expr Rand = _singleExpr("Rand", _scope -> _ExecState.instance().random.nextDouble());
    public static final _Expr Pi   = _singleExpr("Pi",   _scope -> Math.PI);
    public static final _Expr Inf  = _singleExpr("Inf",  _scope -> Double.POSITIVE_INFINITY);
    public static final _Expr MNaN = _singleExpr("MNaN", _scope -> Double.NaN);

    public static final _Jump Break    = new _Jump("Break", 0x01);
    public static final _Jump Continue = new _Jump("Continue", 0x02);
    public static final _Jump Return   = new _Jump("Return", 0x04);

    @SuppressWarnings("try")
    public static List<Object> MT(Object... args)
    {
        var state = _ExecState.instance();
        state.reset();
        var ret = _argsToExpr(args)._evalList(new _Scope(state.getGlobalScope()));
        if(state.endJump(_Jump.Throw))
        {
            throw new MTException("Unhandled exception: " + _valsRepr(state.exception));

        }
        return ret;
    }

    @SuppressWarnings("try")
    public static _Expr Scope(Object... args)
    {
        var body = _argsToExpr(args);
        return _callExpr("Scope", (outerScope, dest) -> body._eval(new _Scope(outerScope), dest));
    }

    public static _Expr C(Object... args)
    {
        return _argsToExpr(args);
    }

    public static _Expr I(Object... args)
    {
        return _argsToExpr(args)._map("I", v -> _toInt(v));
    }

    public static _Expr F(Object... args)
    {
        return _argsToExpr(args)._map("F", v -> _toFloat(v));
    }

    public static _Expr S(Object... args)
    {
        return _argsToExpr(args)._map("S", v -> _toStr(v));
    }

    public static _Expr Fn(Object... args)
    {
        var body = _argsToExpr(args);
        return _singleExpr("Fn", scope -> new _FunctionDef(scope, body));
    }

    public static _Expr T(Object... args)
    {
        var body = _argsToExpr(args);
        return _singleExpr("T", scope -> new _Tuple(body._evalList(scope)));
    }

    public static _VExpr V(Object... keyArgs)
    {
        return new _VExpr(_argsToExpr(keyArgs));
    }

    public static _IfExpr If(Object... args)
    {
        return new _IfExpr(_argsToExpr(args));
    }

    public static _WhileExpr While(Object... args)
    {
        return new _WhileExpr(_argsToExpr(args));
    }

    public static _TryExpr Try(Object... args)
    {
        return new _TryExpr(_argsToExpr(args));
    }

    public static _Expr Throw(Object... args)
    {
        var exExpr = _argsToExpr(args);
        return _voidExpr("Throw", scope ->
        {
            var state = _ExecState.instance();
            state.exception = exExpr._evalList(scope);
            state.currentJump = _Jump.Throw;
        });
    }

    public static _Expr Note(Object... s)
    {
        return _Expr._EMPTY;
    }


    // --- Internal types, variables and functions ---


    private static class _StopIteration extends RuntimeException
    {
        public static final _StopIteration EX = new _StopIteration();
    }

    @FunctionalInterface
    private interface _BiDoublePredicate
    {
        boolean test(double x, double y);
    }

    @FunctionalInterface private interface _DestFn extends Consumer<Object> {}
    @FunctionalInterface private interface _EvalFn extends BiConsumer<_Scope,_DestFn> {}
    @FunctionalInterface private interface _EvalListFn extends Function<_Scope,List<Object>> {}
    @FunctionalInterface private interface _EvalSingleFn extends Function<_Scope,Object> {}

    private static class _Scope
    {
        private _Scope parent;
        private Map<Object,List<Object>> vars = new HashMap<>();
        private int allowedJumps;

        public _Scope(_Scope parent, int allowedJumps)
        {
            this.parent = parent;
            this.allowedJumps = allowedJumps;
        }

        public _Scope(_Scope parent)
        {
            this.parent = parent;
            this.allowedJumps = parent.getAllowedJumps();
        }

        //private _Scope getParent() { return parent; }
        private int getAllowedJumps() { return allowedJumps; }

        private List<Object> get(Object key)
        {
            return getOptional(key)
                .orElseThrow(() -> new MTException(String.format(
                    "Variable \u201c%s\u201d (%s-named) is not defined", _toStr(key), _typeName(key))));
        }

        private Optional<List<Object>> getOptional(Object key)
        {
            var value = Optional.ofNullable(vars.get(key));
            if(value.isEmpty() && parent != null)
            {
                value = parent.getOptional(key);
            }
            return value;
        }

        private void put(Object key, List<Object> value)
        {
            vars.put(key, value);
        }

        @Override
        public String toString() { return vars + " -> " + parent; }
    }

    private static class _Jump
    {
        private static final _Jump Throw = new _Jump("Throw()", 0x8);
        private final String name;
        private final int bit;
        private _Jump(String name, int bit)
        {
            this.name = name;
            this.bit = bit;
        }
        @Override public String toString() { return name; }
    }

    private static class _ExecState
    {
        private static ThreadLocal<_ExecState> inst = new ThreadLocal<>()
        {
            @Override public _ExecState initialValue()
            {
                return new _ExecState();
            }
        };
        private static _ExecState instance() { return inst.get(); }

        private final Random random = new Random();
        private final _Scope globalScope = new _Scope(null, 0);
        // private _Scope localScope;
        private _Jump currentJump;
        private List<Object> exception = null;

        private void reset()
        {
            currentJump = null;
        }

        private _Scope getGlobalScope() { return globalScope; }

        private boolean endJump(_Jump j)
        {
            if(currentJump == j) // NOPMD -- deliberate reference comparison
            {
                currentJump = null;
                return true;
            }
            return false;
        }
    }

    private static class _FunctionDef
    {
        private _Scope parentScope;
        private _Expr body;
        private _FunctionDef(_Scope parentScope, _Expr body)
        {
            this.parentScope = parentScope;
            this.body = body;
        }

        private _Scope getParentScope() { return parentScope; }
        private _Expr getBody() { return body; }

        @Override public String toString() { return "Fn(...)"; }
    }

    private static class _Tuple extends ArrayList<Object>
    {
        private _Tuple(List<Object> values) { super(values); }

        @Override
        public String toString()
        {
            return "T(" + stream().map(o -> _repr(o)).collect(Collectors.joining(", ")) + ")";
        }
    }

    private enum _SpecialKeys { VAL, ARG, THIS_FN, IDX, ITEM, IT, EX, PARTIAL }

    private static _Expr _callExpr(String name, _EvalFn fn)
    {
        return new _Expr(name)
        {
            @Override public void _eval(_Scope scope, _DestFn dest)
            {
                fn.accept(scope, dest);
            }
        };
    }

    private static _Expr _voidExpr(String name, Consumer<_Scope> fn)
    {
        return new _Expr(name)
        {
            @Override public void _eval(_Scope scope, _DestFn _dest)
            {
                fn.accept(scope);
            }
        }
        .setStaticLength(0);
    }

    private static _Expr _generalListExpr(String name, _EvalListFn fn, _EvalListFn mutableListFn)
    {
        return new _Expr(name)
        {
            @Override public List<Object> _evalList(_Scope scope)        { return fn.apply(scope); }
            @Override public List<Object> _evalMutableList(_Scope scope) { return mutableListFn.apply(scope); }
            @Override public void _eval(_Scope scope, _DestFn dest)      { fn.apply(scope).forEach(dest); }
            @Override public _Expr Len()
            {
                return _singleExpr("Len", scope -> (long)_evalList(scope).size());
            }
        };
    }

    private static _Expr _listExpr(String name, _EvalListFn fn)
    {
        return _generalListExpr(name, fn, fn);
    }

    private static _Expr _storedListExpr(String name, Object key)
    {
        return _generalListExpr(name, scope -> scope.get(key),
                                      scope -> new ArrayList<>(scope.get(key)));
    }

    private static _Expr _singleExpr(String name, _EvalSingleFn fn)
    {
        return new _Expr(name)
        {
            @Override public void _eval(_Scope scope, _DestFn dest)      { dest.accept(fn.apply(scope)); }
            @Override public Object _evalOne(_Scope scope)               { return fn.apply(scope); }
            @Override public Object _evalOne(_Scope scope, Object defV)  { return fn.apply(scope); }
            @Override public List<Object> _evalMutableList(_Scope scope) { return _evalList(scope); }
            @Override public List<Object> _evalList(_Scope scope)
            {
                var list = new ArrayList<>();
                list.add(fn.apply(scope));
                return list;
            }
        }
        .setStaticLength(1);
    }

    private static _Expr _argToExpr(Object arg)
    {
        if(arg instanceof _Jump)
        {
            var j = (_Jump)arg;
            return _voidExpr(j.name, scope ->
            {
                if((scope.getAllowedJumps() & j.bit) == 0)
                {
                    throw new MTException(j.name + " not permitted here");
                }
                _ExecState.instance().currentJump = j;
            });
        }
        else if(arg instanceof _Expr)
        {
             return (_Expr)arg;
        }
        else if(arg instanceof Double || arg instanceof Float)
        {
            double d = ((Number)arg).doubleValue();
            return (d % 1.0 == 0.0) ? _singleExpr("<int-literal>",   _scope -> (long)d)
                                    : _singleExpr("<float-literal>", _scope -> d);
        }
        else if(arg instanceof Long || arg instanceof Integer)
        {
            long l = ((Number)arg).longValue();
            return _singleExpr("<int-literal>", _scope -> l);
        }
        else if(arg instanceof String)
        {
            return new _Parser(_escapeStr((String)arg, "%")).parseStringLiteral();
        }
        else
        {
            throw new MTException(
                String.format("Unsupported host language value %s \u201c%s\u201d", arg.getClass().getName(), arg));
        }
    }

    private static _Expr _argsToExpr(Object[] args)
    {
        switch(args.length)
        {
            case 0: return _Expr._EMPTY;
            case 1: return _argToExpr(args[0]);
            default:
                var exprList = Arrays.stream(args).map(Mistletoe::_argToExpr).collect(Collectors.toList());
                long staticLen = 0L;
                for(var expr : exprList)
                {
                    long len = expr.getStaticLength();
                    if(len == _Expr.UNKNOWN_LENGTH)
                    {
                        staticLen = _Expr.UNKNOWN_LENGTH;
                        break;
                    }
                    staticLen += len;
                }

                return _callExpr("<expr-list>", (scope, dest) ->
                {
                    var state = _ExecState.instance();
                    for(var expr : exprList)
                    {
                        expr._eval(scope, dest);
                        if(state.currentJump != null)
                        {
                            break;
                        }
                    }
                })
                .setStaticLength(staticLen);
        }
    }

    private static long _toInt(Object obj)
    {
        if(obj instanceof Number)
        {
            return ((Number)obj).longValue();
        }
        if(obj instanceof String)
        {
            try
            {
                return (long)Double.parseDouble((String)obj);
            }
            catch(NumberFormatException e) {} // NOPMD -- deliberately fall-through to the throw.
        }
        throw new MTException(String.format(
            "Cannot convert %s \u201c%s\u201d to Int", _typeName(obj), _repr(obj)));
    }

    private static double _toFloat(Object obj)
    {
        if(obj instanceof Number)
        {
            return ((Number)obj).doubleValue();
        }
        if(obj instanceof String)
        {
            try
            {
                return Double.parseDouble((String)obj);
            }
            catch(NumberFormatException e) {} // NOPMD -- deliberately fall-through to the throw.
        }
        throw new MTException(String.format(
            "Cannot convert %s \u201c%s\u201d to Float", _typeName(obj), _repr(obj)));
    }

    private static boolean _toBool(Object obj)
    {
        if(obj instanceof Boolean)
        {
            return (Boolean)obj;
        }
        throw new MTException(String.format(
            "Expected Bool but found %s \u201c%s\u201d", _typeName(obj), _repr(obj)));
    }

    private static String _toStr(Object obj)
    {
        if(obj instanceof String) { return (String)obj; }
        if(obj instanceof Boolean)
        {
            return (Boolean)obj ? "TRUE" : "FALSE";
        }
        if(obj instanceof Double)
        {
            Double d = (Double)obj;
            if(Double.isNaN(d)) { return "MNaN"; }
            else if(d == Double.POSITIVE_INFINITY) { return "Inf"; }
            else if(d == Double.NEGATIVE_INFINITY) { return "Inf.Neg()"; }
        }
        return String.valueOf(obj);
    }

    private static String _escapeStr(String s, String percent)
    {
        var sb = new StringBuilder("\"");
        s.chars().forEach(ch ->
        {
            switch(ch)
            {
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '%':  sb.append(percent); break;
                default:   sb.append((char)ch); break;
            }
        });
        sb.append('"');
        return sb.toString();
    }

    private static String _repr(Object obj)
    {
        return (obj instanceof String) ? _escapeStr((String)obj, "%%") : _toStr(obj);
    }

    private static String _valsRepr(List<Object> vals)
    {
        return "[" + vals.stream().map(o -> _repr(o)).collect(Collectors.joining(", ")) + "]";
    }

    private static String _typeName(Object obj)
    {
        if(obj instanceof String)       { return "Str"; }
        if(obj instanceof Long)         { return "Int"; }
        if(obj instanceof Double)       { return "Float"; }
        if(obj instanceof Boolean)      { return "Bool"; }
        if(obj instanceof _FunctionDef) { return "Fn"; }
        if(obj instanceof _Tuple)       { return "Tuple"; }
        if(obj instanceof _SpecialKeys) { return "SpecialKey"; }
        throw new IllegalArgumentException();
    }

    private static boolean _eq(Object x, Object y)
    {
        if(x instanceof Number && y instanceof Number)
        {
            return ((Number)x).doubleValue() == ((Number)y).doubleValue();
        }
        return x.equals(y);
    }

    private static BinaryOperator<Object> _compare(String name, _BiDoublePredicate floatTest, IntPredicate strTest)
    {
        return (x, y) ->
        {
            if(x instanceof Number && y instanceof Number)
            {
                return floatTest.test(((Number)x).doubleValue(), ((Number)y).doubleValue());
            }
            if(x instanceof String && y instanceof String)
            {
                return strTest.test(((String)x).compareTo((String)y));
            }
            throw new MTException(String.format(
                "%s: %s \u201c%s\u201d and %s \u201c%s\u201d are not comparable",
                name, _typeName(x), _repr(x), _typeName(y), _repr(y)));
        };
    }

    private static BinaryOperator<Object> _intOrFloat(BiFunction<Long,Long,Object> intFn,
                                                      BiFunction<Double,Double,Object> floatFn)
    {
        return (x, y) -> (x instanceof Long && y instanceof Long) ? intFn.apply((Long)x, (Long)y)
                                                                  : floatFn.apply(_toFloat(x), _toFloat(y));
    }

    private static double _nonZero(double n)
    {
        if(n == 0.0) { throw new MTException("Division by zero"); }
        return n;
    }

    private static long _nonZero(long n)
    {
        if(n == 0L) { throw new MTException("Division by zero"); }
        return n;
    }

    private interface Mutator
    {
        void set(_Scope scope, Object key, List<Object> values);
    }

    @SuppressWarnings("try")
    private static _Expr _assign(String name, _Expr keyExpr, _Expr valueExpr, Mutator mutator)
    {
        return _voidExpr(name, outerScope ->
        {
            var keys = keyExpr._evalList(outerScope);
            if(keys.isEmpty()) { throw new MTException("Cannot assign to an empty list of variables"); }

            //var state = _ExecState.instance();
            var oldValues = keys.stream()
                .flatMap(k -> outerScope.getOptional(k).orElse(List.of()).stream())
                .collect(Collectors.toList());

            var innerScope = new _Scope(outerScope);
            innerScope.put(_SpecialKeys.VAL, oldValues);
            List<Object> newValues = valueExpr._evalList(innerScope);

            var valueIt = newValues.iterator();
            int nKeys = keys.size();
            for(int i = 0; i < nKeys - 1; i++)
            {
                var valueList = new ArrayList<>();
                if(valueIt.hasNext())
                {
                    valueList.add(valueIt.next());
                }
                mutator.set(outerScope, keys.get(i), valueList);
            }

            var valueList = new ArrayList<>();
            while(valueIt.hasNext())
            {
                valueList.add(valueIt.next());
            }
            mutator.set(outerScope, keys.get(nKeys - 1), valueList);
        });
    }

    @SuppressWarnings("try")
    private static _Expr _call(String name, _Expr fnExpr, _Expr fnArgExpr)
    {
        return _callExpr(name, (outerScope, dest) ->
        {
            var fnArgs = fnArgExpr._evalList(outerScope);
            fnExpr._eval(outerScope, fnIdent ->
            {
                _FunctionDef fn;
                if(fnIdent instanceof _FunctionDef)
                {
                    fn = (_FunctionDef)fnIdent;
                }
                else
                {
                    var lookup = outerScope.getOptional(fnIdent).orElse(List.of());
                    if(lookup.size() == 1 && lookup.get(0) instanceof _FunctionDef)
                    {
                        fn = (_FunctionDef)(lookup.get(0));
                    }
                    else
                    {
                        throw new MTException(String.format("No function named \"%s\"", fnIdent));
                    }
                }

                var innerScope = new _Scope(fn.getParentScope(), Return.bit);
                innerScope.put(_SpecialKeys.ARG, fnArgs);
                innerScope.put(_SpecialKeys.THIS_FN, List.of(fn));
                try
                {
                    fn.getBody()._eval(innerScope, dest);
                }
                finally
                {
                    _ExecState.instance().endJump(Return);
                }
            });
        });
    }

    private static class _Parser
    {
        private enum TT
        {
            OPEN      ("\u201c(\u201d",  "\\("),
            CLOSE     ("\u201c)\u201d",  "\\)"),
            COMMA     ("\u201c,\u201d",  ","),
            DOT       ("\u201c.\u201d",  "\\."),
            HEX_NUMBER("<hex-number>",   "[-+]?0x[0-9a-fA-F]+"),
            NUMBER    ("<number>",       "[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?"),
            STR_DOUBLE("\u201c\"\u201d", "\""),
            STR_SINGLE("\u201c'\u201d",  "'"),
            KEYWORD   ("<keyword>",      "[A-Z][a-zA-Z0-9]*"),
            IDENTIFIER("<identifier>",   "[a-z][a-zA-Z0-9_]*");

            String label;
            Pattern pattern;
            TT(String label, String p) {
                this.label = label;
                this.pattern = Pattern.compile(p);
            }
        }

        private enum SyntaxMode { NORMAL, STRING_EMBEDDED }

        private static final Pattern ESCAPE = Pattern.compile("[%DH(]|x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{6}");
        private static final int ERROR_CONTEXT = 15;
        private String input;
        private String token = null;
        private int pos = 0;

        private _Parser(String input)
        {
            this.input = input;
        }

        private void addLastStr(List<_Expr> components, StringBuilder sb)
        {
            var lastStr = sb.toString();
            int len = lastStr.length();
            if(len > 0)
            {
                sb.delete(0, len);
                components.add(_singleExpr("<str-component>", _scope -> lastStr));
            }
        }

        private _Expr parseStringLiteral()
        {
            var components = new ArrayList<_Expr>();
            var sb = new StringBuilder();
            var escapeMatcher = ESCAPE.matcher(input);
            int inputLen = input.length();
            int startPos = pos;

            char delimCh = input.charAt(pos);
            pos += 1;

            while(pos < inputLen)
            {
                char ch = input.charAt(pos);
                if(ch == delimCh)
                {
                    pos += 1;
                    addLastStr(components, sb);
                    return _singleExpr("<str-literal>", scope ->
                    {
                        var finalSb = new StringBuilder();
                        for(var c : components)
                        {
                            c._eval(scope, v -> finalSb.append(_toStr(v)));
                        }
                        return finalSb.toString();
                    });
                }
                switch(ch)
                {
                    case '\\':
                        switch(((pos + 1) < inputLen) ? input.charAt(pos + 1) : '\0')
                        {
                            case 't':  sb.append('\t'); break;
                            case 'n':  sb.append('\n'); break;
                            case 'r':  sb.append('\r'); break;
                            case '"':  sb.append('"');  break;
                            case '\\': sb.append('\\'); break;
                            default:
                                throw parseErr("illegal \\-escape");
                        }
                        pos += 2;
                        break;

                    case '%':
                        if(!escapeMatcher.region(pos + 1, inputLen).lookingAt())
                        {
                            throw parseErr("illegal %%-escape (use \u201c%%%%\u201d for a literal \u201c%%\u201d)");
                        }
                        var escape = escapeMatcher.group();
                        pos += 1 + escape.length();
                        switch(escape.charAt(0))
                        {
                            case '%':  sb.append('%'); break;
                            case '\'': sb.append('\''); break;
                            case 'D':  sb.append('$'); break;
                            case 'H':  sb.append('#'); break;
                            case 'x': case 'u': case 'U':
                                sb.append(Character.toString(Integer.parseInt(escape.substring(1), 16)));
                                break;
                            case '(':
                                pos -= 1;
                                addLastStr(components, sb);
                                components.add(parseList(SyntaxMode.STRING_EMBEDDED));
                                break;
                            default:
                                throw new AssertionError();
                        }
                        break;

                    case '"':
                        throw parseErr("illegal char \u201c\"\u201d (use \\\")");

                    case '$': case '#':
                        throw parseErr("illegal char \u201c%c\u201d (use a %%-escape)", ch);

                    default:
                        pos += 1;
                        sb.append(ch);
                }
            }
            pos = startPos;
            throw parseErr("unclosed string literal");
        }

        private Object parseOperator(Object expr, Class<?> type, SyntaxMode syntaxMode)
        {
            try
            {
                for(var m : type.getMethods())
                {
                    if(m.getName().equals(token))
                    {
                        if(m.getParameterCount() == 0)
                        {
                            next(TT.OPEN);
                            next(TT.CLOSE);
                            return m.invoke(expr);
                        }
                        else
                        {
                            return m.invoke(expr, (Object)new Object[]{ parseList(syntaxMode) });
                        }
                    }
                }
                return type.getField(token).get(expr);
            }
            catch(InvocationTargetException | IllegalAccessException e)
            {
                var cause = e.getCause();
                if(cause instanceof MTException)
                {
                    throw (MTException)cause; // NOPMD -- the point is to re-throw the original exception.
                }
                throw new AssertionError(e);
            }
            catch(ReflectiveOperationException e)
            {
                pos -= token.length();
                throw parseErr(e, "unexpected symbol \u201c%s\u201d", token);
            }
        }

        private _Expr parseList(SyntaxMode syntaxMode)
        {
            TT strTT = (syntaxMode == SyntaxMode.NORMAL) ? TT.STR_DOUBLE : TT.STR_SINGLE;
            TT identifierTT = (syntaxMode == SyntaxMode.NORMAL) ? null : TT.IDENTIFIER;

            var args = new ArrayList<Object>();
            next(TT.OPEN);
            TT tt = next(TT.HEX_NUMBER, TT.NUMBER, strTT, identifierTT, TT.KEYWORD, TT.CLOSE);
            while(tt != TT.CLOSE)
            {
                switch(tt)
                {
                    case NUMBER:     args.add(Double.parseDouble(token));                   break;
                    case HEX_NUMBER: args.add(Long.parseLong(token.replace("0x", ""), 16)); break;
                    case STR_DOUBLE:
                    case STR_SINGLE:
                        pos -= token.length();
                        args.add(parseStringLiteral());
                        break;

                    case IDENTIFIER:
                    case KEYWORD:
                        Object expr;
                        if(tt == TT.IDENTIFIER)
                        {
                            var varName = token;
                            expr = new _VExpr(_singleExpr("<identifier>", _s -> varName));
                        }
                        else
                        {
                            expr = parseOperator(null, Mistletoe.class, syntaxMode);
                        }

                        if(expr instanceof _Jump)
                        {
                            args.add(expr);
                        }
                        else
                        {
                            while(next(TT.DOT, TT.COMMA, TT.CLOSE) == TT.DOT)
                            {
                                next(TT.KEYWORD);
                                expr = parseOperator(expr, expr.getClass(), syntaxMode);
                            }
                            pos -= token.length();
                            if(!(expr instanceof _Expr))
                            {
                                throw parseErr("incomplete expression");
                            }
                            args.add((_Expr)expr);
                        }
                        break;

                    default: throw new AssertionError();
                }
                tt = next(TT.COMMA, TT.CLOSE);
                if(tt == TT.COMMA)
                {
                    tt = next(TT.HEX_NUMBER, TT.NUMBER, strTT, identifierTT, TT.KEYWORD);
                }
            }
            return _argsToExpr(args.toArray());
        }

        private TT next(TT... expectedTokens)
        {
            int inputLen = input.length();
            while(pos < inputLen && Character.isWhitespace(input.charAt(pos)))
            {
                pos++;
            }
            for(var tokenType : expectedTokens)
            {
                if(tokenType == null) { continue; }
                var matcher = tokenType.pattern.matcher(input);
                if(matcher.region(pos, inputLen).lookingAt())
                {
                    token = matcher.group();
                    pos += token.length();
                    return tokenType;
                }
            }

            if(expectedTokens.length == 1)
            {
                throw parseErr("expected %s", expectedTokens[0].label);
            }
            throw parseErr("expected one of %s",
                Arrays.stream(expectedTokens).filter(Objects::nonNull).map(tt -> tt.label).collect(Collectors.joining(", ")));
        }

        private MTException parseErr(String format, Object... args)
        {
            return parseErr(null, format, args);
        }

        private MTException parseErr(Throwable cause, String format, Object... args)
        {
            int p = pos;
            String inp = input;
            if(inp.startsWith("(") && inp.endsWith(")"))
            {
                p -= 1;
                inp = inp.substring(1, inp.length() - 1);
            }

            int line = 1 + (int)inp.substring(0, p).chars().filter(ch -> ch == '\n').count();
            int col = p - inp.lastIndexOf('\n', p - 1);

            int preCutoff = Math.max(0, p - ERROR_CONTEXT);
            String preContext = (preCutoff > 0 ? "..." : "") + inp.substring(preCutoff, p);

            int postCutoff = Math.min(inp.length(), p + ERROR_CONTEXT);
            String postContext = inp.substring(p, postCutoff) + (postCutoff < inp.length() ? "..." : "");

            return new MTException(String.format("Parsing error at line %d, col %d: %s in \u201c%s\u25b6%s\u201d",
                line, col, String.format(format, args), preContext, postContext), cause);
        }
    }
}
