// Mistletoe v0.1 -- C# implementation

using System;
using System.Collections.Generic;
using System.Collections.Immutable;
using System.Globalization;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;

#pragma warning disable CA1062  // Given the context, it's unlikely null will appear in method arguments. Implicit checking should be fine.
#pragma warning disable CA1305  // Localisation of error messages is not a goal at this time.
#pragma warning disable CA1310  // Localisation of string comparisons is not a goal at this time.
#pragma warning disable CA1707  // Project conventions: names starting with "_" for internal implementation mechanisms.
#pragma warning disable CA1711  // Certain names (particularly 'Ex') are required by the specification.
#pragma warning disable CA1720  // The project has an inherent need to refer to types (Int, etc.)
#pragma warning disable CA1724  // The mistletoe.Mistletoe naming seems the best compromise.
#pragma warning disable CA1805  // Arguably this rule damages readability!
#pragma warning disable CA5394  // The project is not intended for cryptography.


namespace mistletoe
{
    public class MTException : Exception
    {
        internal MTException()           : base() {}
        internal MTException(string msg) : base(msg) {}
        internal MTException(string msg, Exception inner) : base(msg, inner) {}
    }

    internal delegate void _DestFn(object v);
    internal delegate void _EvalFn(_Scope scope, _DestFn dest);
    internal delegate List<object> _EvalListFn(_Scope scope);
    internal delegate object _EvalOneFn(_Scope scope);

    public static class Mistletoe
    {
        public static readonly _Expr TRUE     = new _SingleExpr("TRUE",  _scope => true);
        public static readonly _Expr FALSE    = new _SingleExpr("FALSE", _scope => false);

        public static readonly _Expr Val      = _MT._StoredListExpr("Val",     _SpecialKeys.VAL);
        public static readonly _Expr Arg      = _MT._StoredListExpr("Arg",     _SpecialKeys.ARG);
        public static readonly _Expr ThisFn   = _MT._StoredListExpr("ThisFn",  _SpecialKeys.THIS_FN)._SetStaticLength(1);
        public static readonly _Expr Idx      = _MT._StoredListExpr("Idx",     _SpecialKeys.IDX)._SetStaticLength(1);
        public static readonly _Expr Item     = _MT._StoredListExpr("Item",    _SpecialKeys.ITEM)._SetStaticLength(1);
        public static readonly _Expr It       = _MT._StoredListExpr("It",      _SpecialKeys.IT);
        public static readonly _Expr Ex       = _MT._StoredListExpr("Ex",      _SpecialKeys.EX);
        public static readonly _Expr Partial  = _MT._StoredListExpr("Partial", _SpecialKeys.PARTIAL);

        public static readonly _Expr Time     = new _SingleExpr("Time", _scope => (DateTime.UtcNow - DateTime.UnixEpoch).TotalSeconds);
        public static readonly _Expr Rand     = new _SingleExpr("Rand", _scope => _ExecState.Instance.Rand.NextDouble());
        public static readonly _Expr Pi       = new _SingleExpr("Pi",   _scope => Math.PI);
        public static readonly _Expr Inf      = new _SingleExpr("Inf",  _scope => Double.PositiveInfinity);
        public static readonly _Expr MNaN     = new _SingleExpr("MNaN", _scope => Double.NaN);

        public static readonly _Jump Break    = new _Jump("Break", 0x01);
        public static readonly _Jump Continue = new _Jump("Continue", 0x02);
        public static readonly _Jump Return   = new _Jump("Return", 0x04);

        public static IList<object> MT(params object[] args)
        {
            var state = _ExecState.Instance;
            state.Reset();

            var ret = _MT._ToExpr(args)._EvalList(new _Scope(state.GlobalScope));
            if(state.EndJump(_Jump.Throw))
            {
                throw new MTException("Unhandled exception: " + _MT._ValsRepr(state.Exception!));
            }
            return ret;
        }

        public static _Expr Scope(params object[] args)
        {
            var body = _MT._ToExpr(args);
            return _MT._CallExpr("Scope", (outerScope, dest) => body._Eval(new _Scope(outerScope), dest))._SetStaticLength(body._StaticLength);
        }

        public static _Expr C(params object[] args)
            => _MT._ToExpr(args);

        public static _Expr I(params object[] args) { return _MT._ToExpr(args)._Map("I", v => _MT._ToInt(v)); }
        public static _Expr F(params object[] args) { return _MT._ToExpr(args)._Map("F", v => _MT._ToFloat(v)); }
        public static _Expr S(params object[] args) { return _MT._ToExpr(args)._Map("S", v => _MT._ToStr(v)); }

        public static _Expr Fn(params object[] args)
        {
            var body = _MT._ToExpr(args);
            return new _SingleExpr("Fn", scope => new _FunctionDef(scope, body));
        }

        public static _Expr T(params object[] args)
        {
            var body = _MT._ToExpr(args);
            return new _SingleExpr("Tuple", scope => new _Tuple(body._EvalList(scope)));
        }

        public static _VExpr V(params object[] args)         => new _VExpr(_MT._ToExpr(args));
        public static _IfExpr If(params object[] args)       => new _IfExpr(_MT._ToExpr(args));
        public static _WhileExpr While(params object[] args) => new _WhileExpr(_MT._ToExpr(args));
        public static _TryExpr Try(params object[] args)     => new _TryExpr(_MT._ToExpr(args));

        public static _Expr Throw(params object[] args)
        {
            var expr = _MT._ToExpr(args);
            return _MT._VoidExpr("Throw", scope => {
                var state = _ExecState.Instance;
                state.Exception = expr._EvalList(scope);
                state.CurrentJump = _Jump.Throw;
            });
        }

        public static _Expr Note(params object[] s) => _Expr._EMPTY;
    }


    public abstract class _Expr
    {
        internal static readonly _Expr _EMPTY = _MT._VoidExpr("<empty>", _scope => {});
        internal const long UNKNOWN_LENGTH = -1L;

        internal string _Name;
        internal long _StaticLength { get; set; }
        internal _Expr(string name)
        {
            _Name = name;
            _StaticLength = UNKNOWN_LENGTH;
        }

        internal _Expr _SetStaticLength(long len)
        {
            _StaticLength = len;
            return this;
        }

        public IList<object> staticLength() { return new List<object>{ _StaticLength }; }

        internal abstract void _Eval(_Scope scope, _DestFn dest);
        internal virtual List<object> _EvalMutableList(_Scope scope) => _EvalList(scope);
        internal virtual List<object> _EvalList(_Scope scope)
        {
            var list = new _Vals();
            _Eval(scope, list.Add);
            return list;
        }

        internal virtual object _EvalOne(_Scope scope, object? defaultValue = null)
        {
            object? v = null;
            _Eval(scope, obj => {
                if(v != null)
                {
                    throw new MTException(String.Format("Expected one value but found multiple: {0}, {1}, ...",
                        _MT._Repr(v), _MT._Repr(obj)));
                }
                v = obj;
            });
            if(v == null)
            {
                if(defaultValue != null) { v = defaultValue; }
                else
                {
                    throw new MTException("Expected one value but found zero");
                }
            }
            return v;
        }

        private _Expr _Infix(string name, object[] args, Func<object,object,object> op)
        {
            var right = _MT._ToExpr(args);
            long llen = _StaticLength;
            long rlen = right._StaticLength;

            if(llen == 0 || rlen == 0)
            {
                return _EMPTY;
            }
            else if(llen == 1)
            {
                return _MT._CallExpr(name, (scope, dest) =>
                {
                    var x = _EvalOne(scope);
                    right._Eval(scope, y => dest(op(x, y)));
                })
                ._SetStaticLength(rlen);
            }
            else if(rlen == 1)
            {
                return _MT._CallExpr(name, (scope, dest) =>
                {
                    var y = right._EvalOne(scope);
                    _Eval(scope, x => dest(op(x, y)));
                })
                ._SetStaticLength(llen);
            }
            else
            {
                return _MT._CallExpr(name, (scope, dest) =>
                {
                    var operands1 = _EvalList(scope);
                    var operands2 = right._EvalList(scope);
                    if(operands1.Count == 0 || operands2.Count == 0) { return; }

                    IEnumerator<object> it1 = operands1.GetEnumerator();
                    IEnumerator<object> it2 = operands2.GetEnumerator();
                    int len = Math.Max(operands1.Count, operands2.Count);
                    for(int i = 0; i < len; i++)
                    {
                        if(!it1.MoveNext())
                        {
                            it1.Reset();
                            it1.MoveNext();
                        }
                        if(!it2.MoveNext())
                        {
                            it2.Reset();
                            it2.MoveNext();
                        }
                        dest(op(it1.Current, it2.Current));
                    }
                })
                ._SetStaticLength((llen == UNKNOWN_LENGTH || rlen == UNKNOWN_LENGTH) ? UNKNOWN_LENGTH
                                                                                     : Math.Max(llen, rlen));
            }
        }

        internal _Expr _Map(string name, Func<object,object> op)
            => _MT._CallExpr(name, (scope, dest) => _Eval(scope, v => dest(op(v))))._SetStaticLength(_StaticLength);

        private _Expr _NumericPostfix(string name, Func<long,object> intFn, Func<double,object> floatFn)
            => _MT._CallExpr(name, (scope, dest) =>
                _Eval(scope, v => dest((v is long) ? intFn((long)v)
                                                   : floatFn(_MT._ToFloat(v)))))._SetStaticLength(_StaticLength);

        public _Expr As(params object[] args)
            => _MT._Assign("As", _MT._ToExpr(args), this, (scope, k, v) => scope.Put(k, v));

        public _Expr Call(params object[] args)  => _MT._Call("Call", this, _MT._ToExpr(args));
        public _Expr Apply(params object[] args) => _MT._Call("Apply", _MT._ToExpr(args), this);

        public _Expr Eq(params object[] args) => _Infix("Eq", args, (x, y) => _MT._Eq(x, y));
        public _Expr Ne(params object[] args) => _Infix("Ne", args, (x, y) => !_MT._Eq(x, y));
        public _Expr Lt(params object[] args) => _Infix("Lt", args, _MT._Compare("Lt", (x, y) => x <  y, c => c < 0));
        public _Expr Le(params object[] args) => _Infix("Le", args, _MT._Compare("Le", (x, y) => x <= y, c => c <= 0));
        public _Expr Gt(params object[] args) => _Infix("Gt", args, _MT._Compare("Gt", (x, y) => x >  y, c => c > 0));
        public _Expr Ge(params object[] args) => _Infix("Ge", args, _MT._Compare("Ge", (x, y) => x >= y, c => c >= 0));
        public _Expr IsNaN() => _Map("IsNaN", v => Double.IsNaN(_MT._ToFloat(v)));

        public _Expr In(params object[] args)
        {
            var rightExpr = _MT._ToExpr(args);
            return _MT._CallExpr("In", (scope, dest) =>
            {
                var set = new HashSet<object>();
                rightExpr._Eval(scope, v => set.Add(v));
                _Eval(scope, v => dest(set.Contains(v)));
            })
            ._SetStaticLength(_StaticLength);
        }

        public _Expr NotIn(params object[] args) => In(args).Not();

        public _Expr Plus(params object[] args)  => _Infix("Plus",  args, _MT._IntOrFloat((x, y) => x + y, (x, y) => x + y));
        public _Expr Minus(params object[] args) => _Infix("Minus", args, _MT._IntOrFloat((x, y) => x - y, (x, y) => x - y));
        public _Expr Times(params object[] args) => _Infix("Times", args, _MT._IntOrFloat((x, y) => x * y, (x, y) => x * y));
        public _Expr Div(params object[] args)   => _Infix("Div",   args, _MT._IntOrFloat((x, y) => x / _MT._NonZeroInt(y),
                                                                                          (x, y) => x / _MT._NonZeroFloat(y)));
        public _Expr Mod(params object[] args)   => _Infix("Mod",   args, _MT._IntOrFloat((x, y) => x % _MT._NonZeroInt(y),
                                                                                          (x, y) => x % _MT._NonZeroFloat(y)));
        public _Expr Pow(params object[] args)   => _Infix("Pow",   args, _MT._IntOrFloat((x, y) => (long)Math.Pow(x, y),
                                                                                          (x, y) => Math.Pow(x, y)));
        public _Expr Neg() => _NumericPostfix("Neg", i => -i, f => -f);
        public _Expr Abs() => _NumericPostfix("Abs", i => Math.Abs(i), f => Math.Abs(f));

        public _Expr And(params object[] args) => _Infix("And", args, (x, y) => _MT._ToBool(x) && _MT._ToBool(y));
        public _Expr Or(params object[] args)  => _Infix("Or",  args, (x, y) => _MT._ToBool(x) || _MT._ToBool(y));
        public _Expr Not() => _Map("Not", v => !_MT._ToBool(v));

        public _Expr Int()   => _Map("Int", v => _MT._ToInt(v));
        public _Expr Float() => _Map("Float", v => _MT._ToFloat(v));
        public _Expr Str()   => _Map("Str", v => _MT._ToStr(v));
        public _Expr Tuple() => new _SingleExpr("Tuple", scope => new _Tuple(_EvalList(scope)));
        public _Expr Flatten()
            => _MT._CallExpr("Flatten", (scope, dest) => _Eval(scope, v =>
            {
                if(v is _Tuple t)
                {
                    t.ForEach(item => dest(item));
                }
                else
                {
                    dest(v);
                }
            }));

        public _Expr At(params object[] args)
        {
            var indexExpr = _MT._ToExpr(args);
            return _MT._CallExpr("At", (scope, dest) =>
            {
                var src = _EvalList(scope);
                int len = src.Count;
                indexExpr._Eval(scope, v => {
                    int idx = (int)_MT._ToInt(v);
                    if(idx < -len || idx >= len)
                    {
                        throw new MTException(String.Format("At: index {0} is invalid for a {1}-value sequence", idx, len));
                    }
                    if(idx < 0) { idx += len; }
                    dest(src[idx]);
                });
            })
            ._SetStaticLength(indexExpr._StaticLength);
        }

        public _Expr Len()
            => new _SingleExpr("Len", scope =>
            {
                long count = 0L;
                _Eval(scope, v => count++);
                return count;
            });

        public _Expr Sum()
            => new _SingleExpr("Sum", scope =>
            {
                bool intsOnly = true;
                long intSum = 0;
                double floatSum = 0.0;

                _Eval(scope, v =>
                {
                    if(intsOnly)
                    {
                        if(v is long l)
                        {
                            intSum += l;
                            return;
                        }
                        floatSum = (double)intSum;
                        intsOnly = false;
                    }
                    floatSum += _MT._ToFloat(v);
                });

                if(intsOnly) { return intSum; } else { return floatSum; }
            });

        public _Expr All()
            => new _SingleExpr("All", scope =>
            {
                try
                {
                    _Eval(scope, v => { if(!_MT._ToBool(v)) { throw _StopIteration.EX; }});
                    return true;
                }
                catch(_StopIteration) { return false; }
            });

        public _Expr Any()
            => new _SingleExpr("Any", scope =>
            {
                try
                {
                    _Eval(scope, v => { if(_MT._ToBool(v)) { throw _StopIteration.EX; }});
                    return false;
                }
                catch(_StopIteration) { return true; }
            });

        public _Expr SLen()
            => _Map("SLen", v =>
            {
                long nCodePoints = 0;
                foreach(char ch in _MT._ToStr(v))
                {
                    // Assuming strings are validly formatted, just ignore half of each surrogate pair.
                    if(!Char.IsHighSurrogate(ch))
                    {
                        nCodePoints++;
                    }
                }
                return nCodePoints;
            });

        public _Expr Cat(params object[] args) => _Infix("Cat", args, (x, y) => _MT._ToStr(x) + _MT._ToStr(y));
        public _Expr Join(params object[] args)
        {
            var delimExpr = _MT._ToExpr(args);
            return new _SingleExpr("Join", scope =>
            {
                var sb = new StringBuilder();
                String delim = _MT._ToStr(delimExpr._EvalOne(scope, ""));
                if(delim.Length == 0)
                {
                    _Eval(scope, v => sb.Append(_MT._ToStr(v)));
                }
                else
                {
                    bool useDelim = false;
                    _Eval(scope, v => {
                        if(useDelim) { sb.Append(delim); }
                        else         { useDelim = true; }
                        sb.Append(_MT._ToStr(v));
                    });
                }
                return sb.ToString();
            });
        }

        public _Expr To(params object[] args)
        {
            var endExpr = _MT._ToExpr(args);
            return _MT._CallExpr("To", (scope, dest) =>
            {
                long from = _MT._ToInt(_EvalOne(scope));
                long to = _MT._ToInt(endExpr._EvalOne(scope));
                for(long i = from; i < to; i++)
                {
                    dest(i);
                }
            });
        }

        public _Expr Ord()
            => _MT._CallExpr("Ord", (scope, dest) => _Eval(scope, v =>
            {
                bool surrogatePair = false;

                #pragma warning disable CA1861  // array mutated
                char[] surrogate = {'\0', '\0'};
                #pragma warning restore CA1861

                foreach(char ch in _MT._ToStr(v))
                {
                    if(Char.IsSurrogate(ch))
                    {
                        surrogate[Char.IsHighSurrogate(ch) ? 1 : 0] = ch;
                        if(surrogatePair)
                        {
                            dest((long)Char.ConvertToUtf32(surrogate[1], surrogate[0]));
                        }
                        surrogatePair = !surrogatePair;
                    }
                    else
                    {
                        dest((long)ch);
                    }
                }
            }));

        public _Expr Chr()
            => _Map("Chr", v =>
            {
                long cp = _MT._ToInt(v);
                if(0 <= cp && cp <= 0x10ffff && (cp < 0xd800 || 0xdfff < cp))
                {
                    return Char.ConvertFromUtf32((int)cp);
                }
                throw new MTException(String.Format("Chr: {0}0x{1} ({2}) is not a valid code point",
                    (cp < 0) ? "-" : "", Math.Abs(cp).ToString("x"), cp));
            });

        public _Expr RandShuffle()
            => _MT._ListExpr("RandShuffle", scope =>
            {
                var rand = _ExecState.Instance.Rand;
                var values = _EvalMutableList(scope);
                int len = values.Count;
                for(int i = 0; i < len - 1; i++)
                {
                    int j = rand.Next(i, len);
                    if(i != j)
                    {
                        object tmp = values[i];
                        values[i] = values[j];
                        values[j] = tmp;
                    }
                }
                return values;
            })
            ._SetStaticLength(_StaticLength);

        public _Expr RandChoose(params object[] args)
        {
            var nExpr = _MT._ToExpr(args);
            return _MT._CallExpr("RandChoose", (scope, dest) =>
            {
                var rand = _ExecState.Instance.Rand;
                var values = _EvalMutableList(scope);
                int len = values.Count;

                long n = _MT._ToInt(nExpr._EvalOne(scope, defaultValue: 1L));
                if(n == 0) { return; }

                if(n < 0 || n > len)
                {
                    throw new MTException(String.Format(
                        "RandChoose: cannot choose {0} value(s) from a {1}-value sequence", n, len));
                }

                for(int i = 0; i < n && i < len - 1; i++)
                {
                    int j = rand.Next(i, len);
                    if(i != j)
                    {
                        object tmp = values[i];
                        values[i] = values[j];
                        values[j] = tmp;
                    }
                    dest(values[i]);
                }

                if(n == len)
                {
                    dest(values[len - 1]);
                }
            });
        }

        public _Expr With(params object[] args)
        {
            var body = _MT._ToExpr(args);
            return _MT._CallExpr("With", (outerScope, dest) =>
            {
                var innerScope = new _Scope(outerScope);
                innerScope.Put(_SpecialKeys.IT, _EvalList(outerScope));
                body._Eval(innerScope, dest);
            })
            ._SetStaticLength(body._StaticLength);
        }

        public _Expr ForEach(params object[] args)
        {
            var body = _MT._ToExpr(args);
            return _MT._CallExpr("ForEach", (outerScope, dest) =>
            {
                var state = _ExecState.Instance;
                var innerScope = new _Scope(outerScope, outerScope.AllowedJumps | Mistletoe.Break.Bit | Mistletoe.Continue.Bit);
                try
                {
                    long idx = 0;
                    var itemVar = new List<object>{ 0 }; // 0 will be replaced, but there must be one initial element.
                    var idxVar = new List<object>{ 0L };
                    innerScope.Put(_SpecialKeys.ITEM, itemVar);
                    innerScope.Put(_SpecialKeys.IDX, idxVar);

                    _Eval(outerScope, v =>
                    {
                        itemVar[0] = v;
                        body._Eval(innerScope, dest);
                        state.EndJump(Mistletoe.Continue);
                        if(state.CurrentJump != null)
                        {
                            throw _StopIteration.EX;
                        }
                        idx++;
                        idxVar[0] = idx;
                    });
                }
                catch(_StopIteration) {}
                finally
                {
                    state.EndJump(Mistletoe.Break);
                }
            })
            ._SetStaticLength((_StaticLength == UNKNOWN_LENGTH || body._StaticLength == UNKNOWN_LENGTH)
                ? UNKNOWN_LENGTH
                : (_StaticLength * body._StaticLength));
        }

        public _Expr Eval()
            => _MT._CallExpr("Eval", (outerScope, dest) => _Eval(outerScope, v =>
                new _Parser("(" + _MT._ToStr(v) + ")").ParseList(_Parser.SyntaxMode.NORMAL)._Eval(new _Scope(outerScope), dest)));

        public _Expr Print() => _MT._VoidExpr("Print", scope => _Eval(scope, Console.Write));
        public _Expr Done() => _MT._VoidExpr("Done", scope => _Eval(scope, v => {}));
        public _Expr Note(params object[] _args) => this;
    }

    public class _VExpr : _Expr
    {
        private _Expr _keyExpr;
        internal _VExpr(_Expr keyExpr) : base("V")
        {
            _keyExpr = keyExpr;
        }

        internal override void _Eval(_Scope scope, _DestFn dest)
        {
            _keyExpr._Eval(scope, k => scope.Get(k).ForEach(v => dest(v)));
        }

        public _Expr Pop()
        {
            return _MT._CallExpr("Pop", (scope, dest) => _keyExpr._Eval(scope, k =>
            {
                var list = scope.Get(k);
                if(list.Count > 0)
                {
                    dest(list[0]);
                    list.RemoveAt(0);
                }
            }));
        }

        public _Expr Set(params object[] valueArgs)
            => _MT._Assign("Set", _keyExpr, _MT._ToExpr(valueArgs),
                        (scope, k, v) => scope.Put(k, v));

        public _Expr SetGlobal(params object[] valueArgs)
            => _MT._Assign("SetGlobal", _keyExpr, _MT._ToExpr(valueArgs),
                        (_scope, k, v) => _ExecState.Instance.GlobalScope.Put(k, v));

        public _Expr Update(params object[] valueArgs)
            => _MT._Assign("Update", _keyExpr, _MT._ToExpr(valueArgs),
                (scope, k, v) => {
                    var list = scope.Get(k);
                    list.Clear();
                    list.AddRange(v);
                });

        public _Expr Append(params object[] valueArgs)
            => _MT._Assign("Append", _keyExpr, _MT._ToExpr(valueArgs),
                        (scope, k, v) => scope.Get(k).AddRange(v));
    }

    public class _IfExpr // Does not derive from _Expr
    {
        private _Expr _condition;
        internal _IfExpr(_Expr condition)
        {
            _condition = condition;
        }

        public _ThenExpr Then(params object[] args)
            => new _ThenExpr(_condition, _MT._ToExpr(args));
    }

    public class _ThenExpr : _Expr
    {
        private _Expr _condition;
        private _Expr _thenBody;
        private _Expr _elseBody = _EMPTY;
        internal _ThenExpr(_Expr condition, _Expr thenBody) : base("If-Then")
        {
            _condition = condition;
            _thenBody = thenBody;
        }

        private _Expr Body(_Scope scope) => _MT._ToBool(_condition._EvalOne(scope)) ? _thenBody : _elseBody;

        internal override void _Eval(_Scope scope, _DestFn dest) => Body(scope)._Eval(scope, dest);
        internal override List<object> _EvalList(_Scope scope) => Body(scope)._EvalList(scope);
        internal override object _EvalOne(_Scope scope, object? defaultValue = null) => Body(scope)._EvalOne(scope, defaultValue);

        public _Expr Else(params object[] args)
        {
            _elseBody = _MT._ToExpr(args);

            long thenLen = _thenBody._StaticLength;
            long elseLen = _elseBody._StaticLength;
            if(thenLen != UNKNOWN_LENGTH && thenLen == elseLen)
            {
                _StaticLength = thenLen;
            }
            return this;
        }
    }

    public class _WhileExpr // does not derive from _Expr
    {
        private _Expr _condition;
        internal _WhileExpr(_Expr condition)
        {
            _condition = condition;
        }

        public _Expr Do(params object[] args)
        {
            var body = _MT._ToExpr(args);
            return _MT._CallExpr("While-Do", (outerScope, dest) =>
            {
                var state = _ExecState.Instance;
                var innerScope = new _Scope(outerScope, outerScope.AllowedJumps | Mistletoe.Break.Bit | Mistletoe.Continue.Bit);
                long idx = 0;
                var idxVar = new List<object>{idx};
                innerScope.Put(_SpecialKeys.IDX, idxVar);
                try
                {
                    while(_MT._ToBool(_condition._EvalOne(innerScope)))
                    {
                        body._Eval(innerScope, dest);
                        state.EndJump(Mistletoe.Continue);
                        if(state.CurrentJump != null)
                        {
                            break;
                        }
                        idx++;
                        idxVar[0] = idx;
                    }
                }
                finally
                {
                    state.EndJump(Mistletoe.Break);
                }
            });
        }
    }

    public class _TryExpr : _Expr
    {
        private _Expr _tryBody;
        private _Expr _finallyBody = _EMPTY;
        private List<(_Expr,_Expr)> _catches = new List<(_Expr,_Expr)>();

        internal _TryExpr(_Expr tryBody) : base("Try")
        {
            _tryBody = tryBody;
        }

        internal void _AddCatch(_Expr catchPattern, _Expr catchBody)
            => _catches.Add((catchPattern, catchBody));

        public _CatchExpr Catch(params object[] args)
            => new _CatchExpr(this, _MT._ToExpr(args));

        public _Expr Finally(params object[] args)
        {
            _finallyBody = _MT._ToExpr(args);
            return this;
        }

        internal override void _Eval(_Scope scope, _DestFn dest)
            => _EvalList(scope).ForEach(v => dest(v));

        internal override List<object> _EvalList(_Scope scope)
        {
            if(_catches.Count == 0 && _finallyBody == null)
            {
                throw new MTException("Try: expected at least one Catch and/or a Finally");
            }

            var values = _tryBody._EvalList(scope);
            var state = _ExecState.Instance;
            var match = false;
            if(state.EndJump(_Jump.Throw))
            {
                scope.Put(_SpecialKeys.EX, state.Exception!);
                scope.Put(_SpecialKeys.PARTIAL, values);

                foreach(var (patternExpr, cBody) in _catches)
                {
                    var pattern = patternExpr._EvalList(scope);
                    int pSize = pattern.Count;
                    if(state.Exception!.Count >= pSize && state.Exception!.GetRange(0, pSize).SequenceEqual(pattern))
                    {
                        state.Exception = null;
                        values = cBody._EvalList(scope);
                        match = true;
                        break;
                    }
                }
                if(!match)
                {
                    state.CurrentJump = _Jump.Throw;
                    values.Clear();
                }
            }
            _finallyBody._Eval(scope, values.Add);
            return values;
        }
    }

    public class _CatchExpr  // Does not derive from _Expr
    {
        private _TryExpr _tryExpr;
        private _Expr _catchPattern;

        internal _CatchExpr(_TryExpr tryExpr, _Expr catchPattern)
        {
            _tryExpr = tryExpr;
            _catchPattern = catchPattern;
        }

        public _TryExpr Do(params object[] args)
        {
            _tryExpr._AddCatch(_catchPattern, _MT._ToExpr(args));
            return _tryExpr;
        }
    }


    #pragma warning disable CA1032  // internal mechanism only
    internal sealed class _StopIteration : Exception
    {
        internal static readonly _StopIteration EX = new _StopIteration();
    }
    #pragma warning restore CA1032

    internal sealed class _GeneralCallExpr : _Expr
    {
        private _EvalFn _fn;
        internal _GeneralCallExpr(string name, _EvalFn fn) : base(name)
        {
            _fn = fn;
        }
        internal override void _Eval(_Scope scope, _DestFn dest)
            => _fn(scope, dest);
    }

    internal sealed  class _GeneralListExpr : _Expr
    {
        private _EvalListFn _fn;
        private _EvalListFn _mutableListFn;
        internal _GeneralListExpr(string name, _EvalListFn fn, _EvalListFn mutableListFn) : base(name)
        {
            _fn = fn;
            _mutableListFn = mutableListFn;
        }

        internal override void _Eval(_Scope scope, _DestFn dest)      => _fn(scope).ForEach(v => dest(v));
        internal override List<object> _EvalList(_Scope scope)        => _fn(scope);
        internal override List<object> _EvalMutableList(_Scope scope) => _mutableListFn(scope);
    }

    internal sealed class _SingleExpr : _Expr
    {
        private _EvalOneFn _fn;
        internal _SingleExpr(string name, _EvalOneFn fn) : base(name)
        {
            _SetStaticLength(1L);
            _fn = fn;
        }

        internal override object _EvalOne(_Scope scope, object? defaultValue = null)
            => _fn(scope);

        internal override List<object> _EvalList(_Scope scope)
            => new _Vals{ _fn(scope) };

        internal override void _Eval(_Scope scope, _DestFn dest)
            => dest(_fn(scope));
    }

    internal sealed class _Vals : List<object>
    {
        public override string ToString()
        {
            return "[" + String.Join(", ", this.Select(_MT._Repr)) + "]";
        }
    }

    internal sealed class _Scope
    {
        private _Scope? _parent;
        private Dictionary<object,List<object>> _vars = new Dictionary<object,List<object>>();
        internal int AllowedJumps { get; }

        internal _Scope(_Scope? parent, int allowedJumps)
        {
            _parent = parent;
            AllowedJumps = allowedJumps;
        }

        internal _Scope(_Scope parent)
        {
            _parent = parent;
            AllowedJumps = parent.AllowedJumps;
        }

        internal List<object> Get(object key)
        {
            return GetOptional(key) ?? throw new MTException(String.Format(
                "Variable \u201c{0}\u201d ({1}-named) is not defined", _MT._ToStr(key), _MT._TypeName(key)));
        }

        internal List<object>? GetOptional(object key)
        {
            List<object>? value;
            if(_vars.TryGetValue(key, out value))
            {
                return value;
            }
            return (_parent == null) ? null : _parent.GetOptional(key);
        }

        internal void Put(object key, List<object> value)
        {
            _vars[key] = value;
        }

        public override string ToString() => _vars + " -> " + _parent;
    }

    public sealed class _Jump
    {
        internal static readonly _Jump Throw = new _Jump("Throw()", 0x8);
        internal string Name { get; }
        internal int Bit { get; }
        internal _Jump(string name, int bit)
        {
            Name = name;
            Bit = bit;
        }
        public override string ToString() => Name;
    }

    internal sealed class _ExecState
    {
        private static ThreadLocal<_ExecState> s_instance = new ThreadLocal<_ExecState>(() => new _ExecState());
        internal static _ExecState Instance => s_instance.Value!;

        internal Random Rand { get; } = new Random();
        internal _Scope GlobalScope { get; } = new _Scope(null, 0);
        internal  _Jump? CurrentJump { get; set; } = null;
        internal List<object>? Exception { get; set; } = null;

        internal _ExecState()
        {
        }

        internal void Reset()
        {
            CurrentJump = null;
        }

        internal bool EndJump(_Jump j)
        {
            if(CurrentJump == j)
            {
                CurrentJump = null;
                return true;
            }
            return false;
        }
    }

    internal sealed class _FunctionDef
    {
        internal _Scope ParentScope { get; }
        internal _Expr Body { get; }

        internal _FunctionDef(_Scope parentScope, _Expr body)
        {
            ParentScope = parentScope;
            Body = body;
        }

        public override string ToString() => "Fn(...)";
    }

    internal sealed class _Tuple : List<object>
    {
        private int _hash = -1;
        internal _Tuple(List<object> values) : base(values) {}

        public override string ToString()
            => "T(" + String.Join(", ", this.Select(o => _MT._Repr(o))) + ")";

        public override bool Equals(object? other)
        {
            if(other is _Tuple otherT)
            {
                return this.SequenceEqual(otherT);
            }
            return false;
        }

        public override int GetHashCode()
        {
            if(_hash == -1)
            {
                _hash = 0;
                foreach(var v in this)
                {
                    _hash = (_hash * 37) + v.GetHashCode();
                }
            }
            return _hash;
        }
    }

    internal enum _SpecialKeys { VAL, ARG, THIS_FN, IDX, ITEM, IT, EX, PARTIAL }


    internal static class _MT
    {
        internal static _Expr _CallExpr(string name, _EvalFn fn)
            => new _GeneralCallExpr(name, fn);

        internal static _Expr _VoidExpr(string name, Action<_Scope> fn)
            => new _GeneralCallExpr(name, (scope, _dest) => fn(scope))._SetStaticLength(0L);

        internal static _Expr _ListExpr(string name, _EvalListFn fn)
            => new _GeneralListExpr(name, fn, fn);

        internal static _Expr _StoredListExpr(string name, object key)
            => new _GeneralListExpr(name, scope => scope.Get(key),
                                          scope => new List<object>(scope.Get(key)));

        internal static _Expr _ArgToExpr(object arg)
        {
            switch(arg)
            {
                case _Jump j:
                    return _VoidExpr(j.Name, scope =>
                    {
                        if((scope.AllowedJumps & j.Bit) == 0)
                        {
                            throw new MTException(j.Name + " not permitted here");
                        }
                        _ExecState.Instance.CurrentJump = j;
                    });

                case _Expr expr: return expr;
                case long l:     return new _SingleExpr("<int-literal>", _scope => l);
                case int i:      return new _SingleExpr("<int-literal>", _scope => (long)i);
                case double d when d % 1.0 == 0.0: return new _SingleExpr("<int-literal>",   _scope => (long)d);
                case double d:                     return new _SingleExpr("<float-literal>", _scope => d);
                case float f  when f % 1.0 == 0.0: return new _SingleExpr("<int-literal>",   _scope => (long)f);
                case float f:                      return new _SingleExpr("<float-literal>", _scope => (double)f);
                case string s:   return new _Parser(_EscapeStr(s, "%")).ParseStringLiteral();
                default:
                    throw new MTException(
                        String.Format("Unsupported host language value {0} \u201c{1}\u201d", arg.GetType().Name, arg));
            }
        }

        internal static _Expr _ToExpr(object[] args)
        {
            switch(args.Length)
            {
                case 0: return _Expr._EMPTY;
                case 1: return _ArgToExpr(args[0]);
                default:
                    var exprList = args.Select(_ArgToExpr).ToList();
                    long staticLen = 0L;
                    foreach(_Expr expr in exprList)
                    {
                        long len = expr._StaticLength;
                        if(len == _Expr.UNKNOWN_LENGTH)
                        {
                            staticLen = _Expr.UNKNOWN_LENGTH;
                            break;
                        }
                        staticLen += len;
                    }

                    return _CallExpr("<expr-list>", (scope, dest) =>
                    {
                        foreach(var expr in exprList)
                        {
                            expr._Eval(scope, dest);
                            if(_ExecState.Instance.CurrentJump != null)
                            {
                                break;
                            }
                        }
                    })
                    ._SetStaticLength(staticLen);
            }
        }

        internal static long _ToInt(object v)
        {
            switch(v)
            {
                case long l:   return l;
                case double d: return (long)d;
                case string s:
                    double parsed;
                    if(double.TryParse(s, out parsed)) { return (long)parsed; }
                    break;
            }
            throw new MTException(String.Format("Cannot convert {0} \u201c{1}\u201d to Int", _TypeName(v), _Repr(v)));
        }

        internal static double _ToFloat(object v)
        {
            switch(v)
            {
                case long l:   return (double)l;
                case double d: return d;
                case string s:
                    double parsed;
                    if(double.TryParse(s, out parsed)) { return parsed; }
                    break;
            }
            throw new MTException(String.Format("Cannot convert {0} \u201c{1}\u201d to Float", _TypeName(v), _Repr(v)));
        }

        internal static bool _ToBool(object v)
        {
            if(v is bool b) { return b; }
            throw new MTException(String.Format("Expected Bool but found {0} \u201c{1}\u201d", _TypeName(v), _Repr(v)));
        }

        internal static string _ToStr(object v)
        {
            switch(v)
            {
                case string s: return s;
                case bool b:   return b ? "TRUE" : "FALSE";
                case double d:
                    if(double.IsNaN(d))                   { return "MNaN"; }
                    else if(double.IsPositiveInfinity(d)) { return "Inf"; }
                    else if(double.IsNegativeInfinity(d)) { return "Inf.Neg()"; }
                    else                                  { return d.ToString("0.0###############"); }
            }
            return v.ToString()!;
        }

        internal static string _EscapeStr(string s, string percent)
        {
            var sb = new StringBuilder("\"");
            foreach(char ch in s)
            {
                switch(ch)
                {
                    case '\t': sb.Append("\\t"); break;
                    case '\n': sb.Append("\\n"); break;
                    case '\r': sb.Append("\\r"); break;
                    case '"':  sb.Append("\\\""); break;
                    case '\\': sb.Append("\\\\"); break;
                    case '%':  sb.Append(percent); break;
                    default:   sb.Append(ch); break;
                }
            }
            sb.Append('"');
            return sb.ToString();
        }

        internal static string _Repr(object v)
            => (v is string) ? _EscapeStr((string)v, "%%") : _ToStr(v);

        internal static string _ValsRepr(List<object> vals)
            => "[" + String.Join(", ", vals.Select(o => _Repr(o))) + "]";

        internal static string _TypeName(object v)
        {
            switch(v)
            {
                case long l:         return "Int";
                case double d:       return "Float";
                case string s:       return "Str";
                case bool b:         return "Bool";
                case _Tuple t:       return "Tuple";
                case _FunctionDef f: return "Fn";
                case _SpecialKeys s: return "SpecialKey";
                default:
                    throw new MTException("Internal error");
            }
        }

        internal static bool _Eq(object x, object y)
        {
            if((x is long || x is double) && (y is long || y is double))
            {
                return Convert.ToDouble(x) == Convert.ToDouble(y);
            }
            return x.Equals(y);
        }

        internal static Func<object,object,object> _Compare(string name,
                                                           Func<double,double,bool> floatTest,
                                                           Func<int,bool> strTest)
            => (x, y) =>
            {
                if((x is long || x is double) && (y is long || y is double))
                {
                    return floatTest(_ToFloat(x), _ToFloat(y));
                }
                if(x is string xs && y is string ys)
                {
                    return strTest(xs.CompareTo(ys));
                }
                throw new MTException(String.Format("{0}: {1} \u201c{2}\u201d and {3} \u201c{4}\u201d are not comparable",
                    name, _TypeName(x), _Repr(x), _TypeName(y), _Repr(y)));
            };

        internal static Func<object,object,object> _IntOrFloat(Func<long,long,object> intFn,
                                                              Func<double,double,object> floatFn)
            => (x, y) => {
                if(x is long xl && y is long yl) { return intFn(xl, yl); }
                return floatFn(_ToFloat(x), _ToFloat(y));
            };

        internal static double _NonZeroFloat(double n)
        {
            if(n == 0.0) { throw new MTException("Division by zero"); }
            return n;
        }

        internal static long _NonZeroInt(long n)
        {
            if(n == 0L) { throw new MTException("Division by zero"); }
            return n;
        }

        internal static _Expr _Assign(string name, _Expr keyExpr, _Expr valueExpr, Action<_Scope,object,List<object>> mutator)
            => _VoidExpr(name, outerScope =>
            {
                var keys = keyExpr._EvalList(outerScope);
                if(keys.Count == 0) { throw new MTException("Cannot assign to an empty list of variables"); }

                var state = _ExecState.Instance;
                var oldValues = keys.SelectMany(k => outerScope.GetOptional(k) ?? Enumerable.Empty<object>()).ToList();

                var innerScope = new _Scope(outerScope);
                innerScope.Put(_SpecialKeys.VAL, oldValues);
                var newValues = valueExpr._EvalList(innerScope);

                var valueIt = newValues.GetEnumerator();
                int nKeys = keys.Count;
                List<object> valueList;
                for(int i = 0; i < nKeys - 1; i++)
                {
                    valueList = new _Vals();
                    if(valueIt.MoveNext())
                    {
                        valueList.Add(valueIt.Current);
                    }
                    mutator(outerScope, keys[i], valueList);
                }

                valueList = new _Vals();
                while(valueIt.MoveNext())
                {
                    valueList.Add(valueIt.Current);
                }
                mutator(outerScope, keys[nKeys - 1], valueList);
            });

        internal static _Expr _Call(string name, _Expr fnExpr, _Expr fnArgsExpr)
            => _CallExpr(name, (outerScope, dest) =>
            {
                var fnArgs = fnArgsExpr._EvalList(outerScope);
                fnExpr._Eval(outerScope, fnIdent =>
                {
                    _FunctionDef fn;
                    if(fnIdent is _FunctionDef fn1)
                    {
                        fn = fn1;
                    }
                    else
                    {
                        var lookup = outerScope.GetOptional(fnIdent);
                        if(lookup != null && lookup.Count == 1 && lookup[0] is _FunctionDef fn2)
                        {
                            fn = fn2;
                        }
                        else
                        {
                            throw new MTException(String.Format("No function named \"{0}\"", fnIdent));
                        }
                    }

                    var state = _ExecState.Instance;
                    var innerScope = new _Scope(fn.ParentScope, Mistletoe.Return.Bit);
                    try
                    {
                        innerScope.Put(_SpecialKeys.ARG, fnArgs);
                        innerScope.Put(_SpecialKeys.THIS_FN, new List<object>{fn});
                        fn.Body._Eval(innerScope, dest);
                    }
                    finally
                    {
                        state.EndJump(Mistletoe.Return);
                    }
                });
            });
    }

    internal sealed class _Parser
    {
        private enum TT { OPEN = 0, CLOSE, COMMA, DOT, HEX_NUMBER, NUMBER, STR_DOUBLE, STR_SINGLE, KEYWORD, IDENTIFIER }

        private sealed class TTDetails
        {
            internal TT Type { get; }
            internal String Label { get; }
            internal Regex Pattern { get; }
            internal TTDetails(TT type, String label, String p) {
                Type = type;
                Label = label;
                Pattern = new Regex("\\G" + p);
            }
        }

        private static readonly TTDetails[] s_tokenTypes =
        {
            new TTDetails(TT.OPEN,       "\u201c(\u201d",  "\\("),
            new TTDetails(TT.CLOSE,      "\u201c)\u201d",  "\\)"),
            new TTDetails(TT.COMMA,      "\u201c,\u201d",  ","),
            new TTDetails(TT.DOT,        "\u201c.\u201d",  "\\."),
            new TTDetails(TT.HEX_NUMBER, "<hex-number>",   "[-+]?0x[0-9a-fA-F]+"),
            new TTDetails(TT.NUMBER,     "<number>",       "[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][+-]?[0-9]+)?"),
            new TTDetails(TT.STR_DOUBLE, "\u201c\"\u201d", "\""),
            new TTDetails(TT.STR_SINGLE, "\u201c'\u201d",  "'"),
            new TTDetails(TT.KEYWORD,    "<keyword>",      "[A-Z][a-zA-Z0-9]*"),
            new TTDetails(TT.IDENTIFIER, "<identifier>",   "[a-z][a-zA-Z0-9_]*")
        };

        internal enum SyntaxMode { NORMAL, STRING_EMBEDDED }

        private static readonly Regex ESCAPE = new Regex("[%DH(]|x[0-9a-fA-F]{2}|u[0-9a-fA-F]{4}|U[0-9a-fA-F]{6}");
        private const int ERROR_CONTEXT = 15;
        private string _input;
        private string _token = "";
        private int _pos = 0;

        internal _Parser(string input)
        {
            _input = input;
        }

        private static void AddLastStr(List<_Expr> components, StringBuilder sb)
        {
            var lastStr = sb.ToString();
            if(lastStr.Length > 0)
            {
                sb.Clear();
                components.Add(new _SingleExpr("<str-component>", _scope => lastStr));
            }
        }

        internal _Expr ParseStringLiteral()
        {
            var components = new List<_Expr>();
            var sb = new StringBuilder();
            int inputLen = _input.Length;
            int startPos = _pos;
            char delimCh = _input[_pos];
            _pos += 1;

            while(_pos < inputLen)
            {
                char ch = _input[_pos];
                if(ch == delimCh)
                {
                    _pos += 1;
                    AddLastStr(components, sb);
                    return new _SingleExpr("<str-literal>", scope => {
                        var finalSb = new StringBuilder();
                        foreach(var c in components)
                        {
                            c._Eval(scope, v => finalSb.Append(_MT._ToStr(v)));
                        }
                        return finalSb.ToString();
                    });
                }
                switch(ch)
                {
                    case '\\':
                        switch(((_pos + 1) < inputLen) ? _input[_pos + 1] : '\0')
                        {
                            case 't':  sb.Append('\t'); break;
                            case 'n':  sb.Append('\n'); break;
                            case 'r':  sb.Append('\r'); break;
                            case '"':  sb.Append('"');  break;
                            case '\\': sb.Append('\\'); break;
                            default:
                                throw ParseErr("illegal \\-escape");
                        }
                        _pos += 2;
                        break;

                    case '%':
                        var escapeMatch = ESCAPE.Match(_input, _pos + 1);
                        if(!escapeMatch.Success)
                        {
                            throw ParseErr("illegal %-escape (use \u201c%%\u201d for a literal \u201c%\u201d)");
                        }
                        var escape = escapeMatch.Value;
                        _pos += 1 + escape.Length;
                        switch(escape[0])
                        {
                            case '%':  sb.Append('%'); break;
                            case '\'': sb.Append('\''); break;
                            case 'D':  sb.Append('$'); break;
                            case 'H':  sb.Append('#'); break;
                            case 'x': case 'u': case 'U':
                                sb.Append(Char.ConvertFromUtf32(Convert.ToInt32(escape.Substring(1), 16)));
                                break;
                            case '(':
                                _pos -= 1;
                                AddLastStr(components, sb);
                                components.Add(ParseList(SyntaxMode.STRING_EMBEDDED));
                                break;
                            default:
                                throw new MTException("Internal error");
                        }
                        break;

                    case '"':
                        throw ParseErr("illegal char \u201c\"\u201d (use \\\")");

                    case '$': case '#':
                        throw ParseErr("illegal char \u201c{0}\u201d (use a %-escape)", ch);

                    default:
                        _pos += 1;
                        sb.Append(ch);
                        break;
                }
            }
            _pos = startPos;
            throw ParseErr("unclosed string literal");
        }

        internal object ParseOperator(object? expr, Type type, SyntaxMode syntaxMode)
        {
            var m = type.GetMethod(_token);
            if(m != null)
            {
                try
                {
                    switch(m.GetParameters().Length)
                    {
                        case 0:
                            Next(TT.OPEN);
                            Next(TT.CLOSE);
                            return m.Invoke(expr, Array.Empty<object>())!;
                        case 1:
                            return m.Invoke(expr, new object[]{ new object[]{ ParseList(syntaxMode) }})!;
                        default:
                            throw new MTException("Internal error");
                    }
                }
                catch(TargetInvocationException e)
                {
                    if(e.InnerException is MTException mte)
                    {
                        throw mte;
                    }
                    throw;
                }
            }
            var f = type.GetField(_token);
            if(f == null)
            {
                _pos -= _token.Length;
                throw ParseErr("unexpected symbol \u201c{0}\u201d", _token);
            }
            return f.GetValue(expr)!;
        }

        internal _Expr ParseList(SyntaxMode syntaxMode)
        {
            TT strTT  = (syntaxMode == SyntaxMode.NORMAL) ? TT.STR_DOUBLE : TT.STR_SINGLE;
            TT? identifierTT = (syntaxMode == SyntaxMode.NORMAL) ? (TT?)null : TT.IDENTIFIER;

            var args = new List<object>();
            Next(TT.OPEN);
            TT tt = Next(TT.HEX_NUMBER, TT.NUMBER, strTT, identifierTT, TT.KEYWORD, TT.CLOSE);
            while(tt != TT.CLOSE)
            {
                switch(tt)
                {
                    case TT.NUMBER:
                        args.Add(double.Parse(_token));
                        break;
                    case TT.HEX_NUMBER:
                        int idx = (_token[0] == '0') ? 2 : 3;
                        long sign = (_token[0] == '-') ? -1 : 1;
                        args.Add(sign * long.Parse(_token.Substring(idx), NumberStyles.AllowHexSpecifier));
                        break;
                    case TT.STR_DOUBLE:
                    case TT.STR_SINGLE:
                        _pos -= _token.Length;
                        args.Add(ParseStringLiteral());
                        break;

                    case TT.IDENTIFIER:
                    case TT.KEYWORD:
                        object expr;
                        if(tt == TT.IDENTIFIER)
                        {
                            var varName = _token;
                            // expr = _ListExpr("<var>", scope => scope.Get(varName));
                            expr = new _VExpr(new _SingleExpr("<identifier>", _s => varName));
                        }
                        else
                        {
                            expr = ParseOperator(null, typeof(Mistletoe), syntaxMode);
                        }

                        if(expr is _Jump)
                        {
                            args.Add(expr);
                        }
                        else
                        {
                            while(Next(TT.DOT, TT.COMMA, TT.CLOSE) == TT.DOT)
                            {
                                Next(TT.KEYWORD);
                                expr = ParseOperator(expr, expr.GetType(), syntaxMode);
                            }
                            _pos -= _token.Length;
                            args.Add(expr as _Expr ?? throw ParseErr("incomplete expression"));
                        }
                        break;

                    default: throw new MTException("Internal error");
                }
                tt = Next(TT.COMMA, TT.CLOSE);
                if(tt == TT.COMMA)
                {
                    tt = Next(TT.HEX_NUMBER, TT.NUMBER, strTT, identifierTT, TT.KEYWORD);
                }
            }
            return _MT._ToExpr(args.ToArray());
        }

        private TT Next(params TT?[] expectedTokens)
        {
            int inputLen = _input.Length;

            while(_pos < inputLen && char.IsWhiteSpace(_input[_pos]))
            {
                _pos++;
            }
            foreach(var tokenType in expectedTokens)
            {
                if(tokenType == null) { continue; }
                var match = s_tokenTypes[(int)tokenType].Pattern.Match(_input, _pos);
                if(match.Success)
                {
                    _token = match.Value;
                    _pos += _token.Length;
                    return (TT)tokenType;
                }
            }

            var displayTokens = expectedTokens
                .Where(tt => tt != null)
                .Select(tt => s_tokenTypes[(int)(tt!)].Label)
                .ToList();
            if(displayTokens.Count == 1)
            {
                throw ParseErr("expected {0}", displayTokens[0]);
            }
            throw ParseErr("expected one of {0}", String.Join(", ", displayTokens));
        }

        private MTException ParseErr(string format, params object[] args)
        {
            int p = _pos;
            string inp = _input;
            if(inp.StartsWith('(') && inp.EndsWith(')'))
            {
                p -= 1;
                inp = inp.Substring(1, inp.Length - 2);
            }

            int line = 1 + inp.Substring(0, p).Count(ch => ch == '\n');
            int col = (p == 0) ? 1 : (p - inp.LastIndexOf('\n', p - 1));

            int preCutoff = Math.Max(0, p - ERROR_CONTEXT);
            // string preContext = (preCutoff > 0 ? "..." : "") + inp.Substring(preCutoff, p - preCutoff);
            string preContext = string.Concat(preCutoff > 0 ? "..." : "",
                                              inp.AsSpan(preCutoff, p - preCutoff));

            int postCutoff = Math.Min(inp.Length, p + ERROR_CONTEXT);
            // string postContext = inp.Substring(p, postCutoff - p) + (postCutoff < inp.Length ? "..." : "");
            string postContext = string.Concat(inp.AsSpan(p, postCutoff - p),
                                               postCutoff < inp.Length ? "..." : "");

            return new MTException(String.Format("Parsing error at line {0}, col {1}: {2} in \u201c{3}\u25b6{4}\u201d",
                line, col, String.Format(format, args), preContext, postContext));
        }
    }
}
