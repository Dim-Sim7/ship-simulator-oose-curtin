//
// DO NOT ADD ANYTHING TO THIS FILE (or to scenario.cpp). This is just a utility to supply data to
// your Assignment 2 simulation app. Your code must work with the ORIGINAL version of this file.
//
// You must create a new Scenario object, call get_width()/get_height(), then call 'next_message()'
// periodically, which returns the next message in a queue of randomly-generated messages.
//
// Note that some of the randomly-generated messages will be invalid!
//

#ifndef COMMS_GENERATOR_H
#define COMMS_GENERATOR_H

#include <cstdint>
#include <memory>
#include <string>

struct Scenario
{
    Scenario();
    uint32_t get_width() const;
    uint32_t get_height() const;
    std::shared_ptr<std::string> next_message() const;
    void set_error_probability(double error_prob);
};

#endif
