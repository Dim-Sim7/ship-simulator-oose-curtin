#include "mt_tuple_val.h"

namespace mistletoe
{
    ptr<_Val> _TupleV::EMPTY = std::make_shared<_TupleV>(_Vals{});

    ptr<_Val> _TupleV::of(const ptr<_Vals>& v)
    {
        return (v->size() == 0) ? EMPTY : std::make_shared<_TupleV>(*v);
    }
    _TupleV::_TupleV(const _Vals& v) : _SimpleVal<_Vals,_Val::TupleT>(v) {}
    _TupleV::_TupleV(const ptr<_Vals>& v) : _SimpleVal<_Vals,_Val::TupleT>(*v) {}
    const char* _TupleV::type_name() const
    {
        return "Tuple";
    }
    size_t _TupleV::get_hash() const
    {
        size_t hash = 31;
        for(const ptr<_Val>& v : val)
        {
            hash += 37 * v->get_hash();
        }
        return hash;
    }
    void _TupleV::to_str(std::ostream& out) const
    {
        out << "T(";
        val.to_str(out);
        out.put(')');
    }
}
