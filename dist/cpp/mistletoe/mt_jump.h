#ifndef MT_JUMP_H
#define MT_JUMP_H

#include <cstdint>
#include <exception>
#include <string>
#include <unordered_map>

namespace mistletoe
{
    struct _Jump : std::exception
    {
        static std::unordered_map<std::string,_Jump*> instances;
        static _Jump Throw;
        const char* name;
        uint8_t bit;

        _Jump(const char* name, int bit);
        const char* what() const throw() override;
    };

    extern _Jump Break;
    extern _Jump Continue;
    extern _Jump Return;
}

#endif
