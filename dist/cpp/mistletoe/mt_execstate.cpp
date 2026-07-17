#include "mt_execstate.h"

namespace mistletoe
{
    thread_local _ExecState _ExecState::instance;

    _ExecState::_ExecState() : global_scope{std::make_shared<_Scope>(nullptr, 0)},
                               rand_gen(std::random_device()()),
                               current_jump(0),
                               exception(nullptr) {}

    void _ExecState::reset()
    {
        current_jump = 0;
        exception = nullptr;
    }

    bool _ExecState::end_jump(_Jump jump)
    {
        if(current_jump == jump.bit)
        {
            current_jump = 0;
            return true;
        }
        return false;
    }
}
