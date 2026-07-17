#ifndef MT_EXPR_H
#define MT_EXPR_H

#include "mt_jump.h"
#include "mt_scope.h"
#include "mt_vals.h"
#include <functional>
#include <unordered_map>

#define INFIX_DECL(return_type, owner_type, name) \
    ptr< return_type > name ## _impl (ptr< owner_type > left_expr, ptr<_Expr> right_expr); \
    return_type& name ## _ref (ptr<_Expr> right_expr); \
    template<class... Ts> return_type& name (const Ts&... args) { \
        return name ## _ref (_to_expr(args...)); \
    }

#define POSTFIX_DECL(return_type, owner_type, name) \
    ptr< return_type > name ## _impl (ptr< owner_type > left_expr); \
    return_type& name ## _ref (); \
    return_type& name () { \
        return name ## _ref (); \
    }

#define PREFIX_DECL(return_type, name) \
    ptr< return_type > name ## _impl (ptr<_Expr> right_expr); \
    return_type& name ## _ref (ptr<_Expr> right_expr); \
    template<class... Ts> return_type& name (const Ts&... args) { \
        return name ## _ref (_to_expr(args...)); \
    }

#define KW_VALUE_DECL(name) extern _StaticExpr name


namespace mistletoe
{
    using _DestFn     = std::function<void(ptr<_Val>)>;
    using _EvalFn     = std::function<void(ptr<_Scope>,_DestFn)>;
    using _EvalListFn = std::function<ptr<_Vals>(ptr<_Scope>)>;
    using _EvalOneFn  = std::function<ptr<_Val>(ptr<_Scope>,ptr<_Val>)>;

    struct _Expr;

    ptr<_Expr> _arg_to_expr(_Jump j);
    ptr<_Expr> _arg_to_expr(const _Expr& e);
    ptr<_Expr> _arg_to_expr(ptr<_Expr> e);
    ptr<_Expr> _arg_to_expr(double v);
    ptr<_Expr> _arg_to_expr(int v);
    ptr<_Expr> _arg_to_expr(uint64_t v);
    ptr<_Expr> _arg_to_expr(const char* v);
    ptr<_Expr> _expr_cat(ptr<std::vector<ptr<_Expr>>> expr_list);
    template<class... Ts> ptr<_Expr> _to_expr(const Ts&... args);

    struct _ExprBase
    {
        static thread_local std::unordered_map<size_t,ptr<_ExprBase>> tmp_alloc;
        static size_t next_id;
        size_t _id;
        const char* _name;
        ptr<_ExprBase> _static_self;
        _ExprBase(const char* name);
    };

    struct _Expr : public _ExprBase
    {
        static ptr<_Expr> _EMPTY;
        static const int64_t _UNKNOWN_LENGTH = -1;
        int64_t _static_length;

        _Expr(const char* name, int64_t static_length = _UNKNOWN_LENGTH);

        virtual void _eval(ptr<_Scope> scope, _DestFn dest) const = 0;
        virtual ptr<_Vals> _eval_list(ptr<_Scope> scope) const;
        virtual ptr<_Vals> _eval_mutable_list(ptr<_Scope> scope) const;
        virtual ptr<_Val> _eval_one(ptr<_Scope> scope, ptr<_Val> def_v = nullptr) const;

        ptr<_Vals> staticLength() const;
        ptr<_Expr> _infix(const char* name, ptr<_Expr> left, ptr<_Expr> right, std::function<ptr<_Val>(const char*,ptr<_Val>,ptr<_Val>)> op);
        ptr<_Expr> _infix(const char* name, ptr<_Expr> left, ptr<_Expr> right, std::function<ptr<_Val>(ptr<_Val>,ptr<_Val>)> op);
        ptr<_Expr> _map(const char* name, ptr<_Expr> left, std::function<ptr<_Val>(ptr<_Val>)> op);

        INFIX_DECL(_Expr, _Expr, As);

        INFIX_DECL(_Expr, _Expr, Call);
        INFIX_DECL(_Expr, _Expr, Apply);

        INFIX_DECL(_Expr, _Expr, Eq);
        INFIX_DECL(_Expr, _Expr, Ne);
        INFIX_DECL(_Expr, _Expr, Lt);
        INFIX_DECL(_Expr, _Expr, Le);
        INFIX_DECL(_Expr, _Expr, Gt);
        INFIX_DECL(_Expr, _Expr, Ge);
        INFIX_DECL(_Expr, _Expr, In);
        INFIX_DECL(_Expr, _Expr, NotIn);
        POSTFIX_DECL(_Expr, _Expr, IsNaN);

        INFIX_DECL(_Expr, _Expr, And);
        INFIX_DECL(_Expr, _Expr, Or);
        POSTFIX_DECL(_Expr, _Expr, Not);

        INFIX_DECL(_Expr, _Expr, Plus);
        INFIX_DECL(_Expr, _Expr, Minus);
        INFIX_DECL(_Expr, _Expr, Times);
        INFIX_DECL(_Expr, _Expr, Div);
        INFIX_DECL(_Expr, _Expr, Mod);
        INFIX_DECL(_Expr, _Expr, Pow);
        POSTFIX_DECL(_Expr, _Expr, Neg);
        POSTFIX_DECL(_Expr, _Expr, Abs);

        POSTFIX_DECL(_Expr, _Expr, Int);
        POSTFIX_DECL(_Expr, _Expr, Float);
        POSTFIX_DECL(_Expr, _Expr, Str);

        POSTFIX_DECL(_Expr, _Expr, Tuple);
        POSTFIX_DECL(_Expr, _Expr, Flatten);

        INFIX_DECL(_Expr, _Expr, At);

        POSTFIX_DECL(_Expr, _Expr, Len);
        POSTFIX_DECL(_Expr, _Expr, Sum);
        POSTFIX_DECL(_Expr, _Expr, All);
        POSTFIX_DECL(_Expr, _Expr, Any);

        POSTFIX_DECL(_Expr, _Expr, SLen);
        INFIX_DECL(_Expr, _Expr, Cat);
        INFIX_DECL(_Expr, _Expr, Join);
        POSTFIX_DECL(_Expr, _Expr, Ord);
        POSTFIX_DECL(_Expr, _Expr, Chr);

        POSTFIX_DECL(_Expr, _Expr, RandShuffle);
        INFIX_DECL(_Expr, _Expr, RandChoose);
        INFIX_DECL(_Expr, _Expr, To);

        INFIX_DECL(_Expr, _Expr, With);
        INFIX_DECL(_Expr, _Expr, ForEach);

        POSTFIX_DECL(_Expr, _Expr, Eval);

        POSTFIX_DECL(_Expr, _Expr, Print);
        POSTFIX_DECL(_Expr, _Expr, Done);
        INFIX_DECL(_Expr, _Expr, Note);
    };

    template<class... Ts>
    ptr<_Expr> _to_expr(const Ts&... args)
    {
        return _expr_cat(std::make_shared<std::vector<ptr<_Expr>>>(std::vector<ptr<_Expr>>{ _arg_to_expr(args)... }));
    }

    struct _CallExpr : public _Expr
    {
        _EvalFn fn;
        _CallExpr(const char* name, _EvalFn fn, int64_t static_length = _Expr::_UNKNOWN_LENGTH);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
    };

    struct _ListExpr : public _Expr
    {
        _EvalListFn fn;
        _EvalListFn mutable_list_fn;
        _ListExpr(const char* name, _EvalListFn fn, _EvalListFn mutable_list_fn, int64_t static_length = _Expr::_UNKNOWN_LENGTH);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
        ptr<_Vals> _eval_list(ptr<_Scope> scope) const override;
        ptr<_Vals> _eval_mutable_list(ptr<_Scope> scope) const override;
    };

    struct _SingleExpr : public _Expr
    {
        std::function<ptr<_Val>(ptr<_Scope>)> fn;
        _SingleExpr(const char* name, std::function<ptr<_Val>(ptr<_Scope>)> fn);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
        ptr<_Vals> _eval_list(ptr<_Scope> scope) const override;
        ptr<_Val> _eval_one(ptr<_Scope> scope, ptr<_Val> defV = nullptr) const override;
    };

    struct _StaticExpr : public _Expr
    {
    public:
        _StaticExpr(ptr<_Expr> inner_expr);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
        ptr<_Vals> _eval_list(ptr<_Scope> scope) const override;
        ptr<_Val> _eval_one(ptr<_Scope> scope, ptr<_Val> def_v = nullptr) const override;
    };

    ptr<_Expr> _call_expr(const char* name, _EvalFn fn, int64_t static_length = _Expr::_UNKNOWN_LENGTH);
    ptr<_Expr> _list_expr(const char* name, _EvalListFn fn, int64_t static_length = _Expr::_UNKNOWN_LENGTH);
    ptr<_Expr> _stored_list_expr(const char* name, _EvalListFn fn, int64_t static_length = _Expr::_UNKNOWN_LENGTH);
    ptr<_Expr> _void_expr(const char* name, std::function<void(ptr<_Scope>)> fn);
    ptr<_Expr> _single_expr(const char* name, std::function<ptr<_Val>(ptr<_Scope>)> fn);

    class _VExpr : public _Expr
    {
    private:
        ptr<_Expr> key_expr;
    public:
        _VExpr(ptr<_Expr> _key_expr);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
        POSTFIX_DECL(_Expr, _VExpr, Pop);
        INFIX_DECL(_Expr, _VExpr, Set);
        INFIX_DECL(_Expr, _VExpr, SetGlobal);
        INFIX_DECL(_Expr, _VExpr, Update);
        INFIX_DECL(_Expr, _VExpr, Append);
    };

    class _ThenExpr : public _Expr
    {
    private:
        ptr<_Expr> condition;
        ptr<_Expr> then_body;
        ptr<_Expr> else_body;
        const ptr<_Expr>& body(ptr<_Scope> scope) const;
    public:
        _ThenExpr(ptr<_Expr> condition, ptr<_Expr> then_body);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
        ptr<_Vals> _eval_list(ptr<_Scope> scope) const override;
        ptr<_Val> _eval_one(ptr<_Scope> scope, ptr<_Val> def_v) const override;
        INFIX_DECL(_Expr, _ThenExpr, Else);
    };

    class _IfExpr : public _ExprBase // Does not derive from _Expr
    {
    private:
        ptr<_Expr> condition;
    public:
        _IfExpr(ptr<_Expr> condition);
        INFIX_DECL(_ThenExpr, _IfExpr, Then);
    };

    class _WhileExpr : public _ExprBase // Does not derive from _Expr
    {
    private:
        ptr<_Expr> condition;
    public:
        _WhileExpr(ptr<_Expr> condition);
        INFIX_DECL(_Expr, _WhileExpr, Do);
    };


    class _CatchExpr;
    class _TryExpr : public _Expr
    {
    private:
        ptr<_Expr> try_body;
        ptr<_Expr> finally_body;
        std::vector<std::pair<ptr<_Expr>,ptr<_Expr>>> catches;

    public:
        _TryExpr(ptr<_Expr> try_body);
        void _add_catch(ptr<_Expr> catch_pattern, ptr<_Expr> catch_body);
        void _eval(ptr<_Scope> scope, _DestFn dest) const override;
        ptr<_Vals> _eval_list(ptr<_Scope> scope) const override;

        INFIX_DECL(_CatchExpr, _TryExpr, Catch);
        INFIX_DECL(_Expr, _TryExpr, Finally);
    };


    class _CatchExpr : public _ExprBase  // Does not derive from _Expr
    {
    private:
        ptr<_TryExpr> try_expr;
        ptr<_Expr> catch_pattern;

    public:
        _CatchExpr(ptr<_TryExpr> try_expr, ptr<_Expr> catch_pattern);
        INFIX_DECL(_TryExpr, _CatchExpr, Do);
    };


    class _Operator
    {
    public:
        enum ExprType { Nil, _ExprT, _CallExprT, _ListExprT, _SingleExprT,
                        _VExprT, _IfExprT, _ThenExprT, _WhileExprT, _TryExprT, _CatchExprT };

        static _Operator* lookup(ExprType owner_type, std::string name);
        static bool type_is_full_expression(ExprType type);

        _Operator(std::string name, ExprType owner_type, ExprType return_type, bool right_operand,
                  std::function<ptr<_ExprBase>(ptr<_ExprBase>,ptr<_Expr>)> fn);
        ExprType get_return_type() const;
        bool has_right_operand() const;
        ptr<_ExprBase> call(ptr<_ExprBase> left, ptr<_Expr> right) const;

    private:
        struct Hash
        {
            size_t operator()(const std::pair<ExprType,std::string>& p) const;
        };
        struct Eq
        {
            bool operator()(const std::pair<ExprType,std::string>& p1,
                            const std::pair<ExprType,std::string>& p2) const;
        };
        static std::unordered_map<ExprType,ExprType> super_types;
        static std::unordered_map<std::pair<ExprType,std::string>,_Operator*,Hash,Eq> operators;
        ExprType owner_type;
        ExprType return_type;
        bool left_operand;
        bool right_operand;
        std::function<ptr<_ExprBase>(ptr<_ExprBase>,ptr<_Expr>)> fn;
    };


    // --- prefix operators ---

    PREFIX_DECL(_Expr, Scope);
    PREFIX_DECL(_Expr, C);
    PREFIX_DECL(_Expr, I);
    PREFIX_DECL(_Expr, F);
    PREFIX_DECL(_Expr, S);
    PREFIX_DECL(_Expr, T);
    PREFIX_DECL(_Expr, Fn);
    PREFIX_DECL(_VExpr, V);
    PREFIX_DECL(_IfExpr, If);
    PREFIX_DECL(_WhileExpr, While);
    PREFIX_DECL(_TryExpr, Try);
    PREFIX_DECL(_Expr, Throw);
    PREFIX_DECL(_Expr, Note);


    // --- keyword values ---

    KW_VALUE_DECL(TRUE);
    KW_VALUE_DECL(FALSE);

    KW_VALUE_DECL(Val);
    KW_VALUE_DECL(Arg);
    KW_VALUE_DECL(ThisFn);
    KW_VALUE_DECL(Idx);
    KW_VALUE_DECL(Item);
    KW_VALUE_DECL(It);
    KW_VALUE_DECL(Ex);
    KW_VALUE_DECL(Partial);

    KW_VALUE_DECL(Time);
    KW_VALUE_DECL(Rand);

    KW_VALUE_DECL(Pi);
    KW_VALUE_DECL(Inf);
    KW_VALUE_DECL(MNaN);
}

#endif
