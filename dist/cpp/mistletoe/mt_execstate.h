#ifndef MT_EXECSTATE_H
#define MT_EXECSTATE_H

#include "mt_common.h"
#include "mt_jump.h"
#include "mt_scope.h"
#include "mt_vals.h"
#include <functional>
#include <ostream>
#include <random>
#include <unordered_map>

namespace mistletoe
{
    struct _ExecState
    {
        static thread_local _ExecState instance;
        ptr<_Scope> global_scope;
        // ptr<_Scope> local_scope;
        std::mt19937_64 rand_gen;
        uint8_t current_jump;
        ptr<_Vals> exception;

        _ExecState();
        void reset();
        // void with_scope(std::function<void()> action, std::function<void()> clean_up = []{});
        // void with_scope(ptr<_Scope> alt_parent_scope, std::function<void()> action, std::function<void()> clean_up = []{});
        bool end_jump(_Jump jump);
        // _Vals* get(ptr<_Val> k) const;
        // void put(ptr<_Val> k, _Vals v);
    };
}

#endif
