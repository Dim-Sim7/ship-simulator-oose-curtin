#ifndef MT_VAL_H
#define MT_VAL_H

#include "mt_common.h"
#include <ostream>
#include <string>
#include <vector>


namespace mistletoe
{
    struct _Val
    {
        enum Type { IntT, FloatT, BoolT, StrT, FunctionT, TupleT, SpecialKeyT };

        struct Hash
        {
            size_t operator()(const ptr<_Val> v) const;
        };

        struct Eq
        {
            bool operator()(const ptr<_Val> v1, const ptr<_Val> v2) const;
        };

        virtual ~_Val();
        virtual Type val_type() const = 0;
        virtual const char* type_name() const = 0;
        virtual bool operator==(const _Val& other) const = 0;
        virtual bool container_eq(const _Val& other) const;
        virtual size_t get_hash() const = 0;
        bool operator!=(const _Val& other) const;
        virtual int64_t to_int() const;
        virtual double to_float() const;
        virtual bool to_bool() const;

        virtual void to_str(std::ostream& out) const = 0;
        std::string to_str() const;

        virtual void repr(std::ostream& out) const;
        std::string repr() const;
    };
    std::ostream& operator<<(std::ostream& out, const _Val& v);


    template<class T, _Val::Type t>
    struct _SimpleVal : _Val
    {
        T val;
        _SimpleVal(const T& v) : val(v) {}
        _Val::Type val_type() const override
        {
            return t;
        }
        bool operator==(const _Val& other) const override
        {
            return t == other.val_type() && val == static_cast<const _SimpleVal<T,t>*>(&other)->val;
        }
        void to_str(std::ostream& out) const override
        {
            out << val;
        }
    };

    struct _IntV : public _SimpleVal<int64_t, _Val::IntT>
    {
        static const int64_t MIN_CACHE = -10;
        static const int64_t MAX_CACHE = 10;
        static std::vector<ptr<_Val>> CACHE;

        static ptr<_Val> of(int64_t i);
        _IntV(int64_t v);
        const char* type_name() const override;
        size_t get_hash() const override;
        int64_t to_int() const override;
        double to_float() const override;
    };

    struct _FloatV : public _SimpleVal<double, _Val::FloatT>
    {
        static ptr<_Val> of(double f);
        _FloatV(double v);
        const char* type_name() const override;
        size_t get_hash() const override;
        bool container_eq(const _Val& other) const override;
        int64_t to_int() const override;
        double to_float() const override;
        void to_str(std::ostream& out) const override;
    };

    struct _BoolV : public _SimpleVal<bool, _Val::BoolT>
    {
        static ptr<_Val> TRUE_VALUE;
        static ptr<_Val> FALSE_VALUE;

        static ptr<_Val> of(bool b);
        _BoolV(bool v);
        const char* type_name() const override;
        size_t get_hash() const override;
        bool to_bool() const override;
        void to_str(std::ostream& out) const override;
    };

    struct _StrV : public _SimpleVal<std::string, _Val::StrT>
    {
        static ptr<_Val> EMPTY;
        static ptr<_Val> of(std::string s);
        static void escape(std::ostream& out, std::string s, const char* percent);

        _StrV(const char* v);
        _StrV(std::string v);
        const char* type_name() const override;
        size_t get_hash() const override;
        int64_t to_int() const override;
        double to_float() const override;
        void repr(std::ostream& out) const override;
        bool operator==(const _Val& other) const override;
    };

    struct _SpecialKeyV : public _SimpleVal<const char*, _Val::SpecialKeyT>
    {
        static ptr<_Val> VAL;
        static ptr<_Val> ARG;
        static ptr<_Val> THIS_FN;
        static ptr<_Val> IDX;
        static ptr<_Val> ITEM;
        static ptr<_Val> IT;
        static ptr<_Val> EX;
        static ptr<_Val> PARTIAL;

        _SpecialKeyV(const char* v);
        const char* type_name() const override;
        size_t get_hash() const override;
        void to_str(std::ostream& out) const override;
    };
}

#endif
