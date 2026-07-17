#include "mt_expr.h"

#include "mt_execstate.h"
#include "mt_common.h"
#include "mt_function_val.h"
#include "mt_parser.h"
#include "mt_scope.h"
#include "mt_tuple_val.h"
#include <chrono>
#include <iostream>
#include <random>
#include <unordered_set>


#define INFIX_IMPL(return_type, owner_type, name) \
    _Operator owner_type ## _ ## name ## _infix_op ( \
        #name , _Operator::ExprType:: owner_type ## T , _Operator::ExprType:: return_type ## T , true, \
        [](ptr<_ExprBase> left, ptr<_Expr> right) -> ptr<_ExprBase> { \
            auto _left = std::static_pointer_cast< owner_type >(left); \
            return _left-> name ## _impl (_left, right); \
        }); \
    return_type& owner_type :: name ## _ref (ptr<_Expr> right_expr) { \
        return save(name ## _impl (use(*this), right_expr)); \
    } \
    ptr< return_type > owner_type :: name ## _impl (ptr< owner_type > left_expr, ptr<_Expr> right_expr)

#define POSTFIX_IMPL(return_type, owner_type, name) \
    _Operator owner_type ## _ ## name ## _map_op ( \
        #name , _Operator::ExprType:: owner_type ## T , _Operator::ExprType:: return_type ## T , false, \
        [](ptr<_ExprBase> left, ptr<_Expr> _right) -> ptr<_ExprBase> { \
            auto _left = std::static_pointer_cast< owner_type >(left); \
            return _left-> name ## _impl (_left); \
        }); \
    return_type& owner_type :: name ## _ref () { \
        return save(name ## _impl (use(*this))); \
    } \
    ptr< return_type > owner_type :: name ## _impl (ptr< owner_type > left_expr)

#define PREFIX_IMPL(return_type, name) \
    _Operator name ## _prefix_op ( \
        #name , _Operator::ExprType::Nil, _Operator::ExprType:: return_type ## T , true, \
        [](ptr<_ExprBase> _left, ptr<_Expr> right) -> ptr<_ExprBase> { \
            return name ## _impl (right); \
        }); \
    return_type& name ## _ref (ptr<_Expr> right_expr) { \
        return save(name ## _impl (right_expr)); \
    } \
    ptr< return_type > name ## _impl (ptr<_Expr> right_expr)


#define KW_VALUE_IMPL(name, value) \
    _StaticExpr name (value); \
    _Operator name ## _kwv_op ( \
        #name , _Operator::ExprType::Nil, _Operator::ExprType::_ExprT , false, \
        [](ptr<_ExprBase> _left, ptr<_Expr> _right) -> ptr<_ExprBase> { \
            return name._static_self; \
        });



namespace mistletoe
{
    struct StopIteration {} StopIterationEx;

    ptr<_Expr> _call_expr(const char* name, _EvalFn fn, int64_t static_length)
    {
        return std::make_shared<_CallExpr>(name, fn, static_length);
    }

    ptr<_Expr> _list_expr(const char* name, _EvalListFn fn, int64_t static_length)
    {
        return std::make_shared<_ListExpr>(name, fn, fn, static_length);
    }

    ptr<_Expr> _stored_list_expr(const char* name, _EvalListFn fn, int64_t static_length)
    {
        return std::make_shared<_ListExpr>(
            name, fn,
            [=](ptr<_Scope> scope){ return std::make_shared<_Vals>(*fn(scope)); },
            static_length
        );
    }

    ptr<_Expr> _void_expr(const char* name, std::function<void(ptr<_Scope>)> fn)
    {
        return _call_expr(name, [=](ptr<_Scope> scope, _DestFn _dest){ fn(scope); }, 0);
    }

    ptr<_Expr> _single_expr(const char* name, std::function<ptr<_Val>(ptr<_Scope>)> fn)
    {
        return std::make_shared<_SingleExpr>(name, fn);
    }

    // --- class _Operator ---

    std::unordered_map<_Operator::ExprType,_Operator::ExprType> _Operator::super_types{
        {_Operator::Nil,            _Operator::Nil},
        {_Operator::_ExprT,         _Operator::Nil},
        {_Operator::_CallExprT,     _Operator::_ExprT},
        {_Operator::_ListExprT,     _Operator::_ExprT},
        {_Operator::_SingleExprT,   _Operator::_ExprT},
        {_Operator::_VExprT,        _Operator::_ExprT},
        {_Operator::_IfExprT,       _Operator::Nil},
        {_Operator::_ThenExprT,     _Operator::_ExprT},
        {_Operator::_WhileExprT,    _Operator::Nil},
        {_Operator::_TryExprT,      _Operator::_ExprT},
        {_Operator::_CatchExprT,    _Operator::Nil}
    };

    std::unordered_map<std::pair<_Operator::ExprType,std::string>,_Operator*,_Operator::Hash,_Operator::Eq> _Operator::operators{};

    size_t _Operator::Hash::operator()(const std::pair<_Operator::ExprType,std::string>& p) const
    {
        return p.first + 37 * std::hash<std::string>()(p.second);
    }

    bool _Operator::Eq::operator()(const std::pair<_Operator::ExprType,std::string>& p1,
                                   const std::pair<_Operator::ExprType,std::string>& p2) const
    {
        return p1.first == p2.first && p1.second == p2.second;
    }

    _Operator* _Operator::lookup(ExprType owner_type, std::string name)
    {
        do
        {
            auto op_it = operators.find(std::pair(owner_type, name));
            if(op_it != operators.end())
            {
                return op_it->second;
            }
            owner_type = super_types[owner_type];
        }
        while(owner_type != Nil);
        return nullptr;
    }

    bool _Operator::type_is_full_expression(ExprType type)
    {
        while(type != ExprType::Nil && type != ExprType::_ExprT)
        {
            type = super_types[type];
        }
        return type == ExprType::_ExprT;
    }

    _Operator::_Operator(std::string name, ExprType owner_type, ExprType return_type, bool right_operand,
                         std::function<ptr<_ExprBase>(ptr<_ExprBase>,ptr<_Expr>)> fn)
        : owner_type(owner_type), return_type(return_type), right_operand(right_operand), fn(fn)
    {
        operators[std::pair(owner_type, name)] = this;
    }

    _Operator::ExprType _Operator::get_return_type() const
    {
        return return_type;
    }

    bool _Operator::has_right_operand() const
    {
        return right_operand;
    }

    ptr<_ExprBase> _Operator::call(ptr<_ExprBase> left, ptr<_Expr> right) const
    {
        return fn(left, right);
    }

    // --- helper functions ---

    template<class T>
    T& save(ptr<T> p)
    {
        _ExprBase::tmp_alloc[p->_id] = p;
        return *p;
    }

    _Expr& static_expr(ptr<_Expr> p)
    {
        p->_static_self = p;
        return *p;
    }


    template<class T>
    ptr<T> use(const T& ref)
    {
        if(ref._static_self)
        {
            return std::static_pointer_cast<T>(ref._static_self);
        }

        auto tmp_it = _ExprBase::tmp_alloc.find(ref._id);
        if(tmp_it != _ExprBase::tmp_alloc.end())
        {
            auto p = std::static_pointer_cast<T>(tmp_it->second);
            _ExprBase::tmp_alloc.erase(tmp_it);
            return p;
        }
        throw MTException() << ref._name << " (" << ref._id << "): internal error in use(): _Expr not previously save()'d.";
    }

    ptr<_Expr> _assign(const char* name, ptr<const _Expr> key_expr, ptr<const _Expr> value_expr,
                       std::function<void(ptr<_Scope>,ptr<_Val>,ptr<_Vals>)> mutator)
    {
        return _void_expr(name, [=](ptr<_Scope> outer_scope)
        {
            ptr<_Vals> keys{key_expr->_eval_list(outer_scope)};
            size_t n_keys = keys->size();
            if(n_keys == 0)
            {
                throw MTException() << "Cannot assign to an empty list of variables";
            }

            ptr<_Vals> old_values{std::make_shared<_Vals>()};
            for(ptr<_Val> k : *keys)
            {
                ptr<_Vals> v = outer_scope->get_optional(k);
                if(v)
                {
                    old_values->extend(v);
                }
            }

            ptr<_Scope> inner_scope{std::make_shared<_Scope>(outer_scope)};
            inner_scope->put(_SpecialKeyV::VAL, old_values);
            ptr<_Vals> new_values{value_expr->_eval_list(inner_scope)};

            auto value_it = new_values->begin();
            ptr<_Vals> assign_vals{std::make_shared<_Vals>()};
            for(size_t i = 0; i < n_keys - 1; i++)
            {
                if(value_it != new_values->end())
                {
                    assign_vals->push_back(*value_it);
                    value_it++;
                }
                mutator(outer_scope, (*keys)[i], assign_vals);
                assign_vals = std::make_shared<_Vals>();
            }

            assign_vals->insert(assign_vals->begin(), value_it, new_values->end());
            mutator(outer_scope, (*keys)[n_keys - 1], assign_vals);
        });
    }

    std::function<ptr<_Val>(ptr<_Val>,ptr<_Val>)> _int_or_float(std::function<int64_t(int64_t,int64_t)> int_fn,
                                                                std::function<double(double,double)> float_fn)
    {
        return [=](ptr<_Val> x, ptr<_Val> y) -> ptr<_Val>
        {
            if(x->val_type() == _Val::IntT && y->val_type() == _Val::IntT)
            {
                return _IntV::of(int_fn(x->to_int(), y->to_int()));
            }
            else
            {
                return _FloatV::of(float_fn(x->to_float(), y->to_float()));
            }
        };
    }

    std::function<ptr<_Val>(ptr<_Val>)> _int_or_float(std::function<int64_t(int64_t)> int_fn,
                                                      std::function<double(double)> float_fn)
    {
        return [=](ptr<_Val> v) -> ptr<_Val> {
            switch(v->val_type())
            {
                case _Val::IntT:   return _IntV::of(int_fn(v->to_int()));
                case _Val::FloatT: return _FloatV::of(float_fn(v->to_float()));
                default: throw MTException() << "Unary operation requires an integer or float";
            }
        };
    }

    template<class N> N _non_zero(N n)
    {
        if(n == (N)0) { throw MTException() << "Division by zero"; }
        return n;
    }

    template<template<class> class T>
    ptr<_Val> _compare(const char* name, ptr<_Val> x, ptr<_Val> y)
    {
        auto xt = x->val_type();
        auto yt = y->val_type();
        if(    ((xt == _Val::IntT) || (xt == _Val::FloatT))
            && ((yt == _Val::IntT) || (yt == _Val::FloatT)))
        {
            return _BoolV::of(T<double>()(x->to_float(), y->to_float()));
        }
        else if(xt == _Val::StrT && yt == _Val::StrT)
        {
            return _BoolV::of(T<std::string>()(x->to_str(), y->to_str()));
        }
        throw MTException() << name << ": "
            << x->type_name() << u8" \u201c" << x->repr() << u8"\u201d and "
            << y->type_name() << u8" \u201c" << y->repr() << u8"\u201d are not comparable";
    }

    ptr<_Expr> _call(const char* name, ptr<const _Expr> fn_expr, ptr<const _Expr> fn_args_expr)
    {
        return _call_expr(name, [=](ptr<_Scope> outer_scope, _DestFn dest)
        {
            ptr<_Vals> fn_args{fn_args_expr->_eval_list(outer_scope)};
            fn_expr->_eval(outer_scope, [&](ptr<_Val> fn_ident)
            {
                ptr<_Val> fn_ptr;
                if(fn_ident->val_type() == _Val::FunctionT)
                {
                    fn_ptr = fn_ident;
                }
                else
                {
                    ptr<_Vals> lookup = outer_scope->get(fn_ident);
                    if(lookup && lookup->size() == 1 && (*lookup)[0]->val_type() == _Val::FunctionT)
                    {
                        fn_ptr = (*lookup)[0];
                    }
                    else
                    {
                        throw MTException() << "No function labelled \"" << *fn_ident << "\"";
                    }
                }

                const _FunctionV* fn = static_cast<const _FunctionV*>(&*fn_ptr);

                ptr<_Scope> inner_scope{std::make_shared<_Scope>(fn->parent_scope, Return.bit)};
                inner_scope->put(_SpecialKeyV::ARG, fn_args);
                inner_scope->put(_SpecialKeyV::THIS_FN, std::make_shared<_Vals>(_Vals{fn_ptr}));

                struct Restore{
                    ~Restore() { _ExecState::instance.end_jump(Return); }
                } _restore;
                fn->body->_eval(inner_scope, dest);
            });
        });
    }

    // --- to_expr ---


    ptr<_Expr> _arg_to_expr(_Jump j)
    {
        return _void_expr(j.name, [=](ptr<_Scope> scope)
        {
            if((scope->allowed_jumps & j.bit) == 0)
            {
                throw MTException() << j.name << " not permitted here";
            }
            _ExecState::instance.current_jump = j.bit;
        });
    }

    ptr<_Expr> _arg_to_expr(const _Expr& e)
    {
        return use(e);
    }

    ptr<_Expr> _arg_to_expr(ptr<_Expr> e)
    {
        return e;
    }

    ptr<_Expr> _arg_to_expr(double v)
    {
        if(fmod(v, 1.0) == 0.0)
        {
            int64_t i = v;
            return _single_expr("<int-literal>", [=](ptr<_Scope> _s){ return _IntV::of(i); });
        }
        else
        {
            return _single_expr("<float-literal>", [=](ptr<_Scope> _s){ return _FloatV::of(v); });
        }
    }

    ptr<_Expr> _arg_to_expr(int v)
    {
        return _single_expr("<int-literal>", [=](ptr<_Scope> _s){ return _IntV::of((uint64_t)v); });
    }

    ptr<_Expr> _arg_to_expr(uint64_t v)
    {
        return _single_expr("<int-literal>", [=](ptr<_Scope> _s){ return _IntV::of(v); });
    }

    ptr<_Expr> _arg_to_expr(const char* v)
    {
        std::stringstream sb;
        _StrV::escape(sb, v, "%");
        return _Parser(sb.str()).parse_string_literal();
    }

    ptr<_Expr> _expr_cat(ptr<std::vector<ptr<_Expr>>> expr_list)
    {
        switch(expr_list->size())
        {
            case 0: return _Expr::_EMPTY;
            case 1: return (*expr_list)[0];
            default:
                int64_t static_len = 0;
                for(ptr<_Expr>& expr : *expr_list)
                {
                    int64_t len = expr->_static_length;
                    if(len == _Expr::_UNKNOWN_LENGTH)
                    {
                        static_len = _Expr::_UNKNOWN_LENGTH;
                        break;
                    }
                    static_len += len;
                }

                return _call_expr("<expr-list>",
                    [=](ptr<_Scope> scope, _DestFn dest)
                    {
                        for(ptr<_Expr>& expr : *expr_list)
                        {
                            expr->_eval(scope, dest);
                            if(_ExecState::instance.current_jump != 0)
                            {
                                break;
                            }
                        }
                    },
                    static_len
                );
        }
    }


    // --- prefix operators ---

    PREFIX_IMPL(_Expr, Scope)
    {
        return _call_expr("Scope",
            [=](ptr<_Scope> outer_scope, _DestFn dest)
            {
                right_expr->_eval(std::make_shared<_Scope>(outer_scope), dest);
            },
            right_expr->_static_length);
    }

    PREFIX_IMPL(_Expr, C)
    {
        return right_expr;
    }

    PREFIX_IMPL(_Expr, I)
    {
        return right_expr->_map("I", right_expr, [](ptr<_Val> v) {
            return (v->val_type() == _Val::IntT) ? v : _IntV::of(v->to_int());
        });
    }

    PREFIX_IMPL(_Expr, F)
    {
        return right_expr->_map("F", right_expr, [](ptr<_Val> v) {
            return (v->val_type() == _Val::FloatT) ? v : _FloatV::of(v->to_float());
        });
    }

    PREFIX_IMPL(_Expr, S)
    {
        return right_expr->_map("S", right_expr, [](ptr<_Val> v) {
            return (v->val_type() == _Val::StrT) ? v : _StrV::of(v->to_str());
        });
    }

    PREFIX_IMPL(_Expr, T)
    {
        return _single_expr("T", [=](ptr<_Scope> scope){ return _TupleV::of(right_expr->_eval_list(scope)); });
    }

    PREFIX_IMPL(_Expr, Fn)
    {
        return _single_expr("Fn", [=](ptr<_Scope> scope){ return _FunctionV::of(right_expr, scope); });
    }

    PREFIX_IMPL(_VExpr, V)          { return std::make_shared<_VExpr>(right_expr); }
    PREFIX_IMPL(_IfExpr, If)        { return std::make_shared<_IfExpr>(right_expr); }
    PREFIX_IMPL(_WhileExpr, While)  { return std::make_shared<_WhileExpr>(right_expr); }
    PREFIX_IMPL(_TryExpr, Try)      { return std::make_shared<_TryExpr>(right_expr); }

    PREFIX_IMPL(_Expr, Throw)
    {
        return _void_expr("Throw", [=](ptr<_Scope> scope)
        {
            auto& state = _ExecState::instance;
            state.exception = right_expr->_eval_mutable_list(scope);
            state.current_jump = _Jump::Throw.bit;
        });
    }

    PREFIX_IMPL(_Expr, Note)
    {
        return _Expr::_EMPTY;
    }

    // --- class _ExprBase ---

    thread_local std::unordered_map<size_t,ptr<_ExprBase>> _ExprBase::tmp_alloc{};
    size_t _ExprBase::next_id{0};
    _ExprBase::_ExprBase(const char* name) : _id{next_id++}, _name{name} {}


    // --- class _Expr ---

    ptr<_Expr> _Expr::_EMPTY{use(static_expr(_void_expr("<empty>", [](ptr<_Scope> scope){})))};

    _Expr::_Expr(const char* name, int64_t static_length)
        : _ExprBase{name}, _static_length{static_length} {}

    ptr<_Vals> _Expr::staticLength() const
    {
        return std::make_shared<_Vals>(_Vals{_IntV::of(_static_length)});
    }

    ptr<_Vals> _Expr::_eval_list(ptr<_Scope> scope) const
    {
        ptr<_Vals> vals{std::make_shared<_Vals>()};
        _eval(scope, [&](ptr<_Val> v){ vals->push_back(v); });
        return vals;
    }

    ptr<_Vals> _Expr::_eval_mutable_list(ptr<_Scope> scope) const
    {
        return _eval_list(scope);
    }

    ptr<_Val> _Expr::_eval_one(ptr<_Scope> scope, ptr<_Val> def_v) const
    {
        ptr<_Val> single_value{nullptr};

        _eval(scope, [&](ptr<_Val> v)
        {
            if(single_value)
            {
                throw MTException() << "Expected one value but found multiple: "
                                    << single_value->repr() << ", " << v->repr() << ", ...";
            }
            single_value = v;
        });

        if(!single_value)
        {
            if(def_v)
            {
                single_value = def_v;
            }
            else
            {
                throw MTException() << "Expected one value but found zero";
            }
        }
        return single_value;
    }

    ptr<_Expr> _Expr::_infix(const char* name, ptr<_Expr> left, ptr<_Expr> right,
                             std::function<ptr<_Val>(const char*,ptr<_Val>,ptr<_Val>)> op)
    {
        int64_t llen = _static_length;
        int64_t rlen = right->_static_length;

        if(llen == 0 || rlen == 0)
        {
            return _EMPTY;
        }
        else if(llen == 1)
        {
            return _call_expr(name,
                [=](ptr<_Scope> scope, _DestFn dest)
                {
                    ptr<_Val> x = left->_eval_one(scope);
                    right->_eval(scope,[&,op,name](ptr<_Val> y){ dest(op(name, x, y)); });
                },
                rlen
            );
        }
        else if(rlen == 1)
        {
            return _call_expr(name,
                [=](ptr<_Scope> scope, _DestFn dest)
                {
                    auto y = right->_eval_one(scope);
                    left->_eval(scope, [&,op,name](ptr<_Val> x){ dest(op(name, x, y)); });
                },
                llen
            );
        }
        else
        {
            return _call_expr(name,
                [=](ptr<_Scope> scope, _DestFn dest)
                {
                    ptr<_Vals> operands1{left->_eval_list(scope)};
                    ptr<_Vals> operands2{right->_eval_list(scope)};

                    size_t len1 = operands1->size();
                    size_t len2 = operands2->size();
                    if(len1 == 0 || len2 == 0) { return; }

                    auto it1 = operands1->begin();
                    auto it2 = operands2->begin();
                    size_t longest = (len1 > len2) ? len1 : len2;

                    for(size_t _i = 0; _i < longest; _i++)
                    {
                        if(it1 == operands1->end())
                        {
                            it1 = operands1->begin();
                        }
                        if(it2 == operands2->end())
                        {
                            it2 = operands2->begin();
                        }
                        dest(op(name, *it1, *it2));
                        it1++;
                        it2++;
                    }
                },
                (llen == _UNKNOWN_LENGTH || rlen == _UNKNOWN_LENGTH) ? _UNKNOWN_LENGTH : std::max(llen, rlen)
            );
        }
    }

    ptr<_Expr> _Expr::_infix(const char* name, ptr<_Expr> left, ptr<_Expr> right,
                             std::function<ptr<_Val>(ptr<_Val>,ptr<_Val>)> op)
    {
        return _infix(name, left, right, [=](const char* _name, ptr<_Val> x, ptr<_Val> y){ return op(x, y); });
    }

    ptr<_Expr> _Expr::_map(const char* name, ptr<_Expr> left, std::function<ptr<_Val>(ptr<_Val>)> op)
    {
        return _call_expr(name, [=](ptr<_Scope> scope, _DestFn dest)
        {
            left->_eval(scope, [&](ptr<_Val> v){ dest(op(v)); });
        });
    }

    INFIX_IMPL(_Expr, _Expr, As)
    {
        return _assign("As", right_expr, left_expr,
                       [](ptr<_Scope> scope, ptr<_Val> k, ptr<_Vals> v) { scope->put(k, v); });
    }

    INFIX_IMPL(_Expr, _Expr, Call)  { return _call("Call", left_expr, right_expr); }
    INFIX_IMPL(_Expr, _Expr, Apply) { return _call("Apply", right_expr, left_expr); }

    INFIX_IMPL(_Expr, _Expr, Eq) {
        return _infix("Eq", left_expr, right_expr, [](ptr<_Val> x, ptr<_Val> y) { return _BoolV::of(*x == *y); });
    }

    INFIX_IMPL(_Expr, _Expr, Ne) {
        return _infix("Ne", left_expr, right_expr, [](ptr<_Val> x, ptr<_Val> y) { return _BoolV::of(*x != *y); });
    }

    INFIX_IMPL(_Expr, _Expr, Lt) { return _infix("Lt", left_expr, right_expr, _compare<std::less>); }
    INFIX_IMPL(_Expr, _Expr, Le) { return _infix("Le", left_expr, right_expr, _compare<std::less_equal>); }
    INFIX_IMPL(_Expr, _Expr, Gt) { return _infix("Gt", left_expr, right_expr, _compare<std::greater>); }
    INFIX_IMPL(_Expr, _Expr, Ge) { return _infix("Ge", left_expr, right_expr, _compare<std::greater_equal>); }

    INFIX_IMPL(_Expr, _Expr, In)
    {
        return _call_expr("In",
            [=](ptr<_Scope> scope, _DestFn dest)
            {
                std::unordered_set<ptr<_Val>,_Val::Hash,_Val::Eq> set{};
                right_expr->_eval(scope, [&](ptr<_Val> v){ set.insert(v); });
                left_expr->_eval(scope, [&](ptr<_Val> v)
                {
                    dest(_BoolV::of(set.count(v) == 1));
                });
            },
            _static_length
        );
    }

    INFIX_IMPL(_Expr, _Expr, NotIn) {
        auto in_expr = In_impl(left_expr, right_expr);
        return in_expr->Not_impl(in_expr);
    }

    POSTFIX_IMPL(_Expr, _Expr, IsNaN)
    {
        return _map("IsNaN", left_expr, [](ptr<_Val> v){ return _BoolV::of(std::isnan(v->to_float())); });
    }


    INFIX_IMPL(_Expr, _Expr, Plus) {
        return _infix("Plus", left_expr, right_expr, _int_or_float([](int64_t x, int64_t y) { return x + y; },
                                                                   [](double x, double y) { return x + y; }));
    }

    INFIX_IMPL(_Expr, _Expr, Minus) {
        return _infix("Minus", left_expr, right_expr, _int_or_float([](int64_t x, int64_t y) { return x - y; },
                                                                    [](double x, double y) { return x - y; }));
    }

    INFIX_IMPL(_Expr, _Expr, Times) {
        return _infix("Times", left_expr, right_expr, _int_or_float([](int64_t x, int64_t y) { return x * y; },
                                                                    [](double x, double y) { return x * y; }));
    }

    INFIX_IMPL(_Expr, _Expr, Div) {
        return _infix("Div", left_expr, right_expr, _int_or_float([](int64_t x, int64_t y) { return x / _non_zero(y); },
                                                                  [](double x, double y) { return x / _non_zero(y); }));
    }

    INFIX_IMPL(_Expr, _Expr, Mod) {
        return _infix("Mod", left_expr, right_expr, _int_or_float([](int64_t x, int64_t y) { return x % _non_zero(y); },
                                                       [](double x, double y) { return std::fmod(x, _non_zero(y)); }));
    }

    INFIX_IMPL(_Expr, _Expr, Pow) {
        return _infix("Pow", left_expr, right_expr, _int_or_float([](int64_t x, int64_t y) { return (int64_t)pow(x, y); },
                                                       [](double x, double y) { return pow(x, y); }));
    }

    POSTFIX_IMPL(_Expr, _Expr, Neg) {
        return _map("Neg", left_expr, _int_or_float([](int64_t v){ return -v; },
                                         [](double v){ return -v; }));
    }

    POSTFIX_IMPL(_Expr, _Expr, Abs) {
        return _map("Abs", left_expr, _int_or_float([](int64_t v){ return abs(v); },
                                         [](double v){ return abs(v); }));
    }

    INFIX_IMPL(_Expr, _Expr, And) {
        return _infix("And", left_expr, right_expr, [](ptr<_Val> x, ptr<_Val> y){ return _BoolV::of(x->to_bool() && y->to_bool()); });
    }

    INFIX_IMPL(_Expr, _Expr, Or) {
        return _infix("Or", left_expr, right_expr, [](ptr<_Val> x, ptr<_Val> y){ return _BoolV::of(x->to_bool() || y->to_bool()); });
    }

    POSTFIX_IMPL(_Expr, _Expr, Not) {
        return _map("Not", left_expr, [](ptr<_Val> v){ return _BoolV::of(!v->to_bool()); });
    }

    POSTFIX_IMPL(_Expr, _Expr, Int) {
        return _map("Int", left_expr, [](ptr<_Val> v){ return (v->val_type() == _Val::IntT) ? v : _IntV::of(v->to_int()); });
    }

    POSTFIX_IMPL(_Expr, _Expr, Float) {
        return _map("Float", left_expr, [](ptr<_Val> v){ return (v->val_type() == _Val::FloatT) ? v : _FloatV::of(v->to_float()); });
    }

    POSTFIX_IMPL(_Expr, _Expr, Str) {
        return _map("Str", left_expr, [](ptr<_Val> v){ return (v->val_type() == _Val::StrT) ? v : _StrV::of(v->to_str()); });
    }

    POSTFIX_IMPL(_Expr, _Expr, Tuple)
    {
        return _single_expr("Tuple", [=](ptr<_Scope> scope){ return _TupleV::of(left_expr->_eval_list(scope)); });
    }

    POSTFIX_IMPL(_Expr, _Expr, Flatten)
    {
        return _call_expr("Flatten", [=](ptr<_Scope> scope, _DestFn dest)
        {
            left_expr->_eval(scope, [&](ptr<_Val> v)
            {
                if(v->val_type() == _Val::TupleT)
                {
                    for(ptr<_Val> w : static_cast<_TupleV&>(*v).val)
                    {
                        dest(w);
                    }
                }
                else
                {
                    dest(v);
                }
            });
        });
    }

    INFIX_IMPL(_Expr, _Expr, At)
    {
        return _call_expr("At",
            [=](ptr<_Scope> scope, _DestFn dest)
            {
                ptr<_Vals> src{left_expr->_eval_list(scope)};
                int64_t len = (int64_t)src->size(); // NOTE: unsafe
                right_expr->_eval(scope, [&](ptr<_Val> v)
                {
                    int64_t idx = v->to_int();
                    if(idx < -len || idx >= len)
                    {
                        throw MTException() << "At: index " << idx << " is invalid for a " << len << "-value sequence";
                    }
                    if(idx < 0) { idx += len; }
                    dest((*src)[(size_t)idx]);
                });
            },
            right_expr->_static_length
        );
    }

    POSTFIX_IMPL(_Expr, _Expr, Len)
    {
        return _single_expr("Len", [=](ptr<_Scope> scope)
        {
            int64_t count = 0;
            left_expr->_eval(scope, [&](ptr<_Val> v){ count++; });
            return _IntV::of(count);
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, Sum)
    {
        return _single_expr("Sum", [=](ptr<_Scope> scope)
        {
            bool ints_only = true;
            int64_t int_sum = 0;
            double float_sum = 0.0;

            left_expr->_eval(scope, [&](ptr<_Val> v)
            {
                if(ints_only)
                {
                    if(v->val_type() == _Val::IntT)
                    {
                        int_sum += v->to_int();
                        return;
                    }
                    float_sum = int_sum;
                    ints_only = false;
                }
                float_sum += v->to_float();
            });

            return ints_only ? _IntV::of(int_sum) : _FloatV::of(float_sum);
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, All)
    {
        return _single_expr("All", [=](ptr<_Scope> scope)
        {
            try
            {
                left_expr->_eval(scope, [&](ptr<_Val> v)
                {
                    if(!v->to_bool())
                    {
                        throw StopIterationEx;
                    }
                });
                return _BoolV::TRUE_VALUE;
            }
            catch(StopIteration& ex)
            {
                return _BoolV::FALSE_VALUE;
            }
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, Any)
    {
        return _single_expr("Any", [=](ptr<_Scope> scope)
        {
            try
            {
                left_expr->_eval(scope, [&](ptr<_Val> v)
                {
                    if(v->to_bool())
                    {
                        throw StopIterationEx;
                    }
                });
                return _BoolV::FALSE_VALUE;
            }
            catch(StopIteration& ex)
            {
                return _BoolV::TRUE_VALUE;
            }
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, SLen)
    {
        return _map("SLen", left_expr, [](ptr<_Val> v)
        {
            int64_t count = 0;
            _find_code_points(v->to_str(), [&](int64_t _cp){ count++; });
            return _IntV::of(count);
        });
    }

    INFIX_IMPL(_Expr, _Expr, Cat) {
        return _infix("Cat", left_expr, right_expr, [](ptr<_Val> x, ptr<_Val> y) { return _StrV::of(x->to_str() + y->to_str()); });
    }

    INFIX_IMPL(_Expr, _Expr, Join)
    {
        return _single_expr("Join", [=](ptr<_Scope> scope)
        {
            std::stringstream sb;
            std::string delim = right_expr->_eval_one(scope, _StrV::EMPTY)->to_str();
            if(delim.size() == 0)
            {
                left_expr->_eval(scope, [&](ptr<_Val> v){ v->to_str(sb); });
            }
            else
            {
                bool use_delim = false;
                left_expr->_eval(scope, [&](ptr<_Val> v)
                {
                    if(use_delim) { sb << delim; }
                    else          { use_delim = true; }
                    v->to_str(sb);
                });
            }
            return _StrV::of(sb.str());
        });
    }


    INFIX_IMPL(_Expr, _Expr, To)
    {
        return _call_expr("To", [=](ptr<_Scope> scope, _DestFn dest)
        {
            int64_t from = left_expr->_eval_one(scope)->to_int();
            int64_t to =   right_expr->_eval_one(scope)->to_int();
            for(int64_t i = from; i < to; i++)
            {
                dest(_IntV::of(i));
            }
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, Ord)
    {
        return _call_expr("Ord", [=](ptr<_Scope> scope, _DestFn dest)
        {
            left_expr->_eval(scope, [&](ptr<_Val> v)
            {
                _find_code_points(v->to_str(), [&](int64_t cp){ dest(_IntV::of(cp)); });
            });
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, Chr)
    {
        return _map("Chr", left_expr, [](ptr<_Val> v){ return _StrV::of(_chr(v->to_int())); });
    }

    POSTFIX_IMPL(_Expr, _Expr, RandShuffle)
    {
        return _list_expr("RandShuffle",
            [=](ptr<_Scope> scope)
            {
                ptr<_Vals> src{left_expr->_eval_mutable_list(scope)};
                size_t len = src->size();
                if(len == 0) { return src; } // Cannot calc "len - 1" when len == 0, since it's unsigned.
                for(size_t i = 0; i < len - 1; i++)
                {
                    size_t j = std::uniform_int_distribution<size_t>(i, len - 1)(_ExecState::instance.rand_gen);
                    std::swap((*src)[i], (*src)[j]);
                }
                return src;
            },
            _static_length
        );
    }

    INFIX_IMPL(_Expr, _Expr, RandChoose)
    {
        return _call_expr("RandChoose", [=](ptr<_Scope> scope, _DestFn dest)
        {
            int64_t n = right_expr->_eval_one(scope, _IntV::of(1))->to_int();
            if(n == 0) { return; } // Cannot calc "len - 1" when len == 0, since it's unsigned.

            ptr<_Vals> pop{left_expr->_eval_mutable_list(scope)};
            size_t len = pop->size();
            size_t un = (size_t)n;

            if(n < 0 || un > len)
            {
                throw MTException() << "RandChoose: cannot choose " << n << " value(s) from a " << len << "-value sequence";
            }

            for(size_t i = 0; i < un && i < len - 1; i++)
            {
                size_t j = std::uniform_int_distribution<size_t>(i, len - 1)(_ExecState::instance.rand_gen);
                std::swap((*pop)[i], (*pop)[j]);
                dest((*pop)[i]);
            }
            if(un == len)
            {
                dest((*pop)[len - 1]);
            }
        });
    }

    INFIX_IMPL(_Expr, _Expr, With)
    {
        return _call_expr("With",
            [=](ptr<_Scope> outer_scope, _DestFn dest)
            {
                ptr<_Scope> inner_scope{std::make_shared<_Scope>(outer_scope)};
                inner_scope->put(_SpecialKeyV::IT, left_expr->_eval_list(outer_scope));
                right_expr->_eval(inner_scope, dest);
            },
            right_expr->_static_length
        );
    }

    INFIX_IMPL(_Expr, _Expr, ForEach)
    {
        return _call_expr("ForEach",
            [=](ptr<_Scope> outer_scope, _DestFn dest)
            {
                auto& state = _ExecState::instance;
                ptr<_Scope> inner_scope{std::make_shared<_Scope>(outer_scope, outer_scope->allowed_jumps | Break.bit | Continue.bit)};
                int64_t idx = 0;

                struct Restore {
                    ~Restore() { _ExecState::instance.end_jump(Break); }
                } _restore;

                ptr<_Vals> idx_var {std::make_shared<_Vals>(_Vals{nullptr})};
                ptr<_Vals> item_var{std::make_shared<_Vals>(_Vals{nullptr})};
                inner_scope->put(_SpecialKeyV::IDX, idx_var);
                inner_scope->put(_SpecialKeyV::ITEM, item_var);
                try
                {
                    left_expr->_eval(outer_scope, [&](ptr<_Val> v)
                    {
                        (*idx_var)[0] = _IntV::of(idx);
                        (*item_var)[0] = v;

                        right_expr->_eval(inner_scope, dest);
                        state.end_jump(Continue);
                        if(state.current_jump != 0)
                        {
                            throw StopIterationEx;
                        }
                        idx++;
                    });
                }
                catch(StopIteration& ex) {}
            },
            (_static_length == _UNKNOWN_LENGTH || right_expr->_static_length == _UNKNOWN_LENGTH)
                ? _UNKNOWN_LENGTH
                : (_static_length * right_expr->_static_length)
        );
    }

    POSTFIX_IMPL(_Expr, _Expr, Eval)
    {
        return _call_expr("Eval", [=](ptr<_Scope> outer_scope, _DestFn dest)
        {
            left_expr->_eval(outer_scope, [&](ptr<_Val> v)
            {
                _Parser("(" + v->to_str() + ")")
                    .parse_list(_Parser::SyntaxMode::NORMAL)
                    ->_eval(std::make_shared<_Scope>(outer_scope), dest);
            });
        });
    }

    POSTFIX_IMPL(_Expr, _Expr, Print)
    {
        return _void_expr("Print", [=](ptr<_Scope> scope)
        {
            left_expr->_eval(scope, [&](ptr<_Val> v) { std::cout << *v; });
        });
    }
    POSTFIX_IMPL(_Expr, _Expr, Done)
    {
        return _void_expr("Done", [=](ptr<_Scope> scope){ left_expr->_eval(scope, [](ptr<_Val> v){}); });
    }

    INFIX_IMPL(_Expr, _Expr, Note) {
        return left_expr;
    }


    // --- class _CallExpr ---

    _CallExpr::_CallExpr(const char* name, _EvalFn fn, int64_t static_length)
        : _Expr(name, static_length), fn{fn} {}

    void _CallExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        fn(scope, dest);
    }


    // --- class _ListExpr ---

    _ListExpr::_ListExpr(const char* name, _EvalListFn fn, _EvalListFn mutable_list_fn, int64_t static_length)
        : _Expr(name, static_length), fn{fn}, mutable_list_fn{mutable_list_fn} {}

    void _ListExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        ptr<_Vals> list{fn(scope)};
        for(ptr<_Val> v : *list)
        {
            dest(v);
        }
    }

    ptr<_Vals> _ListExpr::_eval_list(ptr<_Scope> scope) const
    {
        return fn(scope);
    }

    ptr<_Vals> _ListExpr::_eval_mutable_list(ptr<_Scope> scope) const
    {
        return mutable_list_fn(scope);
    }


    // --- class _SingleExpr ---

    _SingleExpr::_SingleExpr(const char* name, std::function<ptr<_Val>(ptr<_Scope>)> fn)
        : _Expr(name, 1), fn(fn) {}

    void _SingleExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        dest(fn(scope));
    }

    ptr<_Vals> _SingleExpr::_eval_list(ptr<_Scope> scope) const
    {
        return std::make_shared<_Vals>(_Vals{fn(scope)});
    }

    ptr<_Val> _SingleExpr::_eval_one(ptr<_Scope> scope, ptr<_Val> defV) const
    {
        return fn(scope);
    }


    // --- class _StaticExpr ---

    _StaticExpr::_StaticExpr(ptr<_Expr> inner_expr)
        : _Expr(inner_expr->_name, inner_expr->_static_length)
    {
        _static_self = inner_expr;
    }

    void _StaticExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        std::static_pointer_cast<_Expr>(_static_self)->_eval(scope, dest);
    }

    ptr<_Vals> _StaticExpr::_eval_list(ptr<_Scope> scope) const
    {
        return std::static_pointer_cast<_Expr>(_static_self)->_eval_list(scope);
    }

    ptr<_Val> _StaticExpr::_eval_one(ptr<_Scope> scope, ptr<_Val> def_v) const
    {
        return std::static_pointer_cast<_Expr>(_static_self)->_eval_one(scope, def_v);
    }


    // --- class _VExpr ---

    _VExpr::_VExpr(ptr<_Expr> key_expr)
        : _Expr("V"), key_expr{key_expr} {}

    void _VExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        key_expr->_eval(scope, [&](ptr<_Val> k)
        {
            for(ptr<_Val> v : *scope->get(k))
            {
                dest(v);
            }
        });
    }


    POSTFIX_IMPL(_Expr, _VExpr, Pop)
    {
        auto key_expr = left_expr->key_expr;
        return _call_expr("Pop", [=](ptr<_Scope> scope, _DestFn dest)
        {
            key_expr->_eval(scope, [&](ptr<_Val> k)
            {
                ptr<_Vals> var_vals = scope->get(k);
                if(var_vals->size() > 0)
                {
                    dest((*var_vals)[0]);
                    var_vals->erase(var_vals->begin());
                }
            });
        });
    }

    INFIX_IMPL(_Expr, _VExpr, Set)
    {
        return _assign("Set", left_expr->key_expr, right_expr,
                       [](ptr<_Scope> scope, ptr<_Val> k, ptr<_Vals> v) { scope->put(k, v); });
    }

    INFIX_IMPL(_Expr, _VExpr, SetGlobal)
    {
        return _assign("SetGlobal", left_expr->key_expr, right_expr,
                       [](ptr<_Scope> _scope, ptr<_Val> k, ptr<_Vals> v) {
                           UNUSED(_scope); // Doesn't use local scope.
                           _ExecState::instance.global_scope->put(k, v);
                       });
    }

    INFIX_IMPL(_Expr, _VExpr, Update)
    {
        return _assign("Update", left_expr->key_expr, right_expr,
                       [](ptr<_Scope> scope, ptr<_Val> k, ptr<_Vals> v)
                       {
                           ptr<_Vals> stored_vals = scope->get(k);
                           stored_vals->clear();
                           stored_vals->extend(v);
                       });
    }

    INFIX_IMPL(_Expr, _VExpr, Append)
    {
        return _assign("Append", left_expr->key_expr, right_expr,
                       [](ptr<_Scope> scope, ptr<_Val> k, ptr<_Vals> v) { scope->get(k)->extend(v); });
    }


    // --- class _IfExpr

    _IfExpr::_IfExpr(ptr<_Expr> condition) : _ExprBase("If"), condition{condition} {}

    INFIX_IMPL(_ThenExpr, _IfExpr, Then)
    {
        return std::make_shared<_ThenExpr>(condition, right_expr);
    }


    // --- class _ThenExpr ---

    _ThenExpr::_ThenExpr(ptr<_Expr> condition, ptr<_Expr> then_body)
        : _Expr("If-Then"), condition{condition}, then_body{then_body}, else_body{_Expr::_EMPTY} {}

    const ptr<_Expr>& _ThenExpr::body(ptr<_Scope> scope) const
    {
        return condition->_eval_one(scope)->to_bool() ? then_body : else_body;
    }

    void _ThenExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        body(scope)->_eval(scope, dest);
    }

    ptr<_Vals> _ThenExpr::_eval_list(ptr<_Scope> scope) const
    {
        return body(scope)->_eval_list(scope);
    }

    ptr<_Val> _ThenExpr::_eval_one(ptr<_Scope> scope, ptr<_Val> def_v) const
    {
        return body(scope)->_eval_one(scope, def_v);
    }

    INFIX_IMPL(_Expr, _ThenExpr, Else)
    {
        else_body = right_expr;
        if(then_body->_static_length != _UNKNOWN_LENGTH && then_body->_static_length == else_body->_static_length)
        {
            _static_length = then_body->_static_length;
        }
        return left_expr;
    }


    // --- class _WhileExpr ---

    _WhileExpr::_WhileExpr(ptr<_Expr> condition) : _ExprBase("While"), condition{condition} {}

    INFIX_IMPL(_Expr, _WhileExpr, Do)
    {
        ptr<_Expr> condition = left_expr->condition;
        return _call_expr("While-Do", [=](ptr<_Scope> outer_scope, _DestFn dest)
        {
            auto& state = _ExecState::instance;
            ptr<_Scope> inner_scope{std::make_shared<_Scope>(outer_scope, outer_scope->allowed_jumps | Break.bit | Continue.bit)};
            int64_t idx = 0;
            ptr<_Vals> idx_var{std::make_shared<_Vals>(_Vals{_IntV::of(idx)})};
            inner_scope->put(_SpecialKeyV::IDX, idx_var);

            struct Restore{
                ~Restore() { _ExecState::instance.end_jump(Break); }
            } _restore;

            while(condition->_eval_one(inner_scope, nullptr)->to_bool())
            {
                right_expr->_eval(inner_scope, dest);
                state.end_jump(Continue);
                if(state.current_jump != 0)
                {
                    break;
                }
                idx++;
                (*idx_var)[0] = _IntV::of(idx);
            }
        });
    }


    // --- class _TryExpr ---

    _TryExpr::_TryExpr(ptr<_Expr> try_body)
        : _Expr("Try"), try_body{try_body}, finally_body{_Expr::_EMPTY} {}


    void _TryExpr::_add_catch(ptr<_Expr> catch_pattern, ptr<_Expr> catch_body)
    {
        catches.push_back(std::pair(catch_pattern, catch_body));
    }

    void _TryExpr::_eval(ptr<_Scope> scope, _DestFn dest) const
    {
        ptr<_Vals> list{_eval_list(scope)};
        for(ptr<_Val> v : *list)
        {
            dest(v);
        }
    }

    ptr<_Vals> _TryExpr::_eval_list(ptr<_Scope> scope) const
    {
        auto& state = _ExecState::instance;
        ptr<_Vals> values{try_body->_eval_mutable_list(scope)};

        bool rethrow = false;
        if(state.end_jump(_Jump::Throw))
        {
            scope->put(_SpecialKeyV::EX, state.exception);
            scope->put(_SpecialKeyV::PARTIAL, values);
            bool match = false;
            for(auto& catch_pair : catches)
            {
                ptr<_Vals> pattern{catch_pair.first->_eval_list(scope)};
                size_t pSize = pattern->size();
                ptr<_Expr> cBody = catch_pair.second;

                if(state.exception->size() >= pSize)
                {
                    match = true;
                    for(size_t i = 0; i < pSize; i++)
                    {
                        if(*(*state.exception)[i] != *(*pattern)[i])
                        {
                            match = false;
                            break;
                        }
                    }
                    if(match)
                    {
                        state.exception = nullptr;
                        values = cBody->_eval_mutable_list(scope);
                        break;
                    }
                }
            }
            if(!match)
            {
                values->clear();
                rethrow = true;
            }
        }
        finally_body->_eval(scope, [&](ptr<_Val> v){ values->push_back(v); });

        if(rethrow)
        {
            state.current_jump = _Jump::Throw.bit;
        }
        return values;
    }

    INFIX_IMPL(_CatchExpr, _TryExpr, Catch)
    {
        return std::make_shared<_CatchExpr>(left_expr, right_expr);
    }

    INFIX_IMPL(_Expr, _TryExpr, Finally)
    {
        finally_body = right_expr;
        return left_expr;
    }


    // --- class _CatchExpr ---

    _CatchExpr::_CatchExpr(ptr<_TryExpr> try_expr, ptr<_Expr> catch_pattern)
        : _ExprBase("Catch"), try_expr{try_expr}, catch_pattern{catch_pattern} {}

    INFIX_IMPL(_TryExpr, _CatchExpr, Do)
    {
        try_expr->_add_catch(catch_pattern, right_expr);
        return try_expr;
    }


    // --- keyword values ---

    KW_VALUE_IMPL(TRUE,  _single_expr("TRUE",  [](ptr<_Scope> _s){ return _BoolV::TRUE_VALUE; }));
    KW_VALUE_IMPL(FALSE, _single_expr("FALSE", [](ptr<_Scope> _s){ return _BoolV::FALSE_VALUE; }));

    KW_VALUE_IMPL(Val,     _stored_list_expr("Val",     [](ptr<_Scope> s){ return s->get(_SpecialKeyV::VAL); }));
    KW_VALUE_IMPL(Arg,     _stored_list_expr("Arg",     [](ptr<_Scope> s){ return s->get(_SpecialKeyV::ARG); }));
    KW_VALUE_IMPL(ThisFn,  _stored_list_expr("ThisFn",  [](ptr<_Scope> s){ return s->get(_SpecialKeyV::THIS_FN); }, 1));
    KW_VALUE_IMPL(Idx,     _stored_list_expr("Idx",     [](ptr<_Scope> s){ return s->get(_SpecialKeyV::IDX); }, 1));
    KW_VALUE_IMPL(Item,    _stored_list_expr("Item",    [](ptr<_Scope> s){ return s->get(_SpecialKeyV::ITEM); }, 1));
    KW_VALUE_IMPL(It,      _stored_list_expr("It",      [](ptr<_Scope> s){ return s->get(_SpecialKeyV::IT); }));
    KW_VALUE_IMPL(Ex,      _stored_list_expr("Ex",      [](ptr<_Scope> s){ return s->get(_SpecialKeyV::EX); }));
    KW_VALUE_IMPL(Partial, _stored_list_expr("Partial", [](ptr<_Scope> s){ return s->get(_SpecialKeyV::PARTIAL); }));

    KW_VALUE_IMPL(Time, _single_expr("Time", [](ptr<_Scope> _s)
    {
        return _FloatV::of(
            std::chrono::duration_cast<std::chrono::seconds>(
                std::chrono::system_clock::now().time_since_epoch()
            ).count());
    }));

    KW_VALUE_IMPL(Rand, _single_expr("Rand", [](ptr<_Scope> _s)
    {
        return _FloatV::of(std::uniform_real_distribution<double>()(_ExecState::instance.rand_gen));
    }));

    KW_VALUE_IMPL(Pi,   _single_expr("Pi",   [](ptr<_Scope> _s){ return _FloatV::of(3.141592653589793); }));
    KW_VALUE_IMPL(Inf,  _single_expr("Inf",  [](ptr<_Scope> _s){ return _FloatV::of(std::numeric_limits<double>::infinity()); }));
    KW_VALUE_IMPL(MNaN, _single_expr("MNaN", [](ptr<_Scope> _s){ return _FloatV::of(std::numeric_limits<double>::quiet_NaN()); }));
}
