// Mistletoe v0.1 -- C++ implementation

#ifndef MISTLETOE_H
#define MISTLETOE_H

#include "mt_expr.h"
#include "mt_execstate.h"
#include "mt_jump.h"
#include "mt_vals.h"

namespace mistletoe
{
    template<class... Ts>
    ptr<_Vals> MT(const Ts&... args)
    {
        auto& state = _ExecState::instance;
        state.reset();
        ptr<_Vals> ret{_to_expr(args...)->_eval_list(std::make_shared<_Scope>(state.global_scope))};
        if(state.end_jump(_Jump::Throw))
        {
            throw MTException() << "Unhandled exception: " << *state.exception;
        }
        return ret;
    }
}

#endif
