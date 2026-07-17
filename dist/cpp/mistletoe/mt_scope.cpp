#include "mt_scope.h"

namespace mistletoe
{
    _Scope::_Scope(ptr<_Scope> parent, uint8_t allowed_jumps)
        : parent(parent), vars(), allowed_jumps(allowed_jumps) {}

    _Scope::_Scope(ptr<_Scope> parent)
        : parent(parent), vars(), allowed_jumps(parent->allowed_jumps) {}

    _Scope::_Scope(const _Scope& other)
        : parent(other.parent), vars(other.vars), allowed_jumps(other.allowed_jumps) {}

    ptr<_Vals> _Scope::get(ptr<_Val> k)
    {
        ptr<_Vals> v = get_optional(k);
        if(!v)
        {
            throw MTException() << u8"Variable \u201c" << k->to_str() << u8"\u201d ("
                                << k->type_name() << u8"-named) is not defined";
        }
        return v;
    }

    ptr<_Vals> _Scope::get_optional(ptr<_Val> k)
    {
        auto it = vars.find(k);
        if(it != vars.end())
        {
            return it->second;
        }
        if(parent)
        {
            return parent->get_optional(k);
        }
        return nullptr;
    }

    void _Scope::put(ptr<_Val> k, ptr<_Vals> v)
    {
        vars[k] = v;
    }

    std::ostream& operator<<(std::ostream& out, const _Scope& v)
    {
        bool first = true;
        out.put('{');
        for(auto& kv : v.vars)
        {
            if(first) { first = false; }
            else      { out << ", "; }
            kv.first->repr(out);
            out << ": " << *kv.second;
        }
        out.put('}');
        if(v.parent)
        {
            out << " -> " << *v.parent;
        }
        return out;
    }
}
