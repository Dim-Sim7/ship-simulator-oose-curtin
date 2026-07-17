#ifndef MT_COMMON_H
#define MT_COMMON_H

#include <exception>
#include <functional>
#include <memory>
#include <sstream>
#include <string>


#define UNUSED(ex) (void)(ex);

namespace mistletoe
{
    template<class T>
    using ptr = std::shared_ptr<T>;

    class MTException : public std::exception
    {
    private:
        std::string msg;
        std::string suffix;
        std::stringstream sb;

    public:
        MTException(std::string suffix = "");
        MTException(const MTException& other);

        template<class T>
        MTException& operator<<(const T& v)
        {
            sb << v;
            return *this;
        }

        const char* what() throw();
    };

    std::string _chr(int64_t code_point); // Encode UTF-8
    void _find_code_points(std::string str, std::function<void(int64_t)> consumer); // Decode UTF-8
}

#endif
