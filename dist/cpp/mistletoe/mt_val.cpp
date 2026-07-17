#include "mt_val.h"
#include "mt_common.h"

#include <cmath>
#include <iomanip>
#include <limits>
#include <sstream>
#include <string>


namespace mistletoe
{
    // --- class _Val ---

    size_t _Val::Hash::operator()(const ptr<_Val> v) const
    {
        return v->get_hash();
    }
    bool _Val::Eq::operator()(const ptr<_Val> v1, const ptr<_Val> v2) const
    {
        return v1->container_eq(*v2);
    }

    _Val::~_Val() {}
    bool _Val::container_eq(const _Val& other) const
    {
        return operator==(other);
    }
    bool _Val::operator!=(const _Val& other) const
    {
        return !(*this == other);
    }
    int64_t _Val::to_int() const
    {
        throw MTException() << u8"Cannot convert " << type_name() << u8" \u201c" << repr() << u8"\u201d to Int";
    }
    double _Val::to_float() const
    {
        throw MTException() << u8"Cannot convert " << type_name() << u8" \u201c" << repr() << u8"\u201d to Float";
    }
    bool _Val::to_bool() const
    {
        throw MTException() << u8"Expected Bool but found " << type_name() << u8" \u201c" << repr() << u8"\u201d";
    }
    std::string _Val::to_str() const
    {
        std::stringstream sb;
        to_str(sb);
        return sb.str();
    }
    void _Val::repr(std::ostream& out) const
    {
        to_str(out);
    }
    std::string _Val::repr() const
    {
        std::stringstream sb;
        repr(sb);
        return sb.str();
    }


    // --- class _IntV ---

    std::vector<ptr<_Val>> _IntV::CACHE(_IntV::MAX_CACHE - _IntV::MIN_CACHE + 1);

    ptr<_Val> _IntV::of(int64_t i)
    {
        if(MIN_CACHE <= i && i <= MAX_CACHE)
        {
            size_t cache_index = i - MIN_CACHE;
            ptr<_Val> p = CACHE[cache_index];
            if(p == nullptr)
            {
                p = std::make_shared<_IntV>(i);
                CACHE[cache_index] = p;
            }
            return p;
        }
        return std::make_shared<_IntV>(i);
    }
    _IntV::_IntV(int64_t v) : _SimpleVal<int64_t,_Val::IntT>(v) {}
    const char* _IntV::type_name() const
    {
        return "Int";
    }
    size_t _IntV::get_hash() const
    {
        return std::hash<int64_t>()(val);
    }
    int64_t _IntV::to_int() const
    {
        return val;
    }
    double _IntV::to_float() const
    {
        return (double)val;
    }


    // --- class _FloatV ---

    ptr<_Val> _FloatV::of(double f)
    {
        return std::make_shared<_FloatV>(f);
    }
    _FloatV::_FloatV(double v) : _SimpleVal<double,_Val::FloatT>(v) {}
    const char* _FloatV::type_name() const
    {
        return "Float";
    }
    size_t _FloatV::get_hash() const
    {
        return std::hash<double>()(val);
    }
    bool _FloatV::container_eq(const _Val& other) const
    {
        if(other.val_type() != _Val::FloatT) { return false; }

        double other_val = static_cast<const _FloatV&>(other).val;
        return (std::isnan(val) && std::isnan(other_val)) || val == other_val;
    }
    int64_t _FloatV::to_int() const
    {
        return (int64_t)val;
    }
    double _FloatV::to_float() const
    {
        return val;
    }
    void _FloatV::to_str(std::ostream& out) const
    {
        if(std::isnan(val))                                      { out << "MNaN"; }
        else if(val == std::numeric_limits<double>::infinity())  { out << "Inf"; }
        else if(val == -std::numeric_limits<double>::infinity()) { out << "Inf.Neg()"; }
        else if(fmod(val, 1.0) == 0.0)
        {
            int orig_precision = out.precision();
            out << std::fixed << std::setprecision(1) << val << std::setprecision(orig_precision) << std::defaultfloat;
        }
        else
        {
            out << val;
        }
    }


    // --- class _BoolV ---

    ptr<_Val> _BoolV::TRUE_VALUE  = std::make_shared<_BoolV>(true);
    ptr<_Val> _BoolV::FALSE_VALUE = std::make_shared<_BoolV>(false);

    ptr<_Val> _BoolV::of(bool b)
    {
        return b ? TRUE_VALUE : FALSE_VALUE;
    }
    _BoolV::_BoolV(bool v) : _SimpleVal<bool,_Val::BoolT>(v) {}
    const char* _BoolV::type_name() const
    {
        return "Bool";
    }
    size_t _BoolV::get_hash() const
    {
        return val ? 47 : 41;
    }
    bool _BoolV::to_bool() const
    {
        return val;
    }
    void _BoolV::to_str(std::ostream& out) const
    {
        out << (val ? "TRUE" : "FALSE");
    }


    // --- class _StrV ---

    ptr<_Val> _StrV::EMPTY = std::make_shared<_StrV>("");
    ptr<_Val> _StrV::of(std::string s)
    {
        return (s.size() == 0) ? EMPTY : std::make_shared<_StrV>(s);
    }

    void _StrV::escape(std::ostream& out, std::string s, const char* percent)
    {
        out.put('"');
        for(const char& ch : s)
        {
            switch(ch)
            {
                case '\\': out << "\\\\"; break;
                case '\"': out << "\\\""; break;
                case '\n': out << "\\n"; break;
                case '\r': out << "\\r"; break;
                case '\t': out << "\\t"; break;
                case '%':  out << percent; break;
                default: out.put(ch);
            }
        }
        out.put('"');
    }

    _StrV::_StrV(const char* v) : _SimpleVal<std::string,_Val::StrT>(std::string(v)) {}
    _StrV::_StrV(std::string v) : _SimpleVal<std::string,_Val::StrT>(v) {}
    const char* _StrV::type_name() const
    {
        return "Str";
    }
    size_t _StrV::get_hash() const
    {
        return std::hash<std::string>()(val);
    }
    int64_t _StrV::to_int() const
    {
        double f;
        std::stringstream ss(val);
        ss >> f;
        if(ss.fail() || !ss.eof())
        {
            return _SimpleVal<std::string,_Val::StrT>::to_int();
        }
        return (int64_t)f;

    }

    double _StrV::to_float() const
    {
        double f;
        std::stringstream ss(val);
        ss >> f;
        if(ss.fail() || !ss.eof())
        {
            return _SimpleVal<std::string,_Val::StrT>::to_float();
        }
        return f;
    }

    void _StrV::repr(std::ostream& out) const
    {
        escape(out, val, "%%");
    }

    bool _StrV::operator==(const _Val& other) const
    {
        return other.val_type() == StrT && val == static_cast<const _StrV*>(&other)->val;
    }


    // --- class _SpecialKeyV ---

    ptr<_Val> _SpecialKeyV::VAL     = std::make_shared<_SpecialKeyV>("Val");
    ptr<_Val> _SpecialKeyV::ARG     = std::make_shared<_SpecialKeyV>("Arg");
    ptr<_Val> _SpecialKeyV::THIS_FN = std::make_shared<_SpecialKeyV>("ThisFn");
    ptr<_Val> _SpecialKeyV::IDX     = std::make_shared<_SpecialKeyV>("Idx");
    ptr<_Val> _SpecialKeyV::ITEM    = std::make_shared<_SpecialKeyV>("Item");
    ptr<_Val> _SpecialKeyV::IT      = std::make_shared<_SpecialKeyV>("It");
    ptr<_Val> _SpecialKeyV::EX      = std::make_shared<_SpecialKeyV>("Ex");
    ptr<_Val> _SpecialKeyV::PARTIAL = std::make_shared<_SpecialKeyV>("Partial");

    _SpecialKeyV::_SpecialKeyV(const char* v) : _SimpleVal<const char*,_Val::SpecialKeyT>(v) {}
    const char* _SpecialKeyV::type_name() const
    {
        return "SpecialKey";
    }
    size_t _SpecialKeyV::get_hash() const
    {
        return std::hash<const char*>()(val);
    }
    void _SpecialKeyV::to_str(std::ostream& out) const
    {
        out << "<" << val << ">";
    }


    // --- ostream operators ---

    std::ostream& operator<<(std::ostream& out, const _Val& v)
    {
        v.to_str(out);
        return out;
    }
}
