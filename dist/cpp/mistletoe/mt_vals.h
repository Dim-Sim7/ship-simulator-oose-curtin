#ifndef MT_VALS_H
#define MT_VALS_H

#include "mt_val.h"

namespace mistletoe
{
    struct _Vals : public std::vector<ptr<_Val>>
    {
        _Vals();
        _Vals(std::initializer_list<ptr<_Val>> init);
        void extend(const ptr<_Vals>& other);
        bool operator==(const _Vals& other) const;
        bool operator!=(const _Vals& other) const;
        void to_str(std::ostream& out) const;
    };
    std::ostream& operator<<(std::ostream& out, const _Vals& vals);
}

#endif
