#ifndef MT_SCOPE_H
#define MT_SCOPE_H

#include "mt_common.h"
#include "mt_vals.h"
#include <cstdint>
#include <ostream>
#include <unordered_map>

namespace mistletoe
{
    struct _Scope
    {
        ptr<_Scope> parent;
        std::unordered_map<ptr<_Val>,ptr<_Vals>,_Val::Hash,_Val::Eq> vars;
        uint8_t allowed_jumps;

        _Scope(ptr<_Scope> parent, uint8_t allowed_jumps);
        _Scope(ptr<_Scope> parent);
        _Scope(const _Scope& other);

        ptr<_Vals> get(ptr<_Val> k);
        ptr<_Vals> get_optional(ptr<_Val> k);
        void put(ptr<_Val> k, ptr<_Vals> v);
        friend std::ostream& operator<<(std::ostream& out, const _Scope& s);
    };

    std::ostream& operator<<(std::ostream& out, const _Scope& v);
}

#endif
