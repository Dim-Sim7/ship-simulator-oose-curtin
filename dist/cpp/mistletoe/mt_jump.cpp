#include "mt_jump.h"

namespace mistletoe
{
    std::unordered_map<std::string,_Jump*> _Jump::instances{};
    _Jump Break{"Break", 0x01};
    _Jump Continue{"Continue", 0x02};
    _Jump Return{"Return", 0x04};
    _Jump _Jump::Throw{"Throw()", 0x08};

    _Jump::_Jump(const char* name, int bit) : name(name), bit(bit)
    {
        instances.emplace(name, this);
    }

    const char* _Jump::what() const throw()
    {
        return name;
    }
}
