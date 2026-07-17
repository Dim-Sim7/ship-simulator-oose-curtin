#ifndef MT_TUPLE_VAL_H
#define MT_TUPLE_VAL_H

#include "mt_val.h"
#include "mt_vals.h"
#include <ostream>

namespace mistletoe
{
    struct _TupleV : public _SimpleVal<_Vals, _Val::TupleT>
    {
        static ptr<_Val> EMPTY;
        static ptr<_Val> of(const ptr<_Vals>& v);
        _TupleV(const _Vals& v);
        _TupleV(const ptr<_Vals>& v);
        const char* type_name() const override;
        size_t get_hash() const override;
        void to_str(std::ostream& out) const override;
    };
}

#endif
