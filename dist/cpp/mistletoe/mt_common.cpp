#include "mt_common.h"
#include <cstdint>

namespace mistletoe
{
    MTException::MTException(std::string suffix)
        : msg{}, suffix{suffix}, sb{} {}

    MTException::MTException(const MTException& other)
        : msg{other.msg + other.sb.str()}, suffix{other.suffix}, sb{} {}

    const char* MTException::what() throw()
    {
        msg.append(sb.str());
        msg.append(suffix);
        sb.str("");
        suffix.clear();
        return msg.c_str();
    }

    std::string _chr(int64_t code_point)
    {
        if(code_point < 0 || code_point > 0x10ffff || (0xd800 <= code_point && code_point <= 0xdfff))
        {
            throw MTException() << "Chr: "
                << ((code_point < 0) ? "-" : "") << "0x" << std::hex << std::abs(code_point)
                << " (" << std::dec << code_point << ") is not a valid code point";
        }

        // Implement UTF-8 encoding
        if(code_point < 0x0080)
        {
            return std::string{(char)code_point};
        }
        else if(code_point < 0x0800)
        {
            return std::string{
                (char)(( code_point >> 6)          | 0xc0),
                (char)(( code_point        & 0x3f) | 0x80)
            };
        }
        else if(code_point < 0x10000)
        {
            return std::string{
                (char)(( code_point >> 12)         | 0xe0),
                (char)(((code_point >>  6) & 0x3f) | 0x80),
                (char)(( code_point        & 0x3f) | 0x80)
            };
        }
        else
        {
            return std::string{
                (char)(( code_point >> 18)         | 0xf0),
                (char)(((code_point >> 12) & 0x3f) | 0x80),
                (char)(((code_point >>  6) & 0x3f) | 0x80),
                (char)(( code_point        & 0x3f) | 0x80)
            };
        }
    }

    void _find_code_points(std::string str, std::function<void(int64_t)> consumer)
    {
        const char* ch = str.data();
        const char* end_ch = ch + str.length();

        // Decode UTF-8.
        while(ch != end_ch)
        {
            uint8_t ones = 0;
            while(ones < 5 && (*ch & (0x80 >> ones)) != 0)
            {
                ones++;
            }
            // The maximum valid number of leading ones is 4 (i.e. 0b11110***). Hence, we
            // iterate to 5 to detect errors.

            const int64_t INVALID = 0xfffd;
            int64_t code_point;
            switch(ones)
            {
                case 0:         code_point = *ch;     ch++; break;
                case 1: case 5: code_point = INVALID; ch++; break;
                default:
                    code_point = *ch & (0xff >> ones);
                    ch++;
                    for(int j = 1; j < ones; j++, ch++)
                    {
                        if((*ch & 0xc0) != 0x80)
                        {
                            code_point = INVALID;
                            break;
                        }
                        code_point = (code_point << 6) | (*ch & 0x3f);
                    }

                    // Disallow overlong encodings
                    switch(ones)
                    {
                        case 2: if(code_point < 0x0080) { code_point = INVALID; } break;
                        case 3: if(code_point < 0x0800) { code_point = INVALID; } break;
                        case 4: if(code_point < 0x10000) { code_point = INVALID; } break;
                    }
            }
            consumer(code_point);
        }
    }
}
