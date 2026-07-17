#ifndef MT_FUNCTION_VAL_H
#define MT_FUNCTION_VAL_H

#include "mt_expr.h"
#include "mt_scope.h"
#include "mt_val.h"
#include <ostream>

namespace mistletoe
{
    struct _FunctionV : public _Val
    {
        static ptr<_Val> of(ptr<_Expr> body, ptr<_Scope> parent_scope);

        static uint64_t next_id;
        const uint64_t id;
        const ptr<_Expr> body;
        const ptr<_Scope> parent_scope;

        _FunctionV(ptr<_Expr> body, ptr<_Scope> parent_scope);
        _FunctionV(const _FunctionV& other);
        _Val::Type val_type() const override;
        const char* type_name() const override;
        bool operator==(const _Val& other) const override;
        size_t get_hash() const override;
        void to_str(std::ostream& out) const override;
    };
}

#endif
