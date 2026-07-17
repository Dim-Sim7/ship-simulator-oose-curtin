#include "mt_vals.h"

namespace mistletoe
{
    _Vals::_Vals() {}
    _Vals::_Vals(std::initializer_list<ptr<_Val>> init) : std::vector<ptr<_Val>>(init) {}

    void _Vals::extend(const ptr<_Vals>& other)
    {
        insert(end(), other->begin(), other->end());
    }
    bool _Vals::operator==(const _Vals& other) const
    {
        size_t len = size();
        if(len != other.size()) { return false; }
        for(size_t i = 0; i < len; i++)
        {
            if(*(*this)[i] != *other[i]) { return false; }
        }
        return true;
    }
    bool _Vals::operator!=(const _Vals& other) const
    {
        return !(*this == other);
    }
    void _Vals::to_str(std::ostream& out) const
    {
        size_t len = size();
        if(len == 0) { return; }
        (*this)[0]->repr(out);
        for(size_t i = 1; i < len; i++)
        {
            out << ", ";
            (*this)[i]->repr(out);
        }
    }

    std::ostream& operator<<(std::ostream& out, const _Vals& vals)
    {
        out.put('[');
        vals.to_str(out);
        out.put(']');
        return out;
    }
}
