#include "mt_function_val.h"

namespace mistletoe
{
    uint64_t _FunctionV::next_id = 0;
    ptr<_Val> _FunctionV::of(ptr<_Expr> body, ptr<_Scope> parent_scope)
    {
        return std::make_shared<_FunctionV>(body, parent_scope);
    }

    _FunctionV::_FunctionV(ptr<_Expr> body, ptr<_Scope> parent_scope)
        : id(next_id++), body(body), parent_scope(parent_scope) {}

    _FunctionV::_FunctionV(const _FunctionV& other)
        : id(other.id), body(other.body), parent_scope(other.parent_scope) {}

    _Val::Type _FunctionV::val_type() const
    {
        return FunctionT;
    }

    const char* _FunctionV::type_name() const
    {
        return "Fn";
    }

    bool _FunctionV::operator==(const _Val& other) const
    {
        return other.val_type() == FunctionT && static_cast<const _FunctionV&>(other).id == id;
    }

    size_t _FunctionV::get_hash() const
    {
        return id;
    }

    void _FunctionV::to_str(std::ostream& out) const
    {
        // out << "<Function " << id << ">";
        out << "Fn(...)";
    }
}
